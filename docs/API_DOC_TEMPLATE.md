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