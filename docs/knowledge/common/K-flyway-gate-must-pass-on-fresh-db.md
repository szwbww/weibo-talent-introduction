---
id: K-flyway-gate-must-pass-on-fresh-db
domain: common
created: 2026-09-02
last_used: 2026-09-02
hit_count: 0
source: execute-p:provider-undelivered-column (repair)
severity: P0
---

带「基线守卫」的迁移（先校验存量数据再改写的 V-something）必须能在**全新数据库**上通过，
否则 `-DmigrationIt=true` 的全套 IT（`FlywayMigrationIntegrationTest`、各 `*MonitoringIT`）
从该迁移落地的 commit 起**整体红到无法启动**，且通常无人察觉——本仓库 V82 从
2026-08-04（f50f00e）红到 2026-09-02 才被发现，期间所有依赖全新库迁移的验证全部不可执行。

反例（V82 基线门，2026-09-02 修复）：守卫用 `id = 17/34 AND updated_at = '2026-06-26 22:14:06'`
这类**生产历史产物**定位被审计的 qa_rule 行。生产库 id/时间戳来自真实运维编辑与部署时刻；
全新迁移链会分配**不同的 id**（合约规则落在 28 而非 34）且 `updated_at` = 迁移运行时刻
（`ON UPDATE CURRENT_TIMESTAMP`，V1 DDL），任何前置迁移都不产生字面时间戳（全仓 grep 仅 V82 内出现）
→ 全新库必然 `SIGNAL 45000`。修复：守卫/禁用/取 category 全部改用
**`reply_subject` + 内容签名**（keywords、enabled、`SHA2(answer_body,256)`）定位；
内容签名对「运维改内容」的漂移检测能力不变（已用漂移造数复测 SIGNAL 仍触发）。

规则：
1. 写带守卫的迁移前，先回答「全新库上守卫的每一列是什么值」；凡依赖 id 序号、字面 updated_at、
   真实删除历史的行定位，都必须改为内容键（reply_subject 等稳定列）+ 内容哈希。
2. 守卫只对「行存在但内容不符」与「行缺失」两种情况 SIGNAL；内容用哈希/精确关键字比对，不用时间戳代理。
3. 校验手段（本仓库，OrbStack）：
   `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dtest=<IT> -DmigrationIt=true -DargLine="-Dapi.version=1.41"`
   （`api.version=1.41` 是 docker-java 默认 1.32 被 OrbStack 拒的必要参数）。
4. 已应用旧版 V82 的库（dev/prod）：V82 校验和已变，下次有**新待应用迁移**时 `validate` 会报
   checksum mismatch，需一次性 `flyway repair`；已全量到最新（无 pending）的库启动不受影响。
