# 数据库连接说明

本文档说明团队成员如何通过服务器访问项目 PostgreSQL 数据库。

不需要在自己的电脑上安装 PostgreSQL。先 SSH 登录服务器，再使用服务器上的 `psql` 连接数据库。

## 服务器信息

```text
Server: 165.232.172.99
Database: api
```

## 服务器登录账号

每个人使用自己的服务器账号登录。

```text
yunqi     4b9jMyf4FCWzfA9V87
lihao     P8PybDFgUJmkCwj5rf
nengen    9nxEeQnX3Y85w54yVH
wangting  KKt4W5mBhznVk9dP5Z
```

登录命令：

```bash
ssh yunqi@165.232.172.99
```

其他成员把用户名换成自己的账号：

```bash
ssh lihao@165.232.172.99
ssh nengen@165.232.172.99
ssh wangting@165.232.172.99
```

输入密码时，终端通常不会显示任何字符，这是正常现象。

## 进入数据库

SSH 登录服务器后，执行：

```bash
psql -d api
```

因为服务器系统账号和数据库账号同名，所以不需要再输入数据库密码。

成功后会看到类似提示：

```text
api=>
```

这表示已经进入 `api` 数据库。

## 常用数据库命令

查看所有表：

```sql
\dt
```

查看 `users` 表结构：

```sql
\d users
```

查询用户表数据：

```sql
SELECT * FROM users;
```

退出数据库：

```sql
\q
```

退出服务器：

```bash
exit
```

## 完整示例

以 `yunqi` 为例：

```bash
ssh yunqi@165.232.172.99
psql -d api
```

进入数据库后：

```sql
\dt
SELECT * FROM users;
\q
```

最后退出服务器：

```bash
exit
```

## 常见错误

### Connection closed by 165.232.172.99 port 22

可能是本机网络代理、VPN 或校园网导致 SSH 握手被中断。

可以尝试关闭代理/VPN 后重新连接：

```bash
ssh yunqi@165.232.172.99
```

### Permission denied

通常是用户名或服务器登录密码错误。

请确认：

```text
用户名是自己的服务器账号
密码复制完整，没有多余空格
```

### psql: command not found

说明当前不在服务器上，或者服务器环境异常。

正确流程是先 SSH 登录服务器，再执行：

```bash
psql -d api
```

### api->

如果提示符变成：

```text
api->
```

说明上一条 SQL 没有用分号 `;` 结束。

可以输入：

```sql
;
```

或按 `Ctrl+C` 取消当前输入。

## 账号说明

服务器账号和数据库账号是一一对应的：

```text
服务器账号 yunqi     -> 数据库账号 yunqi
服务器账号 lihao     -> 数据库账号 lihao
服务器账号 nengen    -> 数据库账号 nengen
服务器账号 wangting  -> 数据库账号 wangting
```

`api_app` 是后端 Spring Boot 程序专用数据库账号，普通成员不需要使用。

## 安全提醒

不要把服务器密码或数据库密码发到公开群、代码仓库或截图里。

如果怀疑密码泄露，请及时联系项目负责人重置密码。
