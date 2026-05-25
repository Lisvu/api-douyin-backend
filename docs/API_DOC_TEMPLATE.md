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
