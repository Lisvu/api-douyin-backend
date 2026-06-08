package com.douyin.api;

import com.douyin.api.model.User;
import com.douyin.api.model.Video;
import com.douyin.api.repository.UserRepository;
import com.douyin.api.repository.VideoRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
@EnableCaching
public class ApiDouyinApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiDouyinApplication.class, args);
    }

    @Bean
    public CommandLineRunner demo(UserRepository userRepository, VideoRepository videoRepository) {
        return args -> {
            if (userRepository.count() == 0) {
                System.out.println("====== Seeding Database with High-Quality TikTok Videos ======");
                
                // Create a default creator user
                User creator = new User();
                creator.setUsername("douyin_creator");
                creator.setPassword(BCrypt.hashpw("password123", BCrypt.gensalt(10)));
                userRepository.save(creator);

                // Seed videos
                Video v1 = new Video();
                v1.setUser(creator);
                v1.setTitle("火山引擎特效展示");
                v1.setDescription("西瓜视频与火山引擎的高清前端视频渲染测试。#字节跳动 #西瓜视频 #开发日常");
                v1.setVideoUrl("https://sf1-cdn-tos.huoshanstatic.com/obj/media-fe/xgplayer_doc_video/mp4/xgplayer-demo-360p.mp4");
                v1.setCoverUrl("https://images.unsplash.com/photo-1542831371-29b0f74f9713?w=800&auto=format&fit=crop&q=60");
                v1.setLikesCount(320);

                Video v2 = new Video();
                v2.setUser(creator);
                v2.setTitle("DCloud 移动端跨平台技术");
                v2.setDescription("uni-app 跨平台开发核心技术讲解与移动开发实践展示。#移动开发 #前端 #uni-app");
                v2.setVideoUrl("https://qiniu-web-assets.dcloud.net.cn/unidoc/zh/uni-app-video-courses.mp4");
                v2.setCoverUrl("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&auto=format&fit=crop&q=60");
                v2.setLikesCount(580); // Highest likes, recommended first!

                Video v3 = new Video();
                v3.setUser(creator);
                v3.setTitle("七牛云 1080P 极光画质演示");
                v3.setDescription("七牛云 SDK 官方提供的 1080P 60帧极速高清流媒体画面展示。#画质党 #高清 #云服务");
                v3.setVideoUrl("https://sdk-release.qnsdk.com/1080_60_5000k.mp4");
                v3.setCoverUrl("https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&auto=format&fit=crop&q=60");
                v3.setLikesCount(140);

                Video v4 = new Video();
                v4.setUser(creator);
                v4.setTitle("可爱小熊步行动画");
                v4.setDescription("W3School 官方托管的超可爱三维卡通角色测试镜头。#可爱 #卡通 #CG动画");
                v4.setVideoUrl("https://www.w3school.com.cn/i/movie.mp4");
                v4.setCoverUrl("https://images.unsplash.com/photo-1473968512647-3e447244af8f?w=800&auto=format&fit=crop&q=60");
                v4.setLikesCount(450); // Second highest, recommended second!

                Video v5 = new Video();
                v5.setUser(creator);
                v5.setTitle("七牛云 2K 色彩解码兼容性测试");
                v5.setDescription("七牛云官方专业色彩与多码率视频解码测试画面。#数码 #解码测试 #极客");
                v5.setVideoUrl("https://sdk-release.qnsdk.com/2K_60_6048k.mp4");
                v5.setCoverUrl("https://images.unsplash.com/photo-1542831371-29b0f74f9713?w=800&auto=format&fit=crop&q=60");
                v5.setLikesCount(290);

                Video v6 = new Video();
                v6.setUser(creator);
                v6.setTitle("云上流媒体解析数据");
                v6.setDescription("专门用于测试流媒体播放器兼容性与分轨播放的公共数据流。#流媒体 #技术分享");
                v6.setVideoUrl("https://sdk-release.qnsdk.com/mp4.mp4");
                v6.setCoverUrl("https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&auto=format&fit=crop&q=60");
                v6.setLikesCount(60);

                videoRepository.saveAll(List.of(v1, v2, v3, v4, v5, v6));
                System.out.println("====== Seeding Complete! ======");
            }
        };
    }
}
