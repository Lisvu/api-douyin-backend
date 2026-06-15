package com.douyin.api;

import com.douyin.api.controller.VideoController;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.repository.CommentRepository;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.ShareRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
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

    @BeforeEach
    void setUp() {
        VideoController videoController = new VideoController(
                userRepository,
                videoRepository,
                likeRepository,
                viewRepository,
                shareRepository,
                commentRepository,
                new NoOpRedisCacheService(),
                TestMediaStorage.create()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(videoController).build();
    }

    @Test
    void publishWithoutCoverGeneratesUploadCoverInsteadOfExternalPlaceholder() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("creator");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> {
            Video saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        MockMultipartFile videoFile = new MockMultipartFile(
                "video",
                "clip.mp4",
                "video/mp4",
                "fake-video-data".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/videos")
                        .file(videoFile)
                        .param("title", "Demo video")
                        .param("description", "test")
                        .requestAttr("userId", 1L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.video_url", containsString("/uploads/videos/video-")))
                .andExpect(jsonPath("$.data.cover_url", containsString("/uploads/covers/cover-")))
                .andExpect(jsonPath("$.data.cover_url", not(containsString("placehold.co"))));
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
