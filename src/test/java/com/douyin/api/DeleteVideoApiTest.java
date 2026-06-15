package com.douyin.api;

import com.douyin.api.controller.VideoController;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.repository.*;
import com.douyin.api.service.RedisCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DeleteVideoApiTest {

    private MockMvc mockMvc;

    @Mock private UserRepository userRepository;
    @Mock private VideoRepository videoRepository;
    @Mock private LikeRepository likeRepository;
    @Mock private ViewRepository viewRepository;
    @Mock private ShareRepository shareRepository;
    @Mock private CommentRepository commentRepository;

    private User owner;
    private User otherUser;
    private Video video;

    @BeforeEach
    void setUp() {
        VideoController videoController = new VideoController(
                userRepository,
                videoRepository,
                likeRepository,
                viewRepository,
                shareRepository,
                commentRepository,
                new NoOpRedisCacheService()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(videoController).build();

        owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("other");

        video = new Video();
        video.setId(42L);
        video.setUser(owner);
        video.setTitle("Test Video");
        video.setLikesCount(0);
        video.setVideoUrl("/uploads/videos/test.mp4");
        video.setCoverUrl("/uploads/covers/test.jpg");
    }

    @Test
    void ownerCanDeleteOwnVideo() throws Exception {
        when(videoRepository.findById(42L)).thenReturn(Optional.of(video));

        mockMvc.perform(authenticatedDelete("/api/v1/videos/42", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Video deleted"));

        verify(likeRepository).deleteByVideoId(42L);
        verify(viewRepository).deleteByVideoId(42L);
        verify(videoRepository).delete(video);
    }

    @Test
    void nonOwnerCannotDeleteOthersVideo() throws Exception {
        when(videoRepository.findById(42L)).thenReturn(Optional.of(video));

        mockMvc.perform(authenticatedDelete("/api/v1/videos/42", 2L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Forbidden: you can only delete your own videos"));

        verify(videoRepository, never()).delete(video);
        verify(likeRepository, never()).deleteByVideoId(42L);
        verify(viewRepository, never()).deleteByVideoId(42L);
    }

    @Test
    void deleteNonExistentVideoReturns404() throws Exception {
        when(videoRepository.findById(99999L)).thenReturn(Optional.empty());

        mockMvc.perform(authenticatedDelete("/api/v1/videos/99999", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Video not found"));

        verify(videoRepository, never()).delete(video);
    }

    @Test
    void deleteWithoutUserIdAttributeReturns404WhenVideoNotFound() throws Exception {
        when(videoRepository.findById(42L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/videos/42")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deleteRemovesLikesAndViewsBeforeVideo() throws Exception {
        when(videoRepository.findById(42L)).thenReturn(Optional.of(video));

        mockMvc.perform(authenticatedDelete("/api/v1/videos/42", 1L))
                .andExpect(status().isOk());

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(
                likeRepository, viewRepository, videoRepository);
        inOrder.verify(likeRepository).deleteByVideoId(42L);
        inOrder.verify(viewRepository).deleteByVideoId(42L);
        inOrder.verify(videoRepository).delete(video);
    }

    private MockHttpServletRequestBuilder authenticatedDelete(String url, Long userId) {
        return delete(url)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON);
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