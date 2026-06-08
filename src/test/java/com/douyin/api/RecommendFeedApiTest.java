package com.douyin.api;

import com.douyin.api.controller.VideoController;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.model.View;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecommendFeedApiTest {

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

    private User author;
    private Video highLikes;
    private Video lowLikes;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(videoController).build();

        author = new User();
        author.setId(1L);
        author.setUsername("douyin_creator");

        highLikes = buildVideo(2L, "High", 580);
        lowLikes = buildVideo(3L, "Low", 140);
    }

    @Test
    void recommendationsReturnVideosSortedByLikesDesc() throws Exception {
        when(videoRepository.findRecommendedVideosForUser(1L)).thenReturn(List.of(highLikes, lowLikes));
        when(videoRepository.count()).thenReturn(6L);
        when(likeRepository.existsByUserIdAndVideoId(1L, 2L)).thenReturn(false);
        when(likeRepository.existsByUserIdAndVideoId(1L, 3L)).thenReturn(true);

        mockMvc.perform(get("/api/v1/videos/recommendations").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.videos.length()").value(2))
                .andExpect(jsonPath("$.videos[0].id").value(2))
                .andExpect(jsonPath("$.videos[0].likeCount").value(580))
                .andExpect(jsonPath("$.videos[0].liked").value(false))
                .andExpect(jsonPath("$.videos[1].liked").value(true))
                .andExpect(jsonPath("$.allViewed").value(false))
                .andExpect(jsonPath("$.totalCount").value(6));
    }

    @Test
    void recordViewReturns404WhenVideoMissing() throws Exception {
        when(viewRepository.existsByUserIdAndVideoId(1L, 99L)).thenReturn(false);
        when(videoRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/videos/99/views")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Video not found."));
    }

    @Test
    void recordViewIsIdempotentWhenAlreadyViewed() throws Exception {
        when(viewRepository.existsByUserIdAndVideoId(1L, 2L)).thenReturn(true);

        mockMvc.perform(post("/api/v1/videos/2/views")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(viewRepository, never()).save(any(View.class));
        verify(videoRepository, never()).findById(2L);
    }

    @Test
    void resetViewsClearsHistoryForCurrentUser() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/views").requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(viewRepository).deleteByUserId(1L);
    }

    private Video buildVideo(Long id, String title, int likes) {
        Video video = new Video();
        video.setId(id);
        video.setUser(author);
        video.setTitle(title);
        video.setDescription("demo");
        video.setVideoUrl("https://example.com/v.mp4");
        video.setCoverUrl("https://example.com/c.jpg");
        video.setLikesCount(likes);
        return video;
    }
}
