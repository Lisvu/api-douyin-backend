# 🚀 抖音短视频 - Java 后端开发手册 (Java Backend Guide)

欢迎加入 **抖音短视频前后端分离平台** 联合开发团队！本项目后端基于 **Java 21 + Spring Boot 3.4.0 + JPA + PostgreSQL** 开发，采用工业级分层架构模型。

---

## 📂 项目简介 & 目录分工

本项目致力于打造一个高性能、高可靠的短视频社交平台后端 API。核心源码均位于 `src/main/java/com/douyin/api/` 目录下，整体遵循以下目录结构与分工规范：

```text
com.douyin.api/
├── ApiDouyinApplication.java   # Spring Boot 主入口
│
├── controller/                 # 1. 接口暴露层 (REST Controller)
│   │                           # 【分工】：定义前端 API 路由，参数基本格式校验与拦截，调用 Service 层
│   ├── AuthController.java     # 账户注册、登录与 Token 刷新
│   ├── VideoController.java    # 短视频浏览、发布、点赞、评论等接口
│   └── AdminController.java    # 开发者后台与性能监控面板
│
├── service/                    # 2. 业务契约层 (Service Interfaces)
│   │                           # 【分工】：仅声明业务逻辑接口，作为前后台及多人协作的协议防线
│   └── impl/                   # 3. 业务实现层 (Service Implementations)
│                               # 【分工】：编写具体的业务算法与控制逻辑（如：点赞防刷、推荐模型计算等）
│
├── model/                      # 4. 数据实体层 (JPA Entities)
│   │                           # 【分工】：使用 JPA 注解映射 PostgreSQL 物理表结构，定义关联关系与索引
│   ├── User.java               # 用户实体
│   ├── Video.java              # 视频实体
│   └── UserRelation.java       # 社交关注关系实体
│
├── repository/                 # 5. 数据防线层 (Repositories)
│   │                           # 【分工】：继承 JpaRepository，执行底层 SQL 增删改查。包含复杂查询的 JPQL
│   ├── UserRepository.java     
│   ├── VideoRepository.java    
│   └── UserRelationRepository.java
│
├── dto/                        # 6. 入参接收层 (Data Transfer Objects)
│   └── ...                     # 【分工】：接收前端 RequestBody，配合 JSR-380 Validation 注解做格式校验
│
├── vo/                         # 7. 出参视图层 (View Objects)
│   └── ...                     # 【分工】：控制返回给前端的 JSON 结构，敏感数据过滤与格式化
│
├── config/                     # 8. 系统配置层 (Configurations)
│   ├── JwtInterceptor.java     # JWT 身份拦截校验器
│   └── WebConfig.java          # 全局 MVC 跨域、静态资源映射配置
│
├── docs/                       # 9. 文档管理目录
│   └── DATABASE_CONNECTION_GUIDE.md # 数据库连接与配置指南
│
└── public/                     # 10. 静态资源根目录 (如物理上传的短视频、封面图暂存)
```

---

## 🛠️ 技术栈清单

*   **核心语言**：Java 21 (使用最新 LTS 特性，如虚拟线程、模式匹配)
*   **基础框架**：Spring Boot 3.4.0 (自动配置、约定优于配置)
*   **持久化层**：Spring Data JPA (Hibernate 6)
*   **数据库**：PostgreSQL (关系型数据库，存储用户、视频及社交图谱关系)
*   **安全认证**：JWT (JSON Web Token) 无状态身份认证机制

---

## 🗄️ 数据库说明 (PostgreSQL)

本项目使用 **远程 PostgreSQL** 数据库进行海量视频与社交数据存储。
*   数据库配置信息请参阅：`docs/DATABASE_CONNECTION_GUIDE.md`
*   **注意**：在开发和运行前，请确保您的本地或测试环境网络能够正常连通远程 PostgreSQL 数据库，并在 `src/main/resources/application.properties` 中正确配置连接凭证。

---

## 🚦 常用启动与自测命令

在提交代码或启动服务前，请在项目根目录下执行以下命令进行自测与启动：

### 1. 执行单元测试与代码编译
确保所有业务代码无编译异常且通过全部测试用例：
```powershell
mvn clean test
```

### 2. 启动 Spring Boot 本地服务
启动后，主应用程序将通过内嵌 Tomcat 容器部署：
```powershell
mvn spring-boot:run
```

---

## 🔗 接口访问地址

*   **默认本地服务地址**：[http://localhost:8080](http://localhost:8080)
*   所有 RESTful API 路由统一前缀为：`/api`（如用户登录接口为 `/api/login`）。
*   非公开接口（除登录、注册外）均被 JWT 拦截器保护，需在 HTTP 请求 Header 中携带 `Authorization: Bearer <Your_Token>`。