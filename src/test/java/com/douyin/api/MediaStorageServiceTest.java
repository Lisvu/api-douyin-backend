package com.douyin.api;

import com.douyin.api.service.MediaStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MediaStorageServiceTest {

    private MediaStorageService service;
    private Path tempUploadDir;

    @BeforeEach
    void setUp() throws IOException {
        tempUploadDir = Files.createTempDirectory("api-douyin-f10-test-");
        service = new MediaStorageService(
                tempUploadDir.toString(),
                false,   // remoteUploadEnabled
                "",      // publicBaseUrl
                "",      // remoteHost
                22,      // remotePort
                "",      // remoteUsername
                "",      // remotePassword
                ""       // remoteUploadDir
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(tempUploadDir)) {
            try (var files = Files.walk(tempUploadDir)) {
                files.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    // ── F10: 文件上传存储 ──────────────────────────────────────────

    @Test
    void storeUploadSavesFileAndReturnsLocalUrl() throws IOException {
        byte[] content = "fake-video-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "video", "my-video.mp4", "video/mp4", content);

        String url = service.storeUpload(file, "videos", "video-test-123.mp4");

        assertThat(url).startsWith("/uploads/videos/video-test-123.mp4");

        // Verify file exists on disk
        Path expectedPath = tempUploadDir.resolve("videos/video-test-123.mp4");
        assertThat(expectedPath.toFile()).exists();
        assertThat(expectedPath.toFile()).isFile();
        assertThat(expectedPath.toFile().length()).isEqualTo(content.length);
    }

    @Test
    void storeUploadCreatesParentDirectories() throws IOException {
        byte[] content = "nested-file".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "video", "clip.webm", "video/webm", content);

        String url = service.storeUpload(file, "videos/deep/nested", "nested-clip.webm");

        assertThat(url).startsWith("/uploads/videos/deep/nested/nested-clip.webm");
        assertThat(tempUploadDir.resolve("videos/deep/nested/nested-clip.webm").toFile()).exists();
    }

    @Test
    void storeUploadWithSpecialFilenameSanitizes() throws IOException {
        byte[] content = "data".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "video", "../../../etc/passwd.mp4", "video/mp4", content);

        // Should not escape the upload directory
        String url = service.storeUpload(file, "videos", "../evil-../../../file.mp4");

        assertThat(url).doesNotContain("..");
        assertThat(url).startsWith("/uploads/");
    }

    // ── F10: 封面自动生成 ──────────────────────────────────────────

    @Test
    void storeGeneratedCoverCreatesValidJpeg() throws IOException {
        String url = service.storeGeneratedCover("测试视频标题", "covers", "cover-auto-1.jpg");

        assertThat(url).startsWith("/uploads/covers/cover-auto-1.jpg");

        Path coverPath = tempUploadDir.resolve("covers/cover-auto-1.jpg");
        assertThat(coverPath.toFile()).exists();
        assertThat(coverPath.toFile().length()).isGreaterThan(512);

        // Verify it's a valid JPEG image
        byte[] imageBytes = Files.readAllBytes(coverPath);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(bis);
            assertThat(image).isNotNull();
            assertThat(image.getWidth()).isEqualTo(600);
            assertThat(image.getHeight()).isEqualTo(800);
        }
    }

    @Test
    void storeGeneratedCoverHandlesEmptyTitle() throws IOException {
        String url = service.storeGeneratedCover("", "covers", "cover-empty.jpg");

        assertThat(url).startsWith("/uploads/covers/cover-empty.jpg");
        assertThat(tempUploadDir.resolve("covers/cover-empty.jpg").toFile()).exists();
    }

    @Test
    void storeGeneratedCoverHandlesNullTitle() throws IOException {
        String url = service.storeGeneratedCover(null, "covers", "cover-null.jpg");

        assertThat(url).startsWith("/uploads/covers/cover-null.jpg");
        assertThat(tempUploadDir.resolve("covers/cover-null.jpg").toFile()).exists();
    }

    // ── F10: 文件扩展名提取 ────────────────────────────────────────

    @Test
    void getFileExtensionExtractsCorrectSuffix() {
        assertThat(MediaStorageService.getFileExtension("video.mp4", ".mp4")).isEqualTo(".mp4");
        assertThat(MediaStorageService.getFileExtension("video.MP4", ".mp4")).isEqualTo(".mp4");
        assertThat(MediaStorageService.getFileExtension("path/to/cover.JPG", ".jpg")).isEqualTo(".jpg");
        assertThat(MediaStorageService.getFileExtension("file.with.dots.png", ".png")).isEqualTo(".png");
    }

    @Test
    void getFileExtensionReturnsFallbackWhenMissing() {
        assertThat(MediaStorageService.getFileExtension("noext", ".mp4")).isEqualTo(".mp4");
        assertThat(MediaStorageService.getFileExtension(null, ".jpg")).isEqualTo(".jpg");
        assertThat(MediaStorageService.getFileExtension("", ".mp4")).isEqualTo(".mp4");
    }

    // ── F10: 文件删除 ──────────────────────────────────────────────

    @Test
    void deleteMediaRemovesLocalFile() throws IOException {
        // First store a file
        byte[] content = "to-delete".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "video", "temp.mp4", "video/mp4", content);
        String url = service.storeUpload(file, "videos", "temp-delete.mp4");

        Path filePath = tempUploadDir.resolve("videos/temp-delete.mp4");
        assertThat(filePath.toFile()).exists();

        // Delete by URL
        service.deleteMedia(url);

        assertThat(filePath.toFile()).doesNotExist();
    }

    @Test
    void deleteMediaWithNullUrlDoesNotThrow() {
        assertDoesNotThrow(() -> service.deleteMedia(null));
    }

    @Test
    void deleteMediaWithBlankUrlDoesNotThrow() {
        assertDoesNotThrow(() -> service.deleteMedia(""));
        assertDoesNotThrow(() -> service.deleteMedia("   "));
    }

    @Test
    void deleteMediaWithNonUploadUrlDoesNotThrow() {
        // URL not starting with /uploads/ should be a no-op
        assertDoesNotThrow(() -> service.deleteMedia("https://external-cdn.com/video.mp4"));
    }
}
