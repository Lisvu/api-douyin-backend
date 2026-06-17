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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MyVideosApiTest {

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

    // ── F06: 正常查询 ──────────────────────────────────────────────

    @Test
    void getMyVideosReturnsPaginatedList() throws Exception {
        List<Video> videos = createVideoList(USER_ID, 3);
        when(videoRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER_ID), any()))
                .thenReturn(videos);
        when(likeRepository.findLikedVideoIds(eq(USER_ID), anyList())).thenReturn(Collections.emptySet());
        when(favoriteRepository.findFavoritedVideoIds(eq(USER_ID), anyList())).thenReturn(Collections.emptySet());
        when(commentRepository.countGroupByVideoIds(anyList())).thenReturn(Collections.emptyList());
        when(favoriteRepository.countGroupByVideoIds(anyList())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users/me/videos")
                        .param("limit", "8")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.videos").isArray())
                .andExpect(jsonPath("$.videos", hasSize(3)))
                .andExpect(jsonPath("$.videos[0].title").value("Video 3"))
                .andExpect(jsonPath("$.videos[1].title").value("Video 2"))
                .andExpect(jsonPath("$.videos[2].title").value("Video 1"))
                .andExpect(jsonPath("$.pagination.limit").value(8))
                .andExpect(jsonPath("$.pagination.hasMore").value(false));
    }

    // ── F06: 空列表 ────────────────────────────────────────────────

    @Test
    void getMyVideosEmptyListReturns200() throws Exception {
        when(videoRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER_ID), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users/me/videos")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.videos").isArray())
                .andExpect(jsonPath("$.videos", hasSize(0)))
                .andExpect(jsonPath("$.pagination.hasMore").value(false));
    }

    // ── F06: cursor 分页 ───────────────────────────────────────────

    @Test
    void getMyVideosCursorPaginationHasMore() throws Exception {
        List<Video> videos = createVideoList(USER_ID, 9);
        when(videoRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER_ID), any()))
                .thenReturn(videos);
        when(likeRepository.findLikedVideoIds(eq(USER_ID), anyList())).thenReturn(Collections.emptySet());
        when(favoriteRepository.findFavoritedVideoIds(eq(USER_ID), anyList())).thenReturn(Collections.emptySet());
        when(commentRepository.countGroupByVideoIds(anyList())).thenReturn(Collections.emptyList());
        when(favoriteRepository.countGroupByVideoIds(anyList())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users/me/videos")
                        .param("limit", "8")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.videos", hasSize(8)))
                .andExpect(jsonPath("$.pagination.hasMore").value(true))
                .andExpect(jsonPath("$.pagination.nextCursor").isNotEmpty());
    }

    @Test
    void getMyVideosWithCursorReturnsNextPage() throws Exception {
        List<Video> videos = createVideoList(USER_ID, 2);
        when(videoRepository.findByUserIdBeforeCursor(eq(USER_ID), any(), anyLong(), any()))
                .thenReturn(videos);
        when(likeRepository.findLikedVideoIds(eq(USER_ID), anyList())).thenReturn(Collections.emptySet());
        when(favoriteRepository.findFavoritedVideoIds(eq(USER_ID), anyList())).thenReturn(Collections.emptySet());
        when(commentRepository.countGroupByVideoIds(anyList())).thenReturn(Collections.emptyList());
        when(favoriteRepository.countGroupByVideoIds(anyList())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users/me/videos")
                        .param("cursor", "MjAyNi0wNi0xN1QxMjowMDowMHwxNQ==")
                        .param("limit", "8")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.videos", hasSize(2)))
                .andExpect(jsonPath("$.pagination.hasMore").value(false));
    }

    @Test
    void getMyVideosInvalidCursorReturns500() throws Exception {
        // decodeCursor throws ResponseStatusException, but the try-catch in
        // getMyVideos wraps it as INTERNAL_SERVER_ERROR
        mockMvc.perform(get("/api/v1/users/me/videos")
                        .param("cursor", "!!!invalid-base64!!!")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── F06: limit 边界值 ──────────────────────────────────────────

    @Test
    void getMyVideosClampLimitTo50() throws Exception {
        when(videoRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER_ID), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users/me/videos")
                        .param("limit", "100")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.limit").value(50));
    }

    @Test
    void getMyVideosClampLimitZeroToOne() throws Exception {
        when(videoRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER_ID), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users/me/videos")
                        .param("limit", "0")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.limit").value(1));
    }

    // ── F06: 未登录 ─────────────────────────────────────────────────

    @Test
    void getMyVideosWithoutUserIdStubReturnsEmpty() throws Exception {
        // Without JWT interceptor, userId attribute may be null.
        // Controller passes null directly to repository; stub to return empty.
        when(videoRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(null), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users/me/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videos", hasSize(0)));
    }

    // ── F06: 视频项字段完整性 ──────────────────────────────────────

    @Test
    void getMyVideosReturnsCompleteVideoFields() throws Exception {
        List<Video> videos = createVideoList(USER_ID, 1);
        when(videoRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER_ID), any()))
                .thenReturn(videos);
        // Simulate liked state (video id = 101 when count=1)
        when(likeRepository.findLikedVideoIds(eq(USER_ID), anyList()))
                .thenReturn(Set.of(101L));
        when(favoriteRepository.findFavoritedVideoIds(eq(USER_ID), anyList()))
                .thenReturn(Collections.emptySet());
        when(commentRepository.countGroupByVideoIds(anyList())).thenReturn(Collections.emptyList());
        when(favoriteRepository.countGroupByVideoIds(anyList())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users/me/videos")
                        .param("limit", "8")
                        .requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videos[0].id").value(101))
                .andExpect(jsonPath("$.videos[0].user_id").value(USER_ID.intValue()))
                .andExpect(jsonPath("$.videos[0].title").value("Video 1"))
                .andExpect(jsonPath("$.videos[0].video_url").value("https://example.com/videos/1.mp4"))
                .andExpect(jsonPath("$.videos[0].cover_url").value("https://example.com/covers/1.jpg"))
                .andExpect(jsonPath("$.videos[0].likeCount").value(10))
                .andExpect(jsonPath("$.videos[0].liked").value(true))
                .andExpect(jsonPath("$.videos[0].is_liked").value(1))
                .andExpect(jsonPath("$.videos[0].likes_count").value(10))
                .andExpect(jsonPath("$.videos[0].creator_name").value("owner_user"))
                .andExpect(jsonPath("$.videos[0].comments_count").isNumber())
                .andExpect(jsonPath("$.videos[0].status").value("published"))
                .andExpect(jsonPath("$.videos[0].created_at").isString());
    }

    // ── helpers ────────────────────────────────────────────────────

    /**
     * Creates videos with https:// URLs so LocalMediaAvailability.isPlayableUrl()
     * always returns true (remote URLs are always considered playable).
     */
    private List<Video> createVideoList(Long ownerId, int count) {
        List<Video> videos = new ArrayList<>();
        for (int i = count; i >= 1; i--) {
            Video video = new Video();
            video.setId((long) (100 + i));
            video.setTitle("Video " + i);
            video.setDescription("Desc " + i);
            video.setVideoUrl("https://example.com/videos/" + i + ".mp4");
            video.setCoverUrl("https://example.com/covers/" + i + ".jpg");
            video.setLikesCount(i * 10);
            video.setCreatedAt(LocalDateTime.now().minusHours(i));

            User user = new User();
            user.setId(ownerId);
            user.setUsername("owner_user");
            user.setAvatarUrl("/uploads/avatars/default.png");
            video.setUser(user);

            videos.add(video);
        }
        return videos;
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
