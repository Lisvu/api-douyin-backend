package com.douyin.api;

import com.douyin.api.controller.VideoController;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Pageable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecommendationApiTest {

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
    private Video topVideo;
    private Video secondVideo;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(videoController).build();

        user = new User();
        user.setId(1L);
        user.setUsername("douyin_creator");

        topVideo = buildVideo(2L, "Top liked", 580);
        secondVideo = buildVideo(4L, "Second liked", 450);
    }

    @Test
    void getRecommendationsReturnsVideosSortedByLikesExcludingViewed() throws Exception {
        when(videoRepository.findRecommendedVideosForUser(eq(1L), any(Pageable.class))).thenReturn(List.of(topVideo, secondVideo));
        when(videoRepository.count()).thenReturn(6L);
        when(likeRepository.findLikedVideoIds(eq(1L), any())).thenReturn(Set.of(4L));

        mockMvc.perform(get("/api/v1/videos/recommendations").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.videos.length()").value(2))
                .andExpect(jsonPath("$.videos[0].id").value(2))
                .andExpect(jsonPath("$.videos[0].likes_count").value(580))
                .andExpect(jsonPath("$.videos[0].liked").value(false))
                .andExpect(jsonPath("$.videos[1].id").value(4))
                .andExpect(jsonPath("$.videos[1].liked").value(true))
                .andExpect(jsonPath("$.allViewed").value(false))
                .andExpect(jsonPath("$.totalCount").value(6));
    }

    @Test
    void getRecommendationsReturnsAllViewedWhenListEmpty() throws Exception {
        when(videoRepository.findRecommendedVideosForUser(eq(1L), any(Pageable.class))).thenReturn(List.of());
        when(videoRepository.count()).thenReturn(6L);

        mockMvc.perform(get("/api/v1/videos/recommendations").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.videos").isEmpty())
                .andExpect(jsonPath("$.allViewed").value(true))
                .andExpect(jsonPath("$.totalCount").value(6));
    }

    @Test
    void recordViewCreatesViewWhenNotExists() throws Exception {
        when(viewRepository.existsByUserIdAndVideoId(1L, 2L)).thenReturn(false);
        when(videoRepository.findById(2L)).thenReturn(Optional.of(topVideo));

        mockMvc.perform(post("/api/v1/videos/2/views").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Video marked as viewed."));

        verify(viewRepository).save(any());
    }

    @Test
    void recordViewIsIdempotentWhenAlreadyViewed() throws Exception {
        when(viewRepository.existsByUserIdAndVideoId(1L, 2L)).thenReturn(true);

        mockMvc.perform(post("/api/v1/videos/2/views").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(viewRepository, never()).save(any());
        verify(videoRepository, never()).findById(eq(2L));
    }

    @Test
    void recordViewReturns404WhenVideoMissing() throws Exception {
        when(viewRepository.existsByUserIdAndVideoId(1L, 99L)).thenReturn(false);
        when(videoRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/videos/99/views").requestAttr("userId", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Video not found."));
    }

    @Test
    void resetViewsDeletesCurrentUserHistory() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/views").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Your watch history has been reset. All videos can be recommended again!"));

        verify(viewRepository).deleteByUserId(1L);
    }

    private Video buildVideo(Long id, String title, int likes) {
        Video video = new Video();
        video.setId(id);
        video.setUser(user);
        video.setTitle(title);
        video.setDescription("demo");
        video.setVideoUrl("https://example.com/video.mp4");
        video.setCoverUrl("https://example.com/cover.jpg");
        video.setLikesCount(likes);
        return video;
    }
}
