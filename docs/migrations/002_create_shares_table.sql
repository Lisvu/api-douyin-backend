-- 视频转发功能：创建 shares 表
-- 在 api 库中执行（SSH 登录服务器后：psql -d api -f 本文件路径）
-- 或复制下面语句在 psql 中手动执行

CREATE TABLE IF NOT EXISTS shares (
    id BIGSERIAL PRIMARY KEY,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shares_to_user_created ON shares (to_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_shares_from_user ON shares (from_user_id);

COMMENT ON TABLE shares IS '视频转发记录';
