# Fast-P Baseline — 2026-10-11-contact-profile-development-master

起始修订（Master base）：`96b89bb203019881a7fc36be557d30ad2a9c43a3`（= Plan 审计 `revision.txt`）。
基线在 worktree 检出该修订 + seed 计划后运行；产品源码与 `source-sha256.json` 一致（1051/1051）。

## 环境

- JDK: `/Library/Java/JavaVirtualMachines/zulu-11.jdk`
- Docker: 可用（Flyway IT 使用 `mysql:8.0.36` Testcontainers）
- Node: v25.7.0；磁盘剩余 39GiB

## 结果

| 编号 | 命令 | 结果 | 备注 |
|---|---|---|---|
| B1 | `mvn -DskipTests test-compile` | _pending_ | |
| B2 | `node --test src/test/js/*.test.js` | exit 0；tests 1568 / pass 1568 / fail 0 | 全量前端回归基线 |
| B3 | `mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | _pending_ | V152 基线 |

## 计划基线一致性

- `source-sha256.json` 1051 条：0 missing / 0 mismatch。
- 迁移占用：worktree 与全分支历史无 V153/V154；V150/V151/V152 为本地最高。
- `new-store-absence.txt` 对应检索（expert_manual_profile|expert_meeting_video）在 01/04 实施前仍零命中。
