# API 文档

## 用户注册

- 接口说明：创建新用户账号，注册成功后返回 JWT Token 和用户基础信息。
- 请求方法：`POST`
- 请求路径：`/api/v1/auth/register`
- 是否需要登录：`否`

## 请求参数

### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| - | - | - | 无 |

### Query 参数

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| - | - | - | - | 无 |

### Body 参数

```json
{
  "username": "test_user",
  "password": "123456"
}
```

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | `String` | 是 | 登录账号，不能为空 |
| `password` | `String` | 是 | 登录密码，不能为空 |

## 请求示例

```bash
curl -X POST "http://165.232.172.99:8080/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"test_user","password":"123456"}'
```

## 响应示例

### 成功响应 `201 Created`

```json
{
  "success": true,
  "message": "Registration successful!",
  "token": "<jwt-token>",
  "user": {
    "id": 1,
    "username": "test_user"
  }
}
```

### 失败响应 `400 Bad Request`

```json
{
  "success": false,
  "message": "Username and password are required."
}
```

```json
{
  "success": false,
  "message": "Username already taken."
}
```

## 错误码

| 状态码 | 说明 |
| --- | --- |
| `400` | 用户名或密码为空；用户名已被占用 |
| `500` | 服务器内部错误 |

## 备注

- 注册成功后，后端会使用 BCrypt 存储密码哈希，不保存明文密码。
- 注册成功后会直接返回 JWT Token，前端可保存后用于需要登录的接口。

---

## 用户登录

- 接口说明：校验用户名和密码，登录成功后返回 JWT Token 和用户基础信息。
- 请求方法：`POST`
- 请求路径：`/api/v1/auth/login`
- 是否需要登录：`否`

## 请求参数

### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| - | - | - | 无 |

### Query 参数

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| - | - | - | - | 无 |

### Body 参数

```json
{
  "username": "test_user",
  "password": "123456"
}
```

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | `String` | 是 | 登录账号，不能为空 |
| `password` | `String` | 是 | 登录密码，不能为空 |

## 请求示例

```bash
curl -X POST "http://165.232.172.99:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"test_user","password":"123456"}'
```

## 响应示例

### 成功响应 `200 OK`

```json
{
  "success": true,
  "message": "Login successful!",
  "token": "<jwt-token>",
  "user": {
    "id": 1,
    "username": "test_user"
  }
}
```

### 失败响应 `400 Bad Request`

```json
{
  "success": false,
  "message": "Username and password are required."
}
```

```json
{
  "success": false,
  "message": "Invalid username or password."
}
```

## 错误码

| 状态码 | 说明 |
| --- | --- |
| `400` | 用户名或密码为空；用户名或密码错误 |
| `500` | 服务器内部错误 |

## 备注

- 登录成功后返回的 `token` 用于后续需要认证的接口。
- 后续请求携带方式：`Authorization: Bearer <token>`。

---

## 获取推荐视频列表（F02）

- 接口说明：按点赞数倒序返回当前用户未观看过的视频列表，用于首页推荐流展示。
- 请求方法：`GET`
- 请求路径：`/api/v1/videos/recommendations`
- 是否需要登录：`是`


### Path / Query / Body 参数

无。

### 请求示例

```bash
curl -X GET "http://localhost:8080/api/v1/videos/recommendations" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "videos": [
    {
      "id": 2,
      "user_id": 1,
      "title": "DCloud 移动端跨平台技术",
      "description": "uni-app 跨平台开发核心技术讲解",
      "video_url": "https://qiniu-web-assets.dcloud.net.cn/unidoc/zh/uni-app-video-courses.mp4",
      "cover_url": "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800",
      "likes_count": 580,
      "likeCount": 580,
      "liked": false,
      "is_liked": 0,
      "creator_name": "douyin_creator",
      "created_at": "2026-01-01T10:00:00"
    }
  ],
  "allViewed": false,
  "totalCount": 6
}
```

### 全部看完 `200 OK`

```json
{
  "success": true,
  "videos": [],
  "allViewed": true,
  "totalCount": 6
}
```

### 失败响应

`401 Unauthorized`

```json
{
  "success": false,
  "message": "Access Denied: Missing or malformed Authorization header."
}
```

`500 Internal Server Error`

```json
{
  "success": false,
  "message": "Error compiling recommendations: <detail>"
}
```

### 响应字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `success` | boolean | 是 | 是否成功 |
| `videos` | array | 是 | 推荐视频列表，按 `likes_count` 降序，已观看视频被排除；可为空数组 |
| `allViewed` | boolean | 是 | `videos` 为空时为 `true`，表示当前用户已看完所有可推荐视频 |
| `totalCount` | number | 是 | 数据库中视频总数（**含**已观看） |
| `message` | string | 否 | 失败时错误说明 |

### `videos[]` 单项字段

| 字段 | 类型 | 说明 | 前端用途 |
| --- | --- | --- | --- |
| `id` | number | 视频主键 | 播放、上报观看 |
| `user_id` | number | 发布者用户 ID | 判断是否本人发布 |
| `title` | string | 标题 | F01 播放区标题 |
| `description` | string | 描述 | 可选副文案 |
| `video_url` | string | 视频 URL（绝对路径或 `/uploads/...`） | `<video src>` |
| `cover_url` | string | 封面 URL | 封面图 |
| `likes_count` | number | 点赞总数（兼容字段，同 `likeCount`） | F01 / F04 展示 |
| `likeCount` | number | **规范字段**：点赞总数 | F01 / F04 展示 |
| `liked` | boolean | **规范字段**：当前用户是否已赞 | F04 点赞按钮状态 |
| `is_liked` | `0` \| `1` | 兼容字段，`liked` 的整型别名 | F04 |
| `created_at` | string (ISO-8601) | 创建时间 | 可选 |
| `creator_name` | string | 发布者用户名 | F01 作者展示 |

### 错误码

| 状态码 | 说明 |
| --- | --- |
| `200` | 查询成功 |
| `401` | 未登录或 Token 无效 |
| `500` | 服务端查询异常 |

