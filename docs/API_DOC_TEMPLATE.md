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

## 删除我的视频

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

## 查询业务日志记录

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

## 系统监控统计数据

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