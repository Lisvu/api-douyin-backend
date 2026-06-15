package com.douyin.api.service;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.imageio.ImageIO;

@Service
public class MediaStorageService {
    private static final String UPLOAD_URL_PREFIX = "/uploads/";

    private final Path localUploadDir;
    private final boolean remoteUploadEnabled;
    private final String publicBaseUrl;
    private final String remoteHost;
    private final int remotePort;
    private final String remoteUsername;
    private final String remotePassword;
    private final String remoteUploadDir;

    public MediaStorageService(
            @Value("${app.media.local-upload-dir:public/uploads}") String localUploadDir,
            @Value("${app.media.remote-upload.enabled:false}") boolean remoteUploadEnabled,
            @Value("${app.media.public-base-url:}") String publicBaseUrl,
            @Value("${app.media.remote-upload.host:${app.ssh-tunnel.ssh-host:}}") String remoteHost,
            @Value("${app.media.remote-upload.port:${app.ssh-tunnel.ssh-port:22}}") int remotePort,
            @Value("${app.media.remote-upload.username:${app.ssh-tunnel.ssh-username:}}") String remoteUsername,
            @Value("${app.media.remote-upload.password:${app.ssh-tunnel.ssh-password:}}") String remotePassword,
            @Value("${app.media.remote-upload.base-dir:}") String remoteUploadDir) {
        Path configuredPath = Path.of(localUploadDir);
        this.localUploadDir = configuredPath.isAbsolute()
                ? configuredPath
                : Path.of(System.getProperty("user.dir")).resolve(configuredPath);
        this.remoteUploadEnabled = remoteUploadEnabled;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.remoteUsername = remoteUsername;
        this.remotePassword = remotePassword;
        this.remoteUploadDir = trimTrailingSlash(remoteUploadDir);
    }

    public String storeUpload(MultipartFile file, String folder, String filename) throws IOException {
        return store(file.getInputStream(), folder, filename);
    }

    public String storeGeneratedCover(String title, String folder, String filename) throws IOException {
        return store(new ByteArrayInputStream(createCoverImage(title)), folder, filename);
    }

    public void deleteMedia(String url) {
        String relativePath = extractUploadRelativePath(url);
        if (relativePath == null) {
            return;
        }

        try {
            Files.deleteIfExists(localUploadDir.resolve(relativePath));
        } catch (IOException ignored) {
            // Deleting media is best-effort; the database record has already gone away.
        }

        if (remoteUploadEnabled) {
            try {
                withSftp(channel -> channel.rm(remotePath(relativePath)));
            } catch (Exception ignored) {
                // Keep delete idempotent even if an old remote file is already missing.
            }
        }
    }

    private String store(InputStream inputStream, String folder, String filename) throws IOException {
        String safeFolder = sanitizeFolder(folder);
        String safeFilename = sanitizeFilename(filename);
        String relativePath = safeFolder + "/" + safeFilename;
        Path target = localUploadDir.resolve(relativePath);

        Files.createDirectories(target.getParent());
        try (InputStream in = inputStream) {
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        if (remoteUploadEnabled) {
            ensureRemoteConfig();
            try {
                uploadRemote(target.toFile(), relativePath);
            } catch (JSchException | SftpException e) {
                throw new IOException("Remote media upload failed: " + e.getMessage(), e);
            }
            return publicBaseUrl + UPLOAD_URL_PREFIX + relativePath;
        }

        return UPLOAD_URL_PREFIX + relativePath;
    }

    private void uploadRemote(File localFile, String relativePath) throws JSchException, SftpException {
        withSftp(channel -> {
            mkdirs(channel, remotePath(Path.of(relativePath).getParent().toString().replace('\\', '/')));
            channel.put(localFile.getAbsolutePath(), remotePath(relativePath));
        });
    }

    private void withSftp(SftpAction action) throws JSchException, SftpException {
        Session session = null;
        ChannelSftp channel = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(remoteUsername, remoteHost, remotePort);
            session.setPassword(remotePassword);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(15_000);
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(15_000);
            action.run(channel);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private void mkdirs(ChannelSftp channel, String absoluteDir) throws SftpException {
        String normalized = absoluteDir.replace('\\', '/');
        String[] parts = normalized.split("/");
        String current = normalized.startsWith("/") ? "/" : "";
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            current = current.isEmpty() || "/".equals(current) ? current + part : current + "/" + part;
            try {
                channel.cd(current);
            } catch (SftpException e) {
                channel.mkdir(current);
            }
        }
    }

    private String remotePath(String relativePath) {
        return remoteUploadDir + "/" + relativePath.replace('\\', '/');
    }

    private void ensureRemoteConfig() throws IOException {
        if (remoteHost.isBlank() || remoteUsername.isBlank() || remotePassword.isBlank()
                || remoteUploadDir.isBlank() || publicBaseUrl.isBlank()) {
            throw new IOException("Remote media upload is enabled but host, username, password, base directory, or public base URL is missing.");
        }
    }

    private String extractUploadRelativePath(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (url.startsWith(UPLOAD_URL_PREFIX)) {
            return url.substring(UPLOAD_URL_PREFIX.length());
        }
        String uploadsMarker = UPLOAD_URL_PREFIX;
        int markerIndex = url.indexOf(uploadsMarker);
        if (url.startsWith(publicBaseUrl) && markerIndex >= 0) {
            return url.substring(markerIndex + uploadsMarker.length());
        }
        return null;
    }

    private byte[] createCoverImage(String title) throws IOException {
        int width = 600;
        int height = 800;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setPaint(new GradientPaint(0, 0, new Color(35, 120, 146), width, height, new Color(190, 72, 100)));
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(255, 255, 255, 230));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 52));
            drawCenteredText(graphics, title == null || title.isBlank() ? "Video" : title.trim(), width, height);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }

    private void drawCenteredText(Graphics2D graphics, String text, int width, int height) {
        String displayText = text.length() > 18 ? text.substring(0, 18) : text;
        FontMetrics metrics = graphics.getFontMetrics();
        int x = Math.max(24, (width - metrics.stringWidth(displayText)) / 2);
        int y = (height - metrics.getHeight()) / 2 + metrics.getAscent();
        graphics.drawString(displayText, x, y);
    }

    private String sanitizeFolder(String folder) {
        String normalized = folder == null ? "" : folder.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
        if (!normalized.matches("[A-Za-z0-9_/-]+")) {
            throw new IllegalArgumentException("Invalid media folder: " + folder);
        }
        return normalized;
    }

    private String sanitizeFilename(String filename) {
        String safe = filename == null ? "" : filename.replace('\\', '/');
        int slashIndex = safe.lastIndexOf('/');
        if (slashIndex >= 0) {
            safe = safe.substring(slashIndex + 1);
        }
        safe = safe.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
            throw new IllegalArgumentException("Invalid media filename.");
        }
        return safe;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }

    public static String getFileExtension(String originalFilename, String fallback) {
        if (originalFilename == null) {
            return fallback;
        }
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return fallback;
        }
        return originalFilename.substring(lastDotIndex).toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface SftpAction {
        void run(ChannelSftp channel) throws SftpException;
    }
}
