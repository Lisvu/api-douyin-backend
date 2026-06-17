package com.douyin.api.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Checks whether a stored media URL can be served from this backend instance.
 * Remote CDN/http(s) URLs are always treated as playable; local /uploads/ paths
 * must exist on disk with a non-trivial file size.
 *
 * The upload root is initialized by {@link com.douyin.api.service.MediaStorageService}
 * during construction so that all path resolution stays consistent with the
 * {@code app.media.local-upload-dir} configuration property.
 */
public final class LocalMediaAvailability {

    private static final long MIN_PLAYABLE_BYTES = 1024;

    private static volatile Path uploadRoot = Paths.get(System.getProperty("user.dir"), "public", "uploads")
            .toAbsolutePath()
            .normalize();

    private LocalMediaAvailability() {
    }

    /**
     * Called once at startup by MediaStorageService to align the upload root
     * with the configured {@code app.media.local-upload-dir}.
     */
    public static void setUploadRoot(Path root) {
        if (root != null) {
            uploadRoot = root.toAbsolutePath().normalize();
        }
    }

    public static boolean isPlayableUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return true;
        }
        if (!url.startsWith("/uploads/")) {
            return true;
        }

        String relative = url.substring("/uploads/".length());
        if (relative.contains("..")) {
            return false;
        }

        Path target = uploadRoot.resolve(relative).normalize();
        if (!target.startsWith(uploadRoot)) {
            return false;
        }

        File file = target.toFile();
        return file.isFile() && file.length() >= MIN_PLAYABLE_BYTES;
    }
}
