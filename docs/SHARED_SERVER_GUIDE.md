# 共享服务器部署说明

> 2026-06-16 更新

## 今天做了什么

在服务器 `165.232.172.99` 上部署了一个**共用的后端**（端口 8080），上传的视频和封面都存储在服务器上，所有人共享。

**之前**：每个人自己跑后端，视频存本地，别人看不到。
**现在**：服务器上跑着唯一一个后端，所有人前端都连它。

## 每个人 git pull 之后要做什么

### 前端（必须）

`.env` 文件改一行：

```env
VITE_API_BASE_URL=http://165.232.172.99:8080
```

改了之后重启 Vite：

```bash
# Ctrl+C 停掉，再
npm run dev
```

**搞定。** 打开 `localhost:5173` 直接用，不需要跑本地后端。

### 后端（只有需要改后端代码的人才要管）

`application.properties` 里检查 SSH 隧道账号是不是自己的（之前是 root，已改成 lihao）：

```properties
app.ssh-tunnel.ssh-username=你的服务器账号
app.ssh-tunnel.ssh-password=你的服务器密码
```

```text
yunqi     / 4b9jMyf4FCWzfA9V87
lihao     / P8PybDFgUJmkCwj5rf
nengen    / 9nxEeQnX3Y85w54yVH
wangting  / KKt4W5mBhznVk9dP5Z
```

## 改了后端代码怎么部署

push 到 GitHub 的 main 分支后，**服务器每 2 分钟自动拉取并重启**，不需要手动操作。

如果不想等（比如在演示前要马上生效），用 lihao 账号手动触发（因为后端进程和代码目录都属于 lihao，其他人账号没权限）：

```bash
ssh lihao@165.232.172.99 "/home/lihao/deploy.sh"
```

lihao 的密码见上文账号表。

## 服务器后端挂了怎么重启

```bash
ssh lihao@165.232.172.99 "cd ~/api-douyin-backend && nohup mvn spring-boot:run -q -Dspring-boot.run.profiles=server > backend.log 2>&1 &"
```

## 配置说明

| 文件 | 在哪 | 说明 |
|------|------|------|
| `application.properties` | GitHub | 本地开发用，每人改自己的 SSH 账号 |
| `application-server.properties` | 只在服务器上 | 服务器专属，不会被 git pull 覆盖 |
