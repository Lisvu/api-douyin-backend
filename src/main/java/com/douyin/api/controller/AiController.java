package com.douyin.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @PostMapping(value = "/video-copy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
                response.put("success", false);
                response.put("message", "AI 生成失败，状态码：" + aiResponse.statusCode());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
            }

            String outputText = extractOutputText(aiResponse.body());
            Map<String, Object> copy = parseCopy(outputText);
            response.put("success", true);
            response.put("copy", copy);
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
        if (responseBody != null && responseBody.trim().startsWith("event:")) {
            String sseText = extractOutputTextFromSse(responseBody);
            if (!sseText.isBlank()) {
                return sseText;
            }
        }

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode outputText = root.path("output_text");
        if (outputText.isTextual()) {
            return outputText.asText();
        }

        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        JsonNode text = part.path("text");
                        if (text.isTextual()) {
                            return text.asText();
                        }
                    }
                }
            }
        }

        JsonNode choicesText = root.path("choices").path(0).path("message").path("content");
        if (choicesText.isTextual()) {
            return choicesText.asText();
        }
        return responseBody;
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
            JsonNode delta = node.path("delta");
            if (delta.isTextual()) {
                deltas.append(delta.asText());
            }

            JsonNode text = node.path("text");
            if (text.isTextual()) {
                completedTexts.add(text.asText());
            }

            if ("response.completed".equals(type)) {
                String output = extractOutputText(data);
                if (!output.isBlank() && !output.equals(data)) {
                    completedTexts.add(output);
                }
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
}
