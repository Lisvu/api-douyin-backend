package com.douyin.api;

import com.douyin.api.service.MediaStorageService;

import java.nio.file.Path;

final class TestMediaStorage {
    private TestMediaStorage() {
    }

    static MediaStorageService create() {
        return new MediaStorageService(
                Path.of(System.getProperty("java.io.tmpdir"), "api-douyin-test-uploads").toString(),
                false,
                "",
                "",
                22,
                "",
                "",
                ""
        );
    }
}
