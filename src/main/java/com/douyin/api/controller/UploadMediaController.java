package com.douyin.api.controller;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class UploadMediaController {

    private final Path uploadRoot;

    public UploadMediaController() {
        this.uploadRoot = Paths.get(System.getProperty("user.dir"), "public", "uploads")
                .toAbsolutePath()
                .normalize();
        File dir = uploadRoot.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @GetMapping("/uploads/{*relativePath}")
    public ResponseEntity<Resource> serveUpload(@PathVariable String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank() || relativePath.contains("..")) {
            return ResponseEntity.notFound().build();
        }

        String normalizedRelative = relativePath.startsWith("/")
                ? relativePath.substring(1)
                : relativePath;
        Path target = uploadRoot.resolve(normalizedRelative).normalize();
        if (!target.startsWith(uploadRoot)) {
            return ResponseEntity.notFound().build();
        }

        File file = target.toFile();
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(target);
        if (contentType == null) {
            contentType = guessContentType(normalizedRelative);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(file.length())
                .body(new FileSystemResource(file));
    }

    private String guessContentType(String relative) {
        String lower = relative.toLowerCase();
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
