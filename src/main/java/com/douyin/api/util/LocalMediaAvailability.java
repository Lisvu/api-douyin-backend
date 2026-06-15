package com.douyin.api.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Checks whether a stored media URL can be served from this backend instance.
 * Remote CDN/http(s) URLs are always treated as playable; local /uploads/ paths
 * must exist on disk with a non-trivial file size.
 */
public final class LocalMediaAvailability {

    private static final long MIN_PLAYABLE_BYTES = 1024;
    private static final Path UPLOAD_ROOT = Paths.get(System.getProperty("user.dir"), "public", "uploads")
            .toAbsolutePath()
            .normalize();

    private LocalMediaAvailability() {
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

        Path target = UPLOAD_ROOT.resolve(relative).normalize();
        if (!target.startsWith(UPLOAD_ROOT)) {
            return false;
        }

        File file = target.toFile();
        return file.isFile() && file.length() >= MIN_PLAYABLE_BYTES;
    }
}
