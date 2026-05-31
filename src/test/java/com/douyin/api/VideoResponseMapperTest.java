package com.douyin.api;

import com.douyin.api.util.VideoResponseMapper;
import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VideoResponseMapperTest {

    @Test
    void feedItemUsesCanonicalLikeFieldsAndLegacyAliases() {
        User author = new User();
        author.setId(7L);
        author.setUsername("creator");

        Video video = new Video();
        video.setId(42L);
        video.setUser(author);
        video.setTitle("demo");
        video.setLikesCount(12);

        Map<String, Object> item = VideoResponseMapper.toFeedItem(video, true);

        assertThat(item.get("liked")).isEqualTo(true);
        assertThat(item.get("likeCount")).isEqualTo(12);
        assertThat(item.get("is_liked")).isEqualTo(1);
        assertThat(item.get("likes_count")).isEqualTo(12);
    }

    @Test
    void toggleResponseIncludesCanonicalAndLegacyLikeFields() {
        Map<String, Object> response = new HashMap<>();
        VideoResponseMapper.putLikeFields(response, false, 5);

        assertThat(response.get("liked")).isEqualTo(false);
        assertThat(response.get("likeCount")).isEqualTo(5);
        assertThat(response.get("likes_count")).isEqualTo(5);
    }
}
