package com.douyin.api;

import com.douyin.api.controller.UserController;
import com.douyin.api.model.User;
import com.douyin.api.repository.CommentRepository;
import com.douyin.api.repository.FavoriteRepository;
import com.douyin.api.repository.LikeNotificationProjection;
import com.douyin.api.repository.LikeRepository;
import com.douyin.api.repository.UserRelationRepository;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.VideoRepository;
import com.douyin.api.repository.ViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LikeNotificationApiTest {

    private MockMvc mockMvc;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private ViewRepository viewRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserRelationRepository userRelationRepository;

    @InjectMocks
    private UserController userController;

    private User owner;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();

        owner = new User();
        owner.setId(2L);
        owner.setUsername("creator");
    }

    @Test
    void getLikeNotificationsReturnsPagedItemsAndUnreadCount() throws Exception {
        LikeNotificationProjection notification = new LikeNotificationProjection() {
            @Override
            public Long getLikeId() {
                return 11L;
            }

            @Override
            public Long getLikerUserId() {
                return 1L;
            }

            @Override
            public String getLikerUsername() {
                return "fan";
            }

            @Override
            public String getLikerDisplayName() {
                return "Fan User";
            }

            @Override
            public Long getVideoId() {
                return 9L;
            }

            @Override
            public String getVideoTitle() {
                return "My video";
            }

            @Override
            public LocalDateTime getLikedAt() {
                return LocalDateTime.of(2026, 5, 22, 12, 0);
            }
        };

        Page<LikeNotificationProjection> page = new PageImpl<>(List.of(notification));
        when(userRepository.findById(2L)).thenReturn(Optional.of(owner));
        when(likeRepository.findReceivedLikeNotifications(eq(2L), any(Pageable.class))).thenReturn(page);
        when(likeRepository.countReceivedLikes(2L)).thenReturn(1L);

        mockMvc.perform(authenticatedGet("/api/v1/users/me/like-notifications?page=1&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.notifications[0].likeId").value(11))
                .andExpect(jsonPath("$.notifications[0].likerUsername").value("fan"))
                .andExpect(jsonPath("$.notifications[0].videoTitle").value("My video"))
                .andExpect(jsonPath("$.notifications[0].read").value(false))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.total").value(1));
    }

    @Test
    void markLikeNotificationsReadUpdatesTimestamp() throws Exception {
        when(userRepository.findById(2L)).thenReturn(Optional.of(owner));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(authenticatedPut("/api/v1/users/me/like-notifications/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.unreadCount").value(0));

        verify(userRepository).save(any(User.class));
    }

    private MockHttpServletRequestBuilder authenticatedGet(String url) {
        return get(url)
                .requestAttr("userId", 2L)
                .accept(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder authenticatedPut(String url) {
        return put(url)
                .requestAttr("userId", 2L)
                .accept(MediaType.APPLICATION_JSON);
    }
}
