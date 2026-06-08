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

## 本机启动 Spring Boot（必看）

后端配置在 `src/main/resources/application.properties`，数据库地址为：

```properties
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/api
spring.datasource.username=api_app
```

这表示程序连接**本机 5432 端口**，再通过 SSH 隧道转发到服务器上的 PostgreSQL。**不能**在本机直接写 `165.232.172.99:5432`（多数同学公网 IP 会被 `pg_hba` 拒绝）。

### 步骤（以 `nengen` 为例）

**终端 A（一直保持打开，不要关）：**

```bash
ssh -L 5432:127.0.0.1:5432 nengen@165.232.172.99
```

输入文档「服务器登录账号」表中 **nengen** 的密码，直到出现 `nengen@api-project:~$`。

**终端 B（启动后端）：**

```powershell
cd E:\api-douyin\api-douyin-backend
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
mvn spring-boot:run
```

看到 `Started ApiDouyinApplication` 即表示数据库已连通。

**可选：检查隧道是否生效（PowerShell）：**

```powershell
Test-NetConnection 127.0.0.1 -Port 5432
```

`TcpTestSucceeded` 为 `True` 时再启动后端。

### 在服务器上跑后端时

若直接在服务器里执行 `mvn spring-boot:run`，保持 `127.0.0.1:5432` 即可（数据库与后端在同一台机器）。

## 完整示例（SSH 登录后查库）

以 `nengen` 为例：

```bash
ssh nengen@165.232.172.99
psql -d api
```

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

### Connection to 127.0.0.1:5432 refused（启动后端时报错）

说明 **SSH 隧道未建立或已断开**。

处理：

1. 重新执行 `ssh -L 5432:127.0.0.1:5432 nengen@165.232.172.99`（用户名换成自己的）。
2. 确认 `Test-NetConnection 127.0.0.1 -Port 5432` 为 `True`。
3. 再执行 `mvn spring-boot:run`。

日志里若出现 `Unable to determine Dialect without JDBC metadata`，也是数据库连不上导致的，先按上面步骤修隧道。

### no pg_hba.conf entry for host "你的公网IP"

说明未走隧道，程序在直连远程库。请使用 `127.0.0.1:5432` + SSH 隧道，不要改回 `165.232.172.99:5432`（除非在服务器本机跑后端）。

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
