package com.douyin.api;

import com.douyin.api.controller.FollowController;
import com.douyin.api.model.UserRelation;
import com.douyin.api.repository.UserRelationRepository;
import com.douyin.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FollowControllerTest {

    private MockMvc mockMvc;

    @Mock private UserRepository userRepository;
    @Mock private UserRelationRepository userRelationRepository;

    @BeforeEach
    void setUp() {
        FollowController controller = new FollowController(userRepository, userRelationRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── 关注接口 POST /api/v1/users/{id}/follow ────────────────

    @Test
    void followUserSuccessfully() throws Exception {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(userRelationRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());
        when(userRelationRepository.findByFollowerIdAndFollowingId(2L, 1L)).thenReturn(Optional.empty());
        when(userRelationRepository.save(any())).thenReturn(new UserRelation());
        when(userRelationRepository.countByFollowerId(1L)).thenReturn(1L);

        mockMvc.perform(post("/api/v1/users/2/follow")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.isFriend").value(false));

        verify(userRelationRepository).save(any(UserRelation.class));
    }

    @Test
    void followSelfReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/users/1/follow")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("不能关注自己"));

        verify(userRelationRepository, never()).save(any());
    }

    @Test
    void followNonExistentUserReturns404() throws Exception {
        when(userRepository.existsById(99L)).thenReturn(false);

        mockMvc.perform(post("/api/v1/users/99/follow")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void followAlreadyFollowedUserReturnsBadRequest() throws Exception {
        UserRelation existing = new UserRelation();
        existing.setFollowerId(1L);
        existing.setFollowingId(2L);

        when(userRepository.existsById(2L)).thenReturn(true);
        when(userRelationRepository.findByFollowerIdAndFollowingId(1L, 2L))
                .thenReturn(Optional.of(existing));

        mockMvc.perform(post("/api/v1/users/2/follow")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("已经关注该用户"));

        verify(userRelationRepository, never()).save(any());
    }

    @Test
    void followWithoutLoginReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/users/2/follow")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void mutualFollowSetsFriendTrue() throws Exception {
        // 用户1关注用户2，且用户2已经关注了用户1 → 互关好友
        UserRelation reverseRelation = new UserRelation();
        reverseRelation.setFollowerId(2L);
        reverseRelation.setFollowingId(1L);

        when(userRepository.existsById(2L)).thenReturn(true);
        when(userRelationRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());
        when(userRelationRepository.findByFollowerIdAndFollowingId(2L, 1L))
                .thenReturn(Optional.of(reverseRelation));
        when(userRelationRepository.save(any())).thenReturn(new UserRelation());
        when(userRelationRepository.countByFollowerId(1L)).thenReturn(1L);

        mockMvc.perform(post("/api/v1/users/2/follow")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.isFriend").value(true))
                .andExpect(jsonPath("$.message").value("关注成功，你们已成为好友！"));
    }

    // ── 取关接口 DELETE /api/v1/users/{id}/follow ──────────────

    @Test
    void unfollowUserSuccessfully() throws Exception {
        UserRelation relation = new UserRelation();
        relation.setFollowerId(1L);
        relation.setFollowingId(2L);

        when(userRelationRepository.findByFollowerIdAndFollowingId(1L, 2L))
                .thenReturn(Optional.of(relation));
        when(userRelationRepository.countByFollowerId(1L)).thenReturn(0L);

        mockMvc.perform(delete("/api/v1/users/2/follow")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("已取消关注"));

        verify(userRelationRepository).deleteByFollowerIdAndFollowingId(1L, 2L);
    }

    @Test
    void unfollowNotFollowedUserReturnsBadRequest() throws Exception {
        when(userRelationRepository.findByFollowerIdAndFollowingId(1L, 2L))
                .thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/users/2/follow")
                        .requestAttr("userId", 1L)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("你还没有关注该用户"));

        verify(userRelationRepository, never()).deleteByFollowerIdAndFollowingId(anyLong(), anyLong());
    }

    // ── 列表接口 ────────────────────────────────────────────────

    @Test
    void getFollowingReturnsEmptyList() throws Exception {
        when(userRelationRepository.findByFollowerId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/me/following")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void getFollowersReturnsEmptyList() throws Exception {
        when(userRelationRepository.findByFollowingId(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/me/followers")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void getFriendsReturnsEmptyWhenNoMutualFollows() throws Exception {
        when(userRelationRepository.findMutualFollows(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/me/friends")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void getRelationReturnsCorrectFlags() throws Exception {
        // 用户1关注了用户2，用户2没有关注用户1
        UserRelation relation = new UserRelation();
        relation.setFollowerId(1L);
        relation.setFollowingId(2L);

        when(userRelationRepository.findByFollowerIdAndFollowingId(1L, 2L))
                .thenReturn(Optional.of(relation));
        when(userRelationRepository.findByFollowerIdAndFollowingId(2L, 1L))
                .thenReturn(Optional.empty());
        when(userRelationRepository.countByFollowerId(1L)).thenReturn(1L);
        when(userRelationRepository.countByFollowingId(1L)).thenReturn(0L);

        mockMvc.perform(get("/api/v1/users/2/relation")
                        .requestAttr("userId", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.isFollowing").value(true))
                .andExpect(jsonPath("$.isFollowedBy").value(false))
                .andExpect(jsonPath("$.isFriend").value(false));
    }
}