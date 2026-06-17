package com.douyin.api;

import com.douyin.api.controller.VideoController;
import com.douyin.api.exception.GlobalExceptionHandler;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.repository.CommentRepository;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.ShareRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import com.douyin.api.service.MediaStorageService;
import com.douyin.api.service.RedisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublishVideoApiTest {

    private MockMvc mockMvc;

    @Mock private UserRepository userRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private LikeRepository likeRepository;
    @Mock private ViewRepository viewRepository;
    @Mock private ShareRepository shareRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private com.douyin.api.repository.FavoriteRepository favoriteRepository;
    @Mock private MediaStorageService mediaStorageService;

    private static final Long USER_ID = 1L;
    private static final String VIDEO_URL = "/uploads/videos/video-1-1.mp4";
    private static final String COVER_URL = "/uploads/covers/cover-1-1.jpg";

    @BeforeEach
    void setUp() {
        VideoController videoController = new VideoController(
                userRepository,
                videoRepository,
                likeRepository,
                viewRepository,
                shareRepository,
                commentRepository,
                favoriteRepository,
                null,  // WatchLaterRepository
                null,  // DanmakuRepository
                null,  // UserRelationRepository
                new NoOpRedisCacheService(),
                mediaStorageService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(videoController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── F05: 正常发布 ──────────────────────────────────────────────

    @Test
    void publishWithoutCoverGeneratesUploadCover() throws Exception {
        User user = createUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(mediaStorageService.storeUpload(any(), eq("videos"), anyString())).thenReturn(VIDEO_URL);
        when(mediaStorageService.storeGeneratedCover(anyString(), eq("covers"), anyString())).thenReturn(COVER_URL);
        when(videoRepository.save(any(Video.class))).thenAnswer(inv -> {
            Video v = inv.getArgument(0);
            v.setId(99L);
            return v;
        });

        MockMultipartFile videoFile = new MockMultipartFile(
                "video", "clip.mp4", "video/mp4", "fake-video-data".getBytes());

        mockMvc.perform(multipart("/api/v1/videos")
                        .file(videoFile)
                        .param("title", "Demo video")
                        .param("description", "test desc")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Video published successfully!"))
                .andExpect(jsonPath("$.data.id").value(99))
                .andExpect(jsonPath("$.data.title").value("Demo video"))
                .andExpect(jsonPath("$.data.description").value("test desc"))
                .andExpect(jsonPath("$.data.video_url").value(VIDEO_URL))
                .andExpect(jsonPath("$.data.cover_url").value(COVER_URL))
                .andExpect(jsonPath("$.data.cover_url", not(containsString("placehold.co"))))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.creator_name").value("testuser"));

        verify(mediaStorageService).storeGeneratedCover(anyString(), eq("covers"), anyString());
    }

    @Test
    void publishWithCustomCoverUsesUploadedCover() throws Exception {
        User user = createUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(mediaStorageService.storeUpload(any(), eq("videos"), anyString())).thenReturn(VIDEO_URL);
        when(mediaStorageService.storeUpload(any(), eq("covers"), anyString())).thenReturn("/uploads/covers/custom-cover.jpg");
        when(videoRepository.save(any(Video.class))).thenAnswer(inv -> {
            Video v = inv.getArgument(0);
            v.setId(100L);
            return v;
        });

        MockMultipartFile videoFile = new MockMultipartFile(
                "video", "clip.mp4", "video/mp4", "fake-video-data".getBytes());
        MockMultipartFile coverFile = new MockMultipartFile(
                "cover", "my-cover.png", "image/png", "fake-png-data".getBytes());

        mockMvc.perform(multipart("/api/v1/videos")
                        .file(videoFile)
                        .file(coverFile)
                        .param("title", "With Cover")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cover_url").value("/uploads/covers/custom-cover.jpg"))
                .andExpect(jsonPath("$.data.cover_url", not(containsString("placehold.co"))));

        // Should NOT call auto-generate when custom cover provided
        verify(mediaStorageService, never()).storeGeneratedCover(anyString(), anyString(), anyString());
    }

    // ── F05: 参数校验 ──────────────────────────────────────────────

    @Test
    void publishWithoutTitleReturns400() throws Exception {
        MockMultipartFile videoFile = new MockMultipartFile(
                "video", "clip.mp4", "video/mp4", "fake-data".getBytes());

        mockMvc.perform(multipart("/api/v1/videos")
                        .file(videoFile)
                        .param("title", "   ")  // blank title
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Video title is required."));
    }

    @Test
    void publishWithoutVideoFileReturnsServerError() throws Exception {
        // MissingServletRequestPartException is NOT mapped in GlobalExceptionHandler
        // (only MissingServletRequestParameterException is). Falls to generic 500.
        mockMvc.perform(multipart("/api/v1/videos")
                        .param("title", "No video file")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().is5xxServerError());
    }

    // ── F05: 认证鉴权 ──────────────────────────────────────────────

    @Test
    void publishWithoutUserIdReturnsClientError() throws Exception {
        MockMultipartFile videoFile = new MockMultipartFile(
                "video", "clip.mp4", "video/mp4", "fake-data".getBytes());

        // userId not set → userRepository.findById(null) → IllegalArgumentException
        // → GlobalExceptionHandler.handleBadRequest → 400
        // In production, JWT interceptor catches this before controller and returns 401
        mockMvc.perform(multipart("/api/v1/videos")
                        .file(videoFile)
                        .param("title", "Test"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void publishWithInvalidUserReturns401() throws Exception {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        MockMultipartFile videoFile = new MockMultipartFile(
                "video", "clip.mp4", "video/mp4", "fake-data".getBytes());

        mockMvc.perform(multipart("/api/v1/videos")
                        .file(videoFile)
                        .param("title", "Test")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User session invalid."));
    }

    // ── helpers ────────────────────────────────────────────────────

    private User createUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("testuser");
        user.setAvatarUrl("/uploads/avatars/default.png");
        return user;
    }

    private static class NoOpRedisCacheService extends RedisCacheService {
        NoOpRedisCacheService() { super(null, null); }

        @Override
        public Optional<Map<String, Object>> getMap(String key) { return Optional.empty(); }

        @Override
        public void put(String key, Object value, Duration ttl) {}

        @Override
        public void evict(String key) {}

        @Override
        public void evictPrefix(String prefix) {}
    }
}
