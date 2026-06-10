-- F14 点赞通知：users 表增加已读时间戳
-- 在 api 库中执行（SSH 登录服务器后：psql -d api -f 本文件路径）
-- 或复制下面语句在 psql 中手动执行

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_like_notification_read_at TIMESTAMP;

COMMENT ON COLUMN users.last_like_notification_read_at IS '用户上次标记点赞通知已读的时间';
