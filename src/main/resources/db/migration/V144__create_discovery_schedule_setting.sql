-- I-1（03）：深度发现同步模式的整数小时设置（单例 id=1）。
-- 只建表：无初始化行（无行 = 沿用部署 cron，不是停用也不是“已保存 2 小时”）、无外键、无 JSON 设置包。
-- updated_at 是该小时周期的锚点（应用以 UTC 墙钟写入 DATETIME(3)，毫秒精度）；刻意不设 ON UPDATE，
-- 避免“相同值重存”把锚点重置成新周期。范围（1～168）由应用侧双重保护，MySQL 可能忽略 CHECK 约束故不依赖它。
CREATE TABLE discovery_schedule_setting (
    id             TINYINT     NOT NULL,
    interval_hours SMALLINT    NOT NULL,
    updated_at     DATETIME(3) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '深度发现的整数小时间隔设置（单例 id=1；updated_at 为小时周期锚点）';
