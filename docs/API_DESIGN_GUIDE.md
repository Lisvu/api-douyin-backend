# API 设计规范

本文档用于统一本项目的 API 设计，依据课程课件 `第6章-RESTful.pdf` 和期末大作业要求整理。新接口必须遵守本文档，避免再出现动词式 URI 或不统一的请求/响应格式。

## 设计目标

本项目 API 采用 RESTful 风格，达到 Richardson Level 2：使用资源 URI 表示业务对象，使用 HTTP 方法表达操作，使用 HTTP 状态码表达结果。认证采用 JWT，每次请求都通过 `Authorization: Bearer <token>` 携带身份信息，后端不依赖服务端 Session。

## URI 规范

1. URI 使用资源名，不使用动作名。
2. 资源名使用复数形式，例如 `/videos`、`/users/me/videos`。
3. 版本号显式写入 URI，统一前缀为 `/api/v1`。
4. 子资源使用层级表达，例如 `/videos/{id}/views` 表示某个视频的观看记录。
5. 查询、分页、筛选使用 query string，例如 `GET /api/v1/users/me/videos?page=1&limit=6`。

禁止新增以下动词式路径：

```text
/api/videos/publish
/api/videos/{id}/view
/api/videos/reset-views
/api/users/delete
/api/admin/logs
```

## HTTP 方法规范

| 方法 | 语义 | 项目示例 |
|---|---|---|
| `GET` | 查询资源 | `GET /api/v1/videos/recommendations` |
| `POST` | 创建资源或提交非幂等操作 | `POST /api/v1/videos` |
| `PUT` | 更新资源或幂等设置状态 | `PUT /api/v1/videos/{id}/like` |
| `DELETE` | 删除资源 | `DELETE /api/v1/videos/{id}` |

## 状态码规范

| 状态码 | 使用场景 |
|---|---|
| `200 OK` | 查询、更新、删除成功 |
| `201 Created` | 注册、发布视频等资源创建成功 |
| `400 Bad Request` | 参数缺失或格式错误 |
| `401 Unauthorized` | 未登录、Token 缺失或过期 |
| `403 Forbidden` | 已登录但无权限操作资源 |
| `404 Not Found` | 资源不存在 |
| `500 Internal Server Error` | 服务端未知异常 |

## 响应体规范

所有 JSON 响应保留统一基础字段：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

当前代码为降低前端改动风险，仍保留 `videos`、`token`、`user`、`pagination`、`stats` 等业务字段。后续新增接口优先使用 `data` 包装业务数据。

错误响应示例：

```json
{
  "success": false,
  "message": "Video not found."
}
```

## 认证与权限

公开接口只有：

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
```

其他 `/api/v1/**` 接口都必须通过 JWT 拦截器校验。前端必须在请求头中携带：

```text
Authorization: Bearer <token>
```

权限控制规则：删除视频只能删除当前登录用户发布的视频；注销账户只能注销当前登录用户；观看记录和我的视频列表都只允许访问当前用户自己的数据；F14 点赞通知列表只返回「他人对我发布视频的点赞」，不包含自己给自己点赞。

## 日志与监控

课程要求记录每个用户请求的输入、输出和接口耗时。本项目通过 `RequestLoggerFilter` 记录：

```text
timestamp
method
url
statusCode
requestBody
responseBody
```

日志查询接口为：

```text
GET /api/v1/admin/request-logs
GET /api/v1/admin/stats
```

为避免监控面板轮询污染统计，后台监控接口自身不进入请求日志。

## 缓存建议

推荐视频、我的视频列表与监控数据均与登录用户和实时状态相关，默认不做 HTTP 缓存。后续若新增公开只读资源，例如公开视频详情，可以使用 `Cache-Control` 明确缓存时间。

## 当前 API 清单

| 功能 | 方法 | 路径 | 认证 |
|---|---|---|---|
| 注册 | `POST` | `/api/v1/auth/register` | 否 |
| 登录 | `POST` | `/api/v1/auth/login` | 否 |
| 注销账户 | `DELETE` | `/api/v1/users/me` | 是 |
| 推荐视频 | `GET` | `/api/v1/videos/recommendations` | 是 |
| 标记视频已观看 | `POST` | `/api/v1/videos/{id}/views` | 是 |
| 重置观看记录 | `DELETE` | `/api/v1/users/me/views` | 是 |
| 点赞或取消点赞（F04） | `PUT` | `/api/v1/videos/{id}/like` | 是 |
| 查看视频评论 | `GET` | `/api/v1/videos/{id}/comments?cursor=&limit=20` | 是 |
| 发表评论 | `POST` | `/api/v1/videos/{id}/comments` | 是 |
| 查看用户主页 | `GET` | `/api/v1/users/{id}` | 是 |
| 查看用户发布的视频 | `GET` | `/api/v1/users/{id}/videos?cursor=&limit=8` | 是 |
| 查看用户点赞的视频 | `GET` | `/api/v1/users/{id}/liked-videos?cursor=&limit=8` | 是 |
| 查看点赞通知（F14） | `GET` | `/api/v1/users/me/like-notifications?page=1&limit=10` | 是 |
| 标记点赞通知已读（F14） | `PUT` | `/api/v1/users/me/like-notifications/read` | 是 |
| 发布视频 | `POST` | `/api/v1/videos` | 是 |
| 查看我的视频 | `GET` | `/api/v1/users/me/videos?page=1&limit=6` | 是 |
| 删除我的视频 | `DELETE` | `/api/v1/videos/{id}` | 是 |
| 查看请求日志 | `GET` | `/api/v1/admin/request-logs` | 是 |
| 查看系统统计 | `GET` | `/api/v1/admin/stats` | 是 |

## 与课程要求的对应关系

| 课程要求 | 本项目实现 |
|---|---|
| 资源 URI | `/videos`、`/users/me/videos`、`/videos/{id}/views` |
| 统一接口 | 使用 `GET`、`POST`、`PUT`、`DELETE` |
| HTTP 状态码 | 成功、参数错误、未授权、无权限、未找到、服务端错误分别返回对应状态码 |
| 无状态认证 | JWT Bearer Token |
| 版本控制 | `/api/v1` |
| 日志监控 | `RequestLoggerFilter` 记录输入、输出、耗时 |
| 权限控制 | JWT 拦截器和视频删除 owner check |
