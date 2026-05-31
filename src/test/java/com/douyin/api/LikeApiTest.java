package com.douyin.api;

import com.douyin.api.controller.VideoController;
import com.douyin.api.model.Like;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LikeApiTest {

    private MockMvc mockMvc;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ViewRepository viewRepository;

    @InjectMocks
    private VideoController videoController;

    private User user;
    private Video video;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(videoController).build();

        user = new User();
        user.setId(1L);
        user.setUsername("tester");

        video = new Video();
        video.setId(9L);
        video.setUser(user);
        video.setTitle("Like demo");
        video.setLikesCount(3);
    }

    @Test
    void toggleLikeCreatesLikeAndReturnsCanonicalFields() throws Exception {
        when(videoRepository.findById(9L)).thenReturn(Optional.of(video));
        when(likeRepository.findByUserIdAndVideoId(1L, 9L)).thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> {
            Video saved = invocation.getArgument(0);
            saved.setLikesCount(4);
            return saved;
        });

        mockMvc.perform(authenticatedPut("/api/v1/videos/9/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(4))
                .andExpect(jsonPath("$.likes_count").value(4));

        verify(likeRepository).save(any(Like.class));
    }

    @Test
    void toggleLikeRemovesExistingLike() throws Exception {
        Like existing = new Like(1L, 9L);
        when(videoRepository.findById(9L)).thenReturn(Optional.of(video));
        when(likeRepository.findByUserIdAndVideoId(1L, 9L)).thenReturn(Optional.of(existing));
        when(videoRepository.save(any(Video.class))).thenAnswer(invocation -> {
            Video saved = invocation.getArgument(0);
            saved.setLikesCount(2);
            return saved;
        });

        mockMvc.perform(authenticatedPut("/api/v1/videos/9/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(2));

        verify(likeRepository).delete(existing);
    }

    @Test
    void duplicateLikeRaceReturnsIdempotentLikedState() throws Exception {
        when(videoRepository.findById(9L)).thenReturn(Optional.of(video));
        when(likeRepository.findByUserIdAndVideoId(1L, 9L)).thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        mockMvc.perform(authenticatedPut("/api/v1/videos/9/like"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(3))
                .andExpect(jsonPath("$.message").value("Video already liked."));
    }

    private MockHttpServletRequestBuilder authenticatedPut(String url) {
        return put(url)
                .requestAttr("userId", 1L)
                .contentType(MediaType.APPLICATION_JSON);
    }
}