### 业务规则

- 排除当前用户在 `views` 表中已有记录的视频。
- 按 `likes_count` **降序**排列。
- 每条视频包含 `liked` / `likeCount`（与组员 B 的 F04 字段约定），供点赞状态回显。

### F03 与推荐接口的关系

F03（上下切换视频）**没有独立的 REST 接口**。切换时前后端只约定以下接口协作：

| 时机 | 接口 | 说明 |
| --- | --- | --- |
| 进入推荐流 / 刷新列表 | `GET /api/v1/videos/recommendations` | 一次性获取未看视频列表，切换本身不重复调用 |
| 切换到新视频并开始播放后 | `POST /api/v1/videos/{id}/views` | 上报当前视频 ID，用于观看去重 |
| 重置后重新推荐 | `DELETE /api/v1/users/me/views` → `GET /api/v1/videos/recommendations` | 清空历史后重新拉列表 |

不提供 `GET /feed/next`、`GET /feed/prev` 等切换专用路径。

---

## 标记视频已观看（F02）

- 接口说明：记录当前用户对指定视频的观看历史，已观看视频不再出现在推荐流中。同一用户对同一视频重复调用具有幂等性。
- 请求方法：`POST`
- 请求路径：`/api/v1/videos/{id}/views`
- 是否需要登录：`是`


### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 是 | 视频 ID |

### 请求示例

```bash
curl -X POST "http://localhost:8080/api/v1/videos/2/views" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "message": "Video marked as viewed."
}
```

### 失败响应

`401 Unauthorized`

```json
{
  "success": false,
  "message": "Access Denied: Missing or malformed Authorization header."
}
```

`404 Not Found`

```json
{
  "success": false,
  "message": "Video not found."
}
```

`500 Internal Server Error`

```json
{
  "success": false,
  "message": "Error marking video as viewed: <detail>"
}
```

### 幂等性

- 同一用户对同一视频重复调用 **不会重复插入** 观看记录。
- 应用层先通过 `existsByUserIdAndVideoId` 判断；数据库 `views` 表有 `(user_id, video_id)` 唯一约束兜底。
- 已存在记录时仍返回 `200 OK` 和 `"Video marked as viewed."`。

### 触发时机

- 用户在推荐流中切换到新视频并开始播放后调用；登录后首次拉列表 **不** 调用。
- 调用成功后，该视频在下次 `GET recommendations` 时不再返回；当前已拉取的列表不会自动剔除该条目。

### 错误码

| 状态码 | 说明 |
| --- | --- |
| `200` | 标记成功（含幂等重复调用） |
| `401` | 未登录或 Token 无效 |
| `404` | 视频不存在 |
| `500` | 服务端写入异常 |

---

## 重置观看记录（F02）

- 接口说明：清空当前登录用户的全部观看历史，使所有视频重新进入推荐池。用于调试或「全部看完」后的重置入口。
- 请求方法：`DELETE`
- 请求路径：`/api/v1/users/me/views`
- 是否需要登录：`是`


### 请求示例

```bash
curl -X DELETE "http://localhost:8080/api/v1/users/me/views" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "message": "Your watch history has been reset. All videos can be recommended again!"
}
```

### 失败响应

`401 Unauthorized`

```json
{
  "success": false,
  "message": "Access Denied: Missing or malformed Authorization header."
}
```

`500 Internal Server Error`

```json
{
  "success": false,
  "message": "Error resetting watch history: <detail>"
}
```

### 异常场景

| 场景 | 预期 |
| --- | --- |
| 未登录调用 | 返回 `401` |
| 重置后重新拉推荐 | `GET recommendations` 恢复完整列表 |
| 观看后去重 | 已 `POST views` 的视频不再出现在 `videos` 中 |
| 全部看完空态 | 前端展示「推荐已看完」，引导用户点击重置 |

### 错误码

| 状态码 | 说明 |
| --- | --- |
| `200` | 重置成功 |
| `401` | 未登录或 Token 无效 |
| `500` | 服务端删除异常 |

### 备注

- 仅删除**当前登录用户**在 `views` 表中的记录，不影响其他用户。
- 重置后推荐流按点赞数重新排序返回。

---

## 视频点赞 / 取消点赞（F04）

- 接口说明：对指定视频切换点赞状态。若当前用户未点赞则创建点赞记录并 `likeCount + 1`；若已点赞则取消点赞并 `likeCount - 1`。同一用户对同一视频不会重复写入点赞记录。
- 请求方法：`PUT`
- 请求路径：`/api/v1/videos/{id}/like`
- 是否需要登录：`是`

### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 是 | 视频 ID |

### Query / Body 参数

无。

### 请求示例

```bash
curl -X PUT "http://localhost:8080/api/v1/videos/12/like" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

点赞成功：

```json
{
  "success": true,
  "message": "Video liked!",
  "liked": true,
  "likeCount": 581,
  "likes_count": 581
}
```

取消点赞：

```json
{
  "success": true,
  "message": "Video unliked.",
  "liked": false,
  "likeCount": 580,
  "likes_count": 580
}
```

### 失败响应

`404 Not Found`

```json
{
  "success": false,
  "message": "Video not found."
}
```

`401 Unauthorized`

```json
{
  "success": false,
  "message": "Access Denied: Missing or malformed Authorization header."
}
```

### 字段约定（共享）

| 字段 | 类型 | 出现位置 | 说明 |
| --- | --- | --- | --- |
| `liked` | `boolean` | 推荐流、点赞切换响应 | 当前登录用户是否已点赞该视频 |
| `likeCount` | `integer` | 推荐流、点赞切换响应 | 视频当前总点赞数 |
| `is_liked` | `0/1` | 推荐流（兼容字段） | `liked` 的整型别名 |
| `likes_count` | `integer` | 点赞切换响应（兼容字段） | `likeCount` 的 snake_case 别名 |

### 异常场景说明

| 场景 | 行为 |
| --- | --- |
| 未登录点赞 | 返回 `401`，前端提示重新登录 |
| 重复快速点击 | 前端请求进行中禁用按钮；后端 `(user_id, video_id)` 唯一约束防止重复写入 |
| 并发重复点赞 | 捕获唯一约束冲突，返回 `liked=true` 与当前 `likeCount`，不重复加 1 |
| 刷新后状态回显 | 重新请求 `GET /api/v1/videos/recommendations`，读取 `liked` 与 `likeCount` |

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 点赞或取消点赞成功；并发重复点赞时幂等返回 `liked=true` 与当前 `likeCount` |
| `401` | 未登录、Token 缺失/格式错误、Token 无效或过期 |
| `404` | 视频 ID 不存在 |
| `500` | 服务端点赞切换异常（如数据库写入失败） |

### 推荐流中的点赞状态回显

`GET /api/v1/videos/recommendations` 每条视频包含：

```json
{
  "id": 12,
  "title": "示例视频",
  "creator_name": "yunqi",
  "liked": true,
  "likeCount": 581,
  "is_liked": 1,
  "likes_count": 581
}
```

---

## 查看点赞通知（F14）

- 接口说明：分页查询「他人对我发布视频的点赞」通知列表，并返回未读数量。只包含其他用户对我名下视频的点赞，不包含自己给自己点赞。已读状态基于用户上次调用「标记已读」接口的时间戳计算。
- 请求方法：`GET`
- 请求路径：`/api/v1/users/me/like-notifications`
- 是否需要登录：`是`

### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| - | - | - | 无 |

### Query 参数

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | `Integer` | 否 | `1` | 页码，从 1 开始 |
| `limit` | `Integer` | 否 | `10` | 每页条数，最大 50 |

### Body 参数

无。

### 请求示例

```bash
curl -X GET "http://localhost:8080/api/v1/users/me/like-notifications?page=1&limit=10" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "notifications": [
    {
      "likeId": 11,
      "likerUsername": "fan_user",
      "likerDisplayName": "Fan User",
      "videoId": 9,
      "videoTitle": "我的作品",
      "likedAt": "2026-05-22T12:00:00",
      "read": false
    }
  ],
  "unreadCount": 1,
  "pagination": {
    "page": 1,
    "limit": 10,
    "total": 1,
    "totalPages": 1
  }
}
```

### 失败响应

`401 Unauthorized`

```json
{
  "success": false,
  "message": "Access Denied: Missing or malformed Authorization header."
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `notifications` | `Array` | 点赞通知列表，按点赞时间倒序 |
| `notifications[].likeId` | `Long` | 点赞记录 ID |
| `notifications[].likerUsername` | `String` | 点赞用户账号 |
| `notifications[].likerDisplayName` | `String` | 点赞用户展示名 |
| `notifications[].videoId` | `Long` | 被点赞视频 ID |
| `notifications[].videoTitle` | `String` | 被点赞视频标题 |
| `notifications[].likedAt` | `String` | 点赞时间（ISO-8601） |
| `notifications[].read` | `Boolean` | 当前用户是否已读该条通知 |
| `unreadCount` | `Integer` | 未读通知总数 |
| `pagination.page` | `Integer` | 当前页码 |
| `pagination.limit` | `Integer` | 每页条数 |
| `pagination.total` | `Long` | 通知总条数 |
| `pagination.totalPages` | `Integer` | 总页数 |

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 查询成功；无他人点赞时返回空数组 `notifications: []`，`unreadCount` 为 `0` |
| `401` | 未登录、Token 缺失/格式错误、Token 无效或过期；用户上下文缺失或会话无效 |
| `500` | 服务端查询异常（如数据库缺少 `users.last_like_notification_read_at` 字段） |

### 异常场景说明

| 场景 | 行为 |
| --- | --- |
| 未登录访问 | 返回 `401`，前端提示重新登录 |
| 暂无他人点赞 | 返回 `200`，`notifications` 为空列表 |
| 自己给自己点赞 | 不出现在通知列表中 |
| 分页参数非法 | `page` 小于 1 时按第 1 页处理；`limit` 小于 1 按 1、大于 50 按 50 处理 |

---

## 标记点赞通知已读（F14）

- 接口说明：将当前用户的点赞通知全部标记为已读，更新 `lastLikeNotificationReadAt` 时间戳。前端通常在打开通知面板时调用。
- 请求方法：`PUT`
- 请求路径：`/api/v1/users/me/like-notifications/read`
- 是否需要登录：`是`

### Path / Query / Body 参数

无。

### 请求示例

```bash
curl -X PUT "http://localhost:8080/api/v1/users/me/like-notifications/read" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "message": "Like notifications marked as read.",
  "unreadCount": 0
}
```

### 失败响应

`401 Unauthorized`

```json
{
  "success": false,
  "message": "Access Denied: Missing or malformed Authorization header."
}
```

`401 Unauthorized - Token 无效或过期`

```json
{
  "success": false,
  "message": "Access Denied: Invalid or expired token."
}
```

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 标记已读成功，`unreadCount` 返回 `0` |
| `401` | 未登录、Token 缺失/格式错误、Token 无效或过期；用户上下文缺失或会话无效 |
| `500` | 服务端更新已读时间戳失败（如数据库缺少 `users.last_like_notification_read_at` 字段） |

---

## 查看视频评论（F15）

- 接口说明：分页查询指定视频的顶层评论列表（cursor 分页），并返回评论总数。评论按创建时间倒序。
- 请求方法：`GET`
- 请求路径：`/api/v1/videos/{id}/comments`
- 是否需要登录：`是`

### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 是 | 视频 ID |

### Query 参数

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `cursor` | `String` | 否 | - | 上一页返回的 `pagination.nextCursor` |
| `limit` | `Integer` | 否 | `20` | 每页条数，最大 50 |

### 成功响应 `200 OK`

```json
{
  "success": true,
  "comments": [
    {
      "id": 4,
      "videoId": 128,
      "userId": 6,
      "username": "zyq",
      "displayName": "zyq",
      "avatarUrl": "/uploads/avatars/zyq.png",
      "content": "这条视频真不错",
      "createdAt": "2026-06-15T12:06:41.350355"
    }
  ],
  "totalCount": 12,
  "pagination": {
    "limit": 20,
    "hasMore": false,
    "nextCursor": null
  }
}
```

### 失败响应

| 状态码 | 说明 |
| --- | --- |
| `401` | 未登录 |
| `404` | 视频不存在 |

---

## 发表评论（F15）

- 接口说明：当前登录用户对指定视频发表文字评论，内容不能为空且不超过 500 字。
- 请求方法：`POST`
- 请求路径：`/api/v1/videos/{id}/comments`
- 是否需要登录：`是`

### Body 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `content` | `String` | 是 | 评论内容，1–500 字 |

### 请求示例

```bash
curl -X POST "http://localhost:8080/api/v1/videos/128/comments" \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d "{\"content\":\"评论内容示例\"}"
```

### 成功响应 `201 Created`

```json
{
  "success": true,
  "message": "Comment posted.",
  "comment": {
    "id": 4,
    "videoId": 128,
    "userId": 6,
    "username": "zyq",
    "content": "评论内容示例",
    "createdAt": "2026-06-15T12:06:41.350355"
  },
  "commentsCount": 13
}
```

---

## 查看用户主页（F16）

- 接口说明：查询指定用户的公开主页资料，含获赞总数、作品数、关注/粉丝/朋友数量等。
- 请求方法：`GET`
- 请求路径：`/api/v1/users/{id}`
- 是否需要登录：`是`

### 成功响应 `200 OK`

```json
{
  "success": true,
  "user": {
    "id": 6,
    "username": "zyq",
    "displayName": "zyq",
    "avatarUrl": "/uploads/avatars/zyq.png",
    "totalLikesReceived": 120,
    "publishedVideoCount": 8,
    "followingCount": 5,
    "followerCount": 12,
    "friendCount": 3
  }
}
```

---

## 查看用户发布的视频（F16）

- 请求方法：`GET`
- 请求路径：`/api/v1/users/{id}/videos`
- Query：`cursor`、`limit`（默认 8，最大 50）
- 说明：cursor 分页返回该用户发布的视频，字段与推荐流视频项一致（含 `liked`、`likeCount`、`comments_count`）。

---

## 查看用户点赞的视频（F16）

- 请求方法：`GET`
- 请求路径：`/api/v1/users/{id}/liked-videos`
- Query：`cursor`、`limit`（默认 8，最大 50）
- 说明：cursor 分页返回该用户点赞过的视频，按点赞时间倒序。

### 前端交互说明（F16）

- 推荐流点击侧栏头像或 `@创作者` 进入 `/users/{id}` 对应的主页视图。
- 主页 Tab 可切换「发布的视频」「点赞的视频」，支持「加载更多」。
- 点击视频卡片可回到播放流播放该视频。

---

## 删除我的视频（F07)

- 接口说明：删除当前登录用户自己发布的视频。只能删除自己的视频，删除他人视频会返回 403 权限错误。删除时会同时删除视频文件、封面文件以及相关的点赞记录和观看记录。
- 请求方法：`DELETE`
- 请求路径：`/api/v1/videos/{id}`
- 是否需要登录：`是`

### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 是 | 要删除的视频 ID |

### 请求示例

```bash
curl -X DELETE "http://165.232.172.99:8080/api/v1/videos/12" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "message": "Video deleted",
  "data": {}
}
```

### 失败响应

`404 Not Found - 视频不存在`

```json
{
  "success": false,
  "message": "Video not found",
  "data": {}
}
```

`403 Forbidden - 删除他人视频`

```json
{
  "success": false,
  "message": "Forbidden: you can only delete your own videos",
  "data": {}
}
```

`401 Unauthorized - 未登录`

```json
{
  "success": false,
  "message": "Access Denied: Missing or malformed Authorization header."
}
```

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 删除成功 |
| `401` | 未登录或 Token 无效 |
| `403` | 尝试删除他人视频，权限不足 |
| `404` | 视频不存在 |

### 备注

- 删除视频会级联删除：
    - 该视频的所有点赞记录
    - 该视频的所有观看记录
    - 本地的视频文件（`./public/uploads/videos/`）
    - 本地的封面文件（`./public/uploads/covers/`）
- 只能删除自己发布的视频，无法删除他人视频
- 删除操作在事务中执行，保证数据一致性

---

## 查询业务日志记录 (F11)

- 接口说明：获取系统所有 API 请求的详细日志记录，包含请求入参、响应出参、HTTP 状态码和接口响应耗时。用于监控系统运行状态和性能分析。
- 请求方法：`GET`
- 请求路径：`/api/v1/admin/request-logs`
- 是否需要登录：`是`

### Query 参数（可选）

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `limit` | `Integer` | 否 | `100` | 每页返回的日志条数 |
| `page` | `Integer` | 否 | `1` | 页码，从 1 开始 |

### 请求示例

```bash
# 获取最近 100 条日志（默认）
curl -X GET "http://165.232.172.99:8080/api/v1/admin/request-logs" \
  -H "Authorization: Bearer <jwt-token>"

# 分页获取，每页 20 条，第 2 页
curl -X GET "http://165.232.172.99:8080/api/v1/admin/request-logs?limit=20&page=2" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "logs": [
    {
      "timestamp": "2026-06-06 18:05:47",
      "method": "GET",
      "url": "/api/v1/videos/recommendations",
      "statusCode": 200,
      "durationMs": 8386,
      "requestBody": "",
      "responseBody": "{\"success\":true,\"videos\":[...]}"
    },
    {
      "timestamp": "2026-06-06 18:05:38",
      "method": "POST",
      "url": "/api/v1/auth/login",
      "statusCode": 200,
      "durationMs": 1262,
      "requestBody": "{\"username\":\"ainsley\",\"password\":\"******\"}",
      "responseBody": "{\"success\":true,\"message\":\"Login successful!\",\"user\":{\"id\":4,\"username\":\"ainsley\"},\"token\":\"******\"}"
    }
  ],
  "total": 156,
  "totalPages": 2,
  "currentPage": 1
}
```

### 日志字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `timestamp` | `String` | 请求时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `method` | `String` | HTTP 方法（GET、POST、PUT、DELETE） |
| `url` | `String` | 请求路径 |
| `statusCode` | `Integer` | HTTP 响应状态码 |
| `durationMs` | `Long` | 接口处理耗时（毫秒） |
| `requestBody` | `String` | 请求体内容（敏感信息已脱敏） |
| `responseBody` | `String` | 响应体内容 |

### 安全说明

日志中的敏感信息已自动脱敏：
- `password` 字段替换为 `******`
- `token` 字段替换为 `******`

---

## 系统监控统计数据 F(12)

- 接口说明：获取系统整体的监控统计数据，包括用户数、视频数、点赞总数、观看总数、平均 API 响应耗时、总请求数等。
- 请求方法：`GET`
- 请求路径：`/api/v1/admin/stats`
- 是否需要登录：`是`

### 请求示例

```bash
curl -X GET "http://165.232.172.99:8080/api/v1/admin/stats" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "stats": {
    "users": 6,
    "videos": 11,
    "likes": 5,
    "views": 8,
    "averageResponseTimeMs": 3218.67,
    "totalRequestsLogged": 3
  }
}
```

### 统计字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `users` | `Long` | 系统注册用户总数 |
| `videos` | `Long` | 视频池中的视频总数 |
| `likes` | `Long` | 全站点赞总数 |
| `views` | `Long` | 全站观看记录总数 |
| `averageResponseTimeMs` | `Double` | 所有 API 的平均响应耗时（毫秒） |
| `totalRequestsLogged` | `Long` | 已记录的请求日志总数 |

---
## 备注

1. 日志存储：所有请求日志持久化存储到 H2 数据库的 `request_log` 表中，应用重启后日志不丢失
2. 日志过滤：以下请求不会被记录：
    - `/h2-console` 相关请求
    - `/api/v1/admin/request-logs` 
    - `/api/v1/admin/stats` 
    - OPTIONS 预检请求
3. 慢接口告警：耗时超过 500ms 的接口会在控制台输出警告日志
4. 分页支持：日志接口支持分页查询，避免一次返回过多数据
5. 敏感信息脱敏：日志中的密码和 Token 已自动脱敏，不会泄露
6. 100条为日志显示的最大显示

---

## 发布视频（F05 / F10）

- 接口说明：当前登录用户上传视频文件（必填）、可选的封面图片，以及标题和描述信息，创建一条新的视频记录。上传成功后视频文件持久化存储，封面可自定义上传或由后端自动生成。发布成功后视频进入推荐池，可被其他用户观看和互动。
- 请求方法：`POST`
- 请求路径：`/api/v1/videos`
- 是否需要登录：`是`
- Content-Type：`multipart/form-data`

### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| - | - | - | 无 |

### Query 参数

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| - | - | - | - | 无 |

### Body 参数（multipart/form-data）

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | `String` | 是 | 视频标题，不能为空 |
| `description` | `String` | 否 | 视频描述，默认为空字符串 |
| `video` | `File` | 是 | 视频文件（multipart），不能为空 |
| `cover` | `File` | 否 | 封面图片（multipart）；未提供时后端自动生成渐变封面 |

### 请求示例

```bash
# 上传视频 + 自定义封面
curl -X POST "http://localhost:8080/api/v1/videos" \
  -H "Authorization: Bearer <jwt-token>" \
  -F "title=我的第一条短视频" \
  -F "description=这是视频描述 #生活 #日常" \
  -F "video=@/path/to/my_video.mp4" \
  -F "cover=@/path/to/cover.jpg"

# 仅上传视频（后端自动生成封面）
curl -X POST "http://localhost:8080/api/v1/videos" \
  -H "Authorization: Bearer <jwt-token>" \
  -F "title=无封面视频" \
  -F "video=@/path/to/my_video.mp4"
```

### 成功响应 `201 Created`

```json
{
  "success": true,
  "message": "Video published successfully!",
  "data": {
    "id": 15,
    "user_id": 5,
    "title": "我的第一条短视频",
    "description": "这是视频描述 #生活 #日常",
    "video_url": "/uploads/videos/video-1765960000000-123456789.mp4",
    "cover_url": "/uploads/covers/cover-1765960000000-123456789.jpg",
    "views_count": 0,
    "comments_count": 0,
    "status": "published",
    "created_at": "2026-06-17T12:00:00",
    "creator_name": "test_user",
    "avatarUrl": "/uploads/avatars/default.png",
    "liked": false,
    "likeCount": 0,
    "is_liked": 0,
    "likes_count": 0,
    "favorited": false,
    "favoriteCount": 0,
    "is_favorited": 0,
    "favorites_count": 0
  }
}
```

### 失败响应

`400 Bad Request - 缺少标题`

```json
{
  "success": false,
  "message": "Video title is required."
}
```

`400 Bad Request - 缺少视频文件`

```json
{
  "success": false,
  "message": "Video file is required."
}
```

`401 Unauthorized - 未登录`

```json
{
  "success": false,
  "message": "Access Denied: Missing or malformed Authorization header."
}
```

`401 Unauthorized - 用户会话无效`

```json
{
  "success": false,
  "message": "User session invalid."
}
```

`500 Internal Server Error - 文件存储失败`

```json
{
  "success": false,
  "message": "File upload failed: <detail>"
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.id` | `Long` | 新创建的视频 ID |
| `data.user_id` | `Long` | 发布者用户 ID |
| `data.title` | `String` | 视频标题 |
| `data.description` | `String` | 视频描述 |
| `data.video_url` | `String` | 视频文件访问 URL（本地相对路径或远程 CDN 绝对路径） |
| `data.cover_url` | `String` | 封面图片访问 URL |
| `data.views_count` | `Integer` | 观看次数 |
| `data.comments_count` | `Long` | 评论总数 |
| `data.status` | `String` | 视频状态，固定为 `"published"` |
| `data.created_at` | `String` | 创建时间（ISO-8601） |
| `data.creator_name` | `String` | 发布者用户名 |
| `data.avatarUrl` | `String` | 发布者头像 URL |
| `data.liked` | `Boolean` | 当前用户是否已点赞（新建视频为 `false`） |
| `data.likeCount` | `Integer` | 点赞总数（新建视频为 `0`） |
| `data.is_liked` | `0/1` | `liked` 的整型别名 |
| `data.likes_count` | `Integer` | `likeCount` 的 snake_case 别名 |

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `201` | 发布成功，视频已落库并可被推荐 |
| `400` | 标题为空或视频文件缺失 |
| `401` | 未登录、Token 无效或用户不存在 |
| `500` | 文件存储失败（磁盘空间不足、远程 SFTP 不可达等） |

---

## 文件存储说明（F10）

### 存储路径

视频文件和封面文件统一存储在项目运行目录下的 `public/uploads/` 目录：

```
{项目根目录}/public/uploads/
├── videos/
│   ├── video-1765960000000-123456789.mp4
│   └── video-1765960001234-987654321.mp4
└── covers/
    ├── cover-1765960000000-123456789.jpg
    └── cover-1765960001234-987654321.jpg
```

- 配置项：`app.media.local-upload-dir`（默认值 `public/uploads`）
- 可通过 `application.properties` 修改存储根目录

### 文件命名规则

| 文件类型 | 命名格式 | 示例 |
| --- | --- | --- |
| 视频文件 | `video-{timestamp}-{random}.{ext}` | `video-1765960000000-123456789.mp4` |
| 自定义封面 | `cover-{timestamp}-{random}.{ext}` | `cover-1765960000000-123456789.jpg` |
| 自动生成封面 | `cover-{timestamp}-{random}.jpg` | `cover-1765960000000-123456789.jpg` |

- `{timestamp}`：`System.currentTimeMillis()`（毫秒级时间戳）
- `{random}`：`Math.round(Math.random() * 1e9)`（0–10 亿随机整数）
- `{ext}`：原始文件扩展名（小写），无扩展名时默认 `.mp4`（视频）或 `.jpg`（封面）

### 文件访问 URL

| 部署模式 | URL 格式 | 说明 |
| --- | --- | --- |
| 本地存储 | `/uploads/{folder}/{filename}` | 由 `UploadMediaController` 提供静态资源服务 |
| 远程 SFTP | `{publicBaseUrl}/uploads/{folder}/{filename}` | 通过 SFTP 上传到远程服务器，由 CDN/网关对外提供访问 |

- 本地模式下，视频通过 `GET /uploads/videos/{filename}` 访问
- 远程模式下，需配置 `app.media.remote-upload.enabled=true` 及对应的 SFTP 连接参数

### 封面自动生成

当发布视频时未提供 `cover` 文件，后端自动生成封面：

- 尺寸：600 × 800 像素
- 背景：渐变色（深蓝绿 → 玫红）
- 文字：视频标题（居中，最多显示 18 个字符）
- 格式：JPEG

### 静态资源访问

`UploadMediaController` 映射 `/uploads/**` 路径，将请求路径解析到本地 `public/uploads/` 对应文件：

```bash
# 访问本地存储的视频文件
curl -X GET "http://localhost:8080/uploads/videos/video-1765960000000-123456789.mp4"

# 访问本地存储的封面文件
curl -X GET "http://localhost:8080/uploads/covers/cover-1765960000000-123456789.jpg"
```

响应头包含 `Cache-Control: public, max-age=3600`，允许浏览器缓存 1 小时。

### 文件删除

删除视频时会同步清理对应的本地文件和远程文件（SFTP），删除操作为 best-effort：即使文件删除失败，数据库记录也会正常清除，不会回滚事务。

---

## 查看我的视频（F06）

- 接口说明：分页查询当前登录用户发布的视频列表，按创建时间倒序排列。每条视频包含点赞状态、收藏状态、评论数等完整字段，与推荐流视频项字段一致。
- 请求方法：`GET`
- 请求路径：`/api/v1/users/me/videos`
- 是否需要登录：`是`

### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| - | - | - | 无 |

### Query 参数

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `cursor` | `String` | 否 | - | 上一页返回的 `pagination.nextCursor`，首次请求不传 |
| `limit` | `Integer` | 否 | `8` | 每页条数，范围 1–50 |

### Body 参数

无。

### 请求示例

```bash
# 首次请求（第一页）
curl -X GET "http://localhost:8080/api/v1/users/me/videos?limit=8" \
  -H "Authorization: Bearer <jwt-token>"

# 加载下一页
curl -X GET "http://localhost:8080/api/v1/users/me/videos?cursor=MjAyNi0wNi0xN1QxMjowMDowMHwxNQ&limit=8" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "videos": [
    {
      "id": 15,
      "user_id": 5,
      "title": "我的第一条短视频",
      "description": "这是视频描述 #生活 #日常",
      "video_url": "/uploads/videos/video-1765960000000-123456789.mp4",
      "cover_url": "/uploads/covers/cover-1765960000000-123456789.jpg",
      "views_count": 0,
      "comments_count": 3,
      "status": "published",
      "created_at": "2026-06-17T12:00:00",
      "creator_name": "test_user",
      "avatarUrl": "/uploads/avatars/default.png",
      "liked": false,
      "likeCount": 5,
      "is_liked": 0,
      "likes_count": 5,
      "favorited": true,
      "favoriteCount": 2,
      "is_favorited": 1,
      "favorites_count": 2
    },
    {
      "id": 12,
      "user_id": 5,
      "title": "另一条视频",
      "description": "",
      "video_url": "/uploads/videos/video-1765950000000-987654321.mp4",
      "cover_url": "/uploads/covers/cover-1765950000000-987654321.jpg",
      "views_count": 0,
      "comments_count": 1,
      "status": "published",
      "created_at": "2026-06-16T10:00:00",
      "creator_name": "test_user",
      "avatarUrl": "/uploads/avatars/default.png",
      "liked": true,
      "likeCount": 12,
      "is_liked": 1,
      "likes_count": 12,
      "favorited": false,
      "favoriteCount": 0,
      "is_favorited": 0,
      "favorites_count": 0
    }
  ],
  "pagination": {
    "limit": 8,
    "hasMore": false,
    "nextCursor": null
  }
}
```

### 空列表 `200 OK`

```json
{
  "success": true,
  "videos": [],
  "pagination": {
    "limit": 8,
    "hasMore": false,
    "nextCursor": null
  }
}
```

### 失败响应

`401 Unauthorized`

```json
{
  "success": false,
  "message": "Access Denied: Missing or malformed Authorization header."
}
```

`500 Internal Server Error`

```json
{
  "success": false,
  "message": "Error loading your videos: <detail>"
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `videos` | `Array` | 当前用户发布的视频列表，按创建时间倒序；可为空数组 |
| `videos[].id` | `Long` | 视频 ID |
| `videos[].user_id` | `Long` | 发布者用户 ID（始终等于当前用户） |
| `videos[].title` | `String` | 视频标题 |
| `videos[].description` | `String` | 视频描述 |
| `videos[].video_url` | `String` | 视频播放 URL |
| `videos[].cover_url` | `String` | 封面图片 URL |
| `videos[].views_count` | `Integer` | 观看次数 |
| `videos[].comments_count` | `Long` | 评论总数 |
| `videos[].status` | `String` | 视频状态，固定为 `"published"` |
| `videos[].created_at` | `String` | 创建时间（ISO-8601） |
| `videos[].creator_name` | `String` | 发布者用户名 |
| `videos[].avatarUrl` | `String` | 发布者头像 URL |
| `videos[].liked` | `Boolean` | 当前用户是否已点赞 |
| `videos[].likeCount` | `Integer` | 点赞总数 |
| `videos[].is_liked` | `0/1` | `liked` 的整型别名 |
| `videos[].likes_count` | `Integer` | `likeCount` 的 snake_case 别名 |
| `videos[].favorited` | `Boolean` | 当前用户是否已收藏 |
| `videos[].favoriteCount` | `Long` | 收藏总数 |
| `videos[].is_favorited` | `0/1` | `favorited` 的整型别名 |
| `videos[].favorites_count` | `Long` | `favoriteCount` 的 snake_case 别名 |
| `pagination.limit` | `Integer` | 本次请求的每页条数 |
| `pagination.hasMore` | `Boolean` | 是否还有更多数据 |
| `pagination.nextCursor` | `String/null` | 下一页游标；`null` 表示已到最后一页 |

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 查询成功；无视频时返回空数组 `videos: []` |
| `401` | 未登录、Token 缺失/格式错误、Token 无效或过期 |
| `500` | 服务端查询异常 |

### 分页机制

- 采用 **cursor 游标分页**（非传统页码分页），避免深分页性能问题。
- 首次请求不传 `cursor`；后续请求传入上一页响应中的 `pagination.nextCursor`。
- 游标编码规则：Base64URL(`created_at|videoId`)，前端无需解析，原样传递即可。
- `limit` 默认 8，最大 50，超出范围自动修正。

### 缓存策略

- 接口使用 Spring Cache `@Cacheable`，缓存 key 基于 `userId + cursor + limit`。
- 发布新视频或删除视频时，通过 `@CacheEvict(value = "userVideos", allEntries = true)` 清除当前用户全部缓存。

### 业务规则

- 仅返回当前登录用户自己发布的视频，无法查看他人发布的视频列表。
- 视频列表不包含文件已损坏或不可播放的视频（`LocalMediaAvailability.isPlayableUrl` 过滤）。
- 每条视频的 `liked`、`favorited` 等状态基于当前登录用户计算。
- 视频项字段与推荐流（F02）一致，前端可复用同一视频卡片组件。



## 关注用户

- 接口说明：当前登录用户关注指定用户。若双方互相关注则自动成为好友。
- 请求方法：`POST`
- 请求路径：`/api/v1/users/{id}/follow`
- 是否需要登录：`是`

### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 是 | 目标用户 ID |

### 请求示例

```bash
curl -X POST "http://localhost:8080/api/v1/users/6/follow" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "message": "关注成功",
  "isFriend": false,
  "followingCount": 3,
  "friendCount": 1
}
```

互关成为好友时 `message` 为 `"关注成功，你们已成为好友！"`，`isFriend` 为 `true`。

### 失败响应

`400 Bad Request - 关注自己`

```json
{
  "success": false,
  "message": "不能关注自己"
}
```

`400 Bad Request - 已关注`

```json
{
  "success": false,
  "message": "已经关注该用户"
}
```

`404 Not Found`

```json
{
  "success": false,
  "message": "用户不存在"
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `isFriend` | `boolean` | 关注后是否与对方互关成为好友 |
| `followingCount` | `Long` | 当前用户关注总数 |
| `friendCount` | `Long` | 当前用户好友（互关）总数 |

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 关注成功 |
| `400` | 关注自己；或已关注该用户 |
| `401` | 未登录或 Token 无效 |
| `404` | 目标用户不存在 |

---

## 取消关注用户

- 接口说明：当前登录用户取消关注指定用户，好友关系同步解除。
- 请求方法：`DELETE`
- 请求路径：`/api/v1/users/{id}/follow`
- 是否需要登录：`是`

### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 是 | 目标用户 ID |

### 请求示例

```bash
curl -X DELETE "http://localhost:8080/api/v1/users/6/follow" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "message": "已取消关注",
  "followingCount": 2,
  "friendCount": 0
}
```

### 失败响应

`400 Bad Request`

```json
{
  "success": false,
  "message": "你还没有关注该用户"
}
```

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 取消关注成功 |
| `400` | 原本未关注该用户 |
| `401` | 未登录或 Token 无效 |

---

## 获取我关注的用户列表

- 接口说明：返回当前登录用户关注的所有用户，含是否互关标识，用于「关注」Tab 左侧用户列表展示。
- 请求方法：`GET`
- 请求路径：`/api/v1/users/me/following`
- 是否需要登录：`是`

### 请求示例

```bash
curl -X GET "http://localhost:8080/api/v1/users/me/following" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "users": [
    {
      "id": 6,
      "username": "creator_a",
      "displayName": "Creator A",
      "avatarUrl": null,
      "isFriend": true
    }
  ],
  "count": 1
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `users` | `Array` | 关注的用户列表；无关注时为空数组 |
| `users[].isFriend` | `boolean` | 该用户是否与我互关（好友） |
| `count` | `Integer` | 关注总数 |

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 查询成功 |
| `401` | 未登录或 Token 无效 |

---

## 获取我的粉丝列表

- 接口说明：返回关注了当前登录用户的所有用户，含是否互关标识。
- 请求方法：`GET`
- 请求路径：`/api/v1/users/me/followers`
- 是否需要登录：`是`

### 请求示例

```bash
curl -X GET "http://localhost:8080/api/v1/users/me/followers" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "users": [
    {
      "id": 7,
      "username": "fan_user",
      "displayName": "Fan User",
      "avatarUrl": null,
      "isFriend": false
    }
  ],
  "count": 1
}
```

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 查询成功 |
| `401` | 未登录或 Token 无效 |

---

## 获取我的朋友列表（互关用户）

- 接口说明：返回当前登录用户的互关好友列表，用于「朋友」Tab 左侧列表展示。
- 请求方法：`GET`
- 请求路径：`/api/v1/users/me/friends`
- 是否需要登录：`是`

### 请求示例

```bash
curl -X GET "http://localhost:8080/api/v1/users/me/friends" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "friends": [
    {
      "id": 6,
      "username": "creator_a",
      "displayName": "Creator A",
      "avatarUrl": null,
      "isFriend": true
    }
  ],
  "count": 1
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `friends` | `Array` | 互关好友列表；无好友时为空数组 |
| `friends[].isFriend` | `boolean` | 固定为 `true` |
| `count` | `Integer` | 好友总数 |

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 查询成功 |
| `401` | 未登录或 Token 无效 |

---

## 查询关注关系

- 接口说明：查询当前登录用户与指定用户之间的关注关系，用于推荐流侧栏关注按钮状态回显。
- 请求方法：`GET`
- 请求路径：`/api/v1/users/{id}/relation`
- 是否需要登录：`是`

### Path 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 是 | 目标用户 ID |

### 请求示例

```bash
curl -X GET "http://localhost:8080/api/v1/users/6/relation" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "isFollowing": true,
  "isFollowedBy": false,
  "isFriend": false,
  "followingCount": 3,
  "followerCount": 5,
  "friendCount": 1
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `isFollowing` | `boolean` | 当前用户是否已关注目标用户 |
| `isFollowedBy` | `boolean` | 目标用户是否关注了当前用户 |
| `isFriend` | `boolean` | 双方是否互关（`isFollowing && isFollowedBy`） |
| `followingCount` | `Long` | 当前用户关注总数 |
| `followerCount` | `Long` | 当前用户粉丝总数 |
| `friendCount` | `Long` | 当前用户好友（互关）总数 |

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 查询成功 |
| `401` | 未登录或 Token 无效 |

---

## 搜索视频与用户

- 接口说明：根据关键词搜索视频（标题、描述、标签、评论内容）和用户名，返回合并去重后的视频列表（标题匹配优先）和用户列表，用于顶部搜索框下拉结果和搜索结果页展示。
- 请求方法：`GET`
- 请求路径：`/api/v1/videos/search`
- 是否需要登录：`是`

### Query 参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `q` | `String` | 是 | 搜索关键词，不能为空 |

### 请求示例

```bash
curl -X GET "http://localhost:8080/api/v1/videos/search?q=游戏" \
  -H "Authorization: Bearer <jwt-token>"
