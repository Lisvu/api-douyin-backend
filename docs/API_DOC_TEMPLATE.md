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

## 备注
