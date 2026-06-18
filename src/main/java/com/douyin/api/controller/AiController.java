package com.douyin.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String token;
    private final String model;
    private final int timeoutSeconds;
    private final boolean verifySsl;

    public AiController(
            ObjectMapper objectMapper,
            @Value("${codex.api.endpoint:}") String endpoint,
            @Value("${codex.api.token:}") String token,
            @Value("${codex.api.model:gpt-5.4}") String model,
            @Value("${codex.api.timeout-seconds:180}") int timeoutSeconds,
            @Value("${codex.api.verify-ssl:true}") boolean verifySsl) {
        this.objectMapper = objectMapper;
        this.endpoint = endpoint == null ? "" : endpoint.trim();
        this.token = token == null ? "" : token.trim();
        this.model = model == null ? "gpt-5.4" : model.trim();
        this.timeoutSeconds = timeoutSeconds;
        this.verifySsl = verifySsl;
    }

    @PostMapping(value = "/video-copies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "生成视频文案")
    public ResponseEntity<Map<String, Object>> generateVideoCopy(
            @RequestParam(value = "frame", required = false) MultipartFile frame,
            @RequestParam(value = "filename", required = false) String filename,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (endpoint.isBlank() || token.isBlank()) {
            response.put("success", false);
            response.put("message", "AI 接口未配置 CODEX_API_ENDPOINT 或 CODEX_API_TOKEN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        try {
            String prompt = """
                    你是短视频平台的中文文案助手。请根据视频首帧和文件名，为用户生成适合发布的标题和简介。
                    要求：
                    1. 标题 8-24 个中文字符，吸引人但不要夸张。
                    2. 简介 1-2 句话，适合短视频平台。
                    3. 给出 3-6 个中文话题标签。
                    4. 只返回 JSON，不要 Markdown。
                    JSON 字段：title, description, hashtags。
                    文件名：%s
                    """.formatted(filename == null || filename.isBlank() ? "未命名视频" : filename);

            Map<String, Object> textPart = Map.of("type", "input_text", "text", prompt);
            List<Map<String, Object>> content;
            if (frame != null && !frame.isEmpty()) {
                if (frame.getSize() > 5 * 1024 * 1024) {
                    response.put("success", false);
                    response.put("message", "视频首帧图片不能超过 5MB");
                    return ResponseEntity.badRequest().body(response);
                }
                String contentType = frame.getContentType() == null ? "image/jpeg" : frame.getContentType();
                String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(frame.getBytes());
                content = List.of(textPart, Map.of("type", "input_image", "image_url", dataUrl));
            } else {
                content = List.of(textPart);
            }

            Map<String, Object> payload = Map.of(
                    "model", model,
                    "input", List.of(Map.of("role", "user", "content", content))
            );

            HttpRequest aiRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> aiResponse = buildHttpClient().send(aiRequest, HttpResponse.BodyHandlers.ofString());
            if (aiResponse.statusCode() < 200 || aiResponse.statusCode() >= 300) {
                response.put("success", true);
                response.put("message", "AI 服务暂时不稳定，已生成基础文案");
                response.put("copy", fallbackCopy(filename));
                return ResponseEntity.ok(response);
            }

            String outputText = extractOutputText(aiResponse.body());
            Map<String, Object> copy = parseCopy(outputText);
            response.put("success", true);
            response.put("copy", copy);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", true);
            response.put("message", "AI 服务暂时不稳定，已生成基础文案");
            response.put("copy", fallbackCopy(filename));
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping(value = "/video-tags", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "生成视频分类")
    public ResponseEntity<Map<String, Object>> generateVideoTags(
            @RequestParam(value = "frame", required = false) MultipartFile frame,
            @RequestParam(value = "filename", required = false) String filename,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (endpoint.isBlank() || token.isBlank()) {
            response.put("success", false);
            response.put("message", "AI 接口未配置 CODEX_API_ENDPOINT 或 CODEX_API_TOKEN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        try {
            String prompt = """
                    你是短视频平台的内容分类助手。请根据视频首帧、标题、简介和文件名，判断这个视频最适合的分类。
                    分类只能从以下列表中选择一个：游戏、二次元、音乐、影视、美食、知识、小剧场、生活vlog、体育、旅行、科技、自然、创意。
                    只返回 JSON，不要 Markdown。
                    JSON 字段：category, confidence, reason, tags。
                    文件名：%s
                    标题：%s
                    简介：%s
                    """.formatted(
                    filename == null || filename.isBlank() ? "未命名视频" : filename,
                    title == null ? "" : title.trim(),
                    description == null ? "" : description.trim()
            );

            Map<String, Object> textPart = Map.of("type", "input_text", "text", prompt);
            List<Map<String, Object>> content;
            if (frame != null && !frame.isEmpty()) {
                if (frame.getSize() > 5 * 1024 * 1024) {
                    response.put("success", false);
                    response.put("message", "视频首帧图片不能超过 5MB");
                    return ResponseEntity.badRequest().body(response);
                }
                String contentType = frame.getContentType() == null ? "image/jpeg" : frame.getContentType();
                String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(frame.getBytes());
                content = List.of(textPart, Map.of("type", "input_image", "image_url", dataUrl));
            } else {
                content = List.of(textPart);
            }

            Map<String, Object> payload = Map.of(
                    "model", model,
                    "input", List.of(Map.of("role", "user", "content", content))
            );

            HttpRequest aiRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> aiResponse = buildHttpClient().send(aiRequest, HttpResponse.BodyHandlers.ofString());
            if (aiResponse.statusCode() < 200 || aiResponse.statusCode() >= 300) {
                response.put("success", false);
                response.put("message", "AI 生成失败，状态码：" + aiResponse.statusCode());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
            }

            String outputText = extractOutputText(aiResponse.body());
            Map<String, Object> tags = parseTags(outputText);
            response.put("success", true);
            response.put("tags", tags);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "AI 生成失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping(value = "/comment-reply", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "润色评论内容")
    public ResponseEntity<Map<String, Object>> generateCommentReply(
            @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (endpoint.isBlank() || token.isBlank()) {
            response.put("success", false);
            response.put("message", "AI 接口未配置 CODEX_API_ENDPOINT 或 CODEX_API_TOKEN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }

        try {
            String videoTitle = String.valueOf(body.getOrDefault("videoTitle", "")).trim();
            String comment = String.valueOf(body.getOrDefault("comment", "")).trim();
            String style = String.valueOf(body.getOrDefault("style", "自然")).trim();
            String prompt = """
                    你是短视频评论润色助手。请润色用户已经输入的评论，不要生成新的回复对象，不要改变原本想表达的意思。
                    润色风格：%s
                    要求：
                    1. 保留用户原意，只让表达更自然、更口语化。
                    2. 不要把陈述改成提问，不要凭空增加新信息。
                    3. 30 个中文字符以内，适合短视频评论区。
                    4. 不要 Markdown。
                    只返回 JSON，不要 Markdown。
                    JSON 字段：polished, reason。
                    视频标题：%s
                    用户原评论：%s
                    """.formatted(style, videoTitle, comment);

            Map<String, Object> payload = Map.of(
                    "model", model,
                    "input", List.of(Map.of("role", "user", "content", List.of(Map.of("type", "input_text", "text", prompt))))
            );

            HttpRequest aiRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> aiResponse = buildHttpClient().send(aiRequest, HttpResponse.BodyHandlers.ofString());
            if (aiResponse.statusCode() < 200 || aiResponse.statusCode() >= 300) {
                response.put("success", false);
                response.put("message", "AI 生成失败，状态码：" + aiResponse.statusCode());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
            }

            String outputText = extractOutputText(aiResponse.body());
            Map<String, Object> reply = parsePolishedComment(outputText, comment);
            response.put("success", true);
            response.put("reply", reply);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "AI 生成失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping(value = "/cover-suggestions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "生成视频封面建议")
    public ResponseEntity<Map<String, Object>> generateCoverSuggestion(
            @RequestParam(value = "frames", required = false) List<MultipartFile> frames,
            @RequestParam(value = "ratios", required = false) List<String> ratios,
            @RequestParam(value = "filename", required = false) String filename,
            @RequestParam(value = "title", required = false) String title,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (endpoint.isBlank() || token.isBlank()) {
            response.put("success", false);
            response.put("message", "AI 接口未配置 CODEX_API_ENDPOINT 或 CODEX_API_TOKEN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
        if (frames == null || frames.isEmpty()) {
            response.put("success", false);
            response.put("message", "请至少提供一张候选封面");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String prompt = """
                    你是短视频封面选择助手。请从候选画面中选出最适合做封面的一个。
                    判断标准：主体清晰、信息量高、画面稳定、能吸引点击。
                    候选画面编号从 0 开始。只返回 JSON，不要 Markdown。
                    JSON 字段：selectedIndex, reason, overlayText。
                    文件名：%s
                    标题：%s
                    候选时间比例：%s
                    """.formatted(
                    filename == null || filename.isBlank() ? "未命名视频" : filename,
                    title == null ? "" : title.trim(),
                    ratios == null ? "" : String.join(", ", ratios)
            );

            List<Map<String, Object>> content = new ArrayList<>();
            content.add(Map.of("type", "input_text", "text", prompt));
            int maxFrames = Math.min(frames.size(), 3);
            for (int i = 0; i < maxFrames; i++) {
                MultipartFile frame = frames.get(i);
                if (frame == null || frame.isEmpty()) {
                    continue;
                }
                if (frame.getSize() > 5 * 1024 * 1024) {
                    response.put("success", false);
                    response.put("message", "候选封面图片不能超过 5MB");
                    return ResponseEntity.badRequest().body(response);
                }
                String contentType = frame.getContentType() == null ? "image/jpeg" : frame.getContentType();
                String dataUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(frame.getBytes());
                content.add(Map.of("type", "input_text", "text", "候选画面编号：" + i));
                content.add(Map.of("type", "input_image", "image_url", dataUrl));
            }

            Map<String, Object> payload = Map.of(
                    "model", model,
                    "input", List.of(Map.of("role", "user", "content", content))
            );

            HttpRequest aiRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> aiResponse = buildHttpClient().send(aiRequest, HttpResponse.BodyHandlers.ofString());
            if (aiResponse.statusCode() < 200 || aiResponse.statusCode() >= 300) {
                response.put("success", false);
                response.put("message", "AI 生成失败，状态码：" + aiResponse.statusCode());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
            }

            String outputText = extractOutputText(aiResponse.body());
            Map<String, Object> suggestion = parseCoverSuggestion(outputText, frames.size());
            response.put("success", true);
            response.put("suggestion", suggestion);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "AI 生成失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }


    private HttpClient buildHttpClient() throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, Math.min(timeoutSeconds, 60))));
        if (!verifySsl) {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            builder.sslContext(sslContext);
        }
        return builder.build();
    }

    private String extractOutputText(String responseBody) throws Exception {
        if (responseBody != null && looksLikeSse(responseBody)) {
            String sseText = extractOutputTextFromSse(responseBody);
            if (!sseText.isBlank()) {
                return sseText;
            }
            return "";
        }

        return extractOutputText(objectMapper.readTree(responseBody), responseBody);
    }

    private String extractOutputText(JsonNode root, String fallback) throws Exception {
        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual()) {
            return outputText.asText();
        }

        JsonNode delta = root.path("delta");
        if (delta.isTextual()) {
            return delta.asText();
        }

        JsonNode text = root.path("text");
        if (text.isTextual()) {
            return text.asText();
        }

        JsonNode contentText = root.path("content");
        if (contentText.isTextual()) {
            return contentText.asText();
        }

        JsonNode response = root.path("response");
        if (response.isObject()) {
            String responseText = extractOutputText(response, "");
            if (!responseText.isBlank()) {
                return responseText;
            }
        }

        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        JsonNode partText = part.path("text");
                        if (partText.isTextual()) {
                            return partText.asText();
                        }
                    }
                }
            }
        }

        JsonNode choicesText = root.path("choices").path(0).path("message").path("content");
        if (choicesText.isTextual()) {
            return choicesText.asText();
        }
        return fallback;
    }

    private boolean looksLikeSse(String responseBody) {
        for (String line : responseBody.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("event:") || trimmed.startsWith("data:")) {
                return true;
            }
        }
        return false;
    }

    private String extractOutputTextFromSse(String responseBody) throws Exception {
        StringBuilder deltas = new StringBuilder();
        List<String> completedTexts = new ArrayList<>();
        for (String line : responseBody.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) {
                continue;
            }
            String data = trimmed.substring("data:".length()).trim();
            if (data.isBlank() || "[DONE]".equals(data)) {
                continue;
            }

            JsonNode node = objectMapper.readTree(data);
            String type = node.path("type").asText("");
            String eventText = extractOutputText(node, "");
            if (eventText.isBlank()) {
                continue;
            }

            if (type.endsWith(".delta")) {
                deltas.append(eventText);
            } else if ("response.completed".equals(type) || type.endsWith(".done") || type.endsWith(".completed")) {
                completedTexts.add(eventText);
            } else if (deltas.isEmpty()) {
                completedTexts.add(eventText);
            }
        }

        if (!completedTexts.isEmpty()) {
            return completedTexts.get(completedTexts.size() - 1);
        }
        return deltas.toString();
    }

    private Map<String, Object> parseCopy(String rawText) throws Exception {
        String clean = rawText == null ? "" : rawText.trim()
                .replaceAll("^```(?:json)?\\s*", "")
                .replaceAll("\\s*```$", "");
        if (clean.isBlank()) {
            return fallbackCopy(null);
        }
        int start = clean.indexOf('{');
        int end = clean.lastIndexOf('}');
        if (start >= 0 && end > start) {
            clean = clean.substring(start, end + 1);
        }
        JsonNode node = objectMapper.readTree(clean);
        String title = node.path("title").asText("我的精彩短视频").trim();
        String description = node.path("description").asText("").trim();
        Object hashtags = objectMapper.convertValue(node.path("hashtags"), Object.class);
        return Map.of(
                "title", title,
                "description", description,
                "hashtags", hashtags
        );
    }

    private Map<String, Object> fallbackCopy(String filename) {
        String rawName = filename == null || filename.isBlank() ? "我的短视频" : filename;
        String baseName = rawName.replaceAll("\\.[^.]+$", "").replaceAll("[_\\-]+", " ").trim();
        if (baseName.isBlank()) {
            baseName = "我的短视频";
        }
        String title = baseName.length() > 24 ? baseName.substring(0, 24) : baseName;
        return Map.of(
                "title", title,
                "description", "记录这一刻，分享给更多人看看。",
                "hashtags", List.of("短视频", "生活记录", "今日分享")
        );
    }

    private Map<String, Object> parseTags(String rawText) throws Exception {
        String clean = rawText == null ? "" : rawText.trim()
                .replaceAll("^```(?:json)?\\s*", "")
                .replaceAll("\\s*```$", "");
        int start = clean.indexOf('{');
        int end = clean.lastIndexOf('}');
        if (start >= 0 && end > start) {
            clean = clean.substring(start, end + 1);
        }
        JsonNode node = objectMapper.readTree(clean);
        String category = node.path("category").asText("生活vlog").trim();
        double confidence = node.path("confidence").asDouble(0.5);
        String reason = node.path("reason").asText("").trim();
        Object tags = objectMapper.convertValue(node.path("tags"), Object.class);
        return Map.of(
                "category", category,
                "confidence", confidence,
                "reason", reason,
                "tags", tags
        );
    }

    private Map<String, Object> parsePolishedComment(String rawText, String fallbackComment) throws Exception {
        String clean = rawText == null ? "" : rawText.trim()
                .replaceAll("^```(?:json)?\\s*", "")
                .replaceAll("\\s*```$", "");
        int start = clean.indexOf('{');
        int end = clean.lastIndexOf('}');
        if (start >= 0 && end > start) {
            clean = clean.substring(start, end + 1);
        }
        JsonNode node = objectMapper.readTree(clean);
        String polished = node.path("polished").asText("").trim();
        if (polished.isBlank()) {
            polished = node.path("reply").asText(fallbackComment).trim();
        }
        String reason = node.path("reason").asText("").trim();
        return Map.of(
                "polished", polished,
                "reply", polished,
                "reason", reason
        );
    }

    private Map<String, Object> parseCoverSuggestion(String rawText, int frameCount) throws Exception {
        String clean = rawText == null ? "" : rawText.trim()
                .replaceAll("^```(?:json)?\\s*", "")
                .replaceAll("\\s*```$", "");
        int start = clean.indexOf('{');
        int end = clean.lastIndexOf('}');
        if (start >= 0 && end > start) {
            clean = clean.substring(start, end + 1);
        }
        JsonNode node = objectMapper.readTree(clean);
        int selectedIndex = node.path("selectedIndex").asInt(0);
        if (selectedIndex < 0 || selectedIndex >= frameCount) {
            selectedIndex = 0;
        }
        String reason = node.path("reason").asText("").trim();
        String overlayText = node.path("overlayText").asText("").trim();
        return Map.of(
                "selectedIndex", selectedIndex,
                "reason", reason,
                "overlayText", overlayText
        );
    }
}