```

### 成功响应 `200 OK`

```json
{
  "success": true,
  "keyword": "游戏",
  "videos": [
    {
      "id": 3,
      "title": "热门游戏攻略",
      "creator_name": "gamer_x",
      "cover_url": "/uploads/covers/cover-xxx.jpg",
      "video_url": "/uploads/videos/video-xxx.mp4",
      "likeCount": 320,
      "liked": false,
      "favorited": false,
      "favoriteCount": 5,
      "comments_count": 12
    }
  ],
  "users": [
    {
      "id": 7,
      "username": "gamer_x",
      "displayName": "GamerX",
      "avatarUrl": null
    }
  ]
}
```

### 失败响应

`400 Bad Request`

```json
{
  "success": false,
  "message": "搜索关键词不能为空"
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | `String` | 实际搜索的关键词（trim 后） |
| `videos` | `Array` | 匹配的视频列表，最多 20 条；标题/描述匹配优先，评论内容匹配次之；不可播放的视频自动过滤；字段与推荐流视频项一致 |
| `users` | `Array` | 匹配的用户列表，最多 20 条 |
| `users[].id` | `Long` | 用户 ID |
| `users[].username` | `String` | 用户名 |
| `users[].displayName` | `String` | 展示名 |
| `users[].avatarUrl` | `String` | 头像 URL |

### 业务规则

- 视频搜索同时检索标题、描述（含标签）和评论内容，结果合并去重，标题匹配排在前。
- 不可播放的视频 URL（本地文件不存在）会被自动过滤，不出现在结果中。
- 视频结果包含当前用户的点赞状态（`liked`）和收藏状态（`favorited`），逻辑与推荐流一致。
- 每类结果最多返回 20 条。

### 错误码说明

| 状态码 | 说明 |
| --- | --- |
| `200` | 查询成功；无匹配结果时 `videos` 和 `users` 均为空数组 |
| `400` | 关键词为空 |
| `401` | 未登录或 Token 无效 |