# Fast-P Baseline — 2026-10-11-contact-profile-development-master

起始修订（Master base）：`96b89bb203019881a7fc36be557d30ad2a9c43a3`（= Plan 审计 `revision.txt`）。
基线在 worktree 检出该修订 + seed 计划后运行；产品源码与 `source-sha256.json` 一致（1051/1051）。

## 环境

- JDK: `/Library/Java/JavaVirtualMachines/zulu-11.jdk`
- Docker: OrbStack context（socket `unix:///Users/lukai/.orbstack/run/docker.sock`），CLI API 1.54；镜像 `mysql:8.0.36`、`testcontainers/ryuk:0.11.0` 本地已存在。
- Node: v25.7.0；磁盘剩余 39GiB。

### Docker/Testcontainers 环境修复（控制器级，非产品代码）

现象：裸命令 `mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` 在 `startMysql` 抛
`IllegalState Docker is required for Flyway migration tests`；Testcontainers 1.19.8 的 docker-java 客户端以 API 1.32 连接而 OrbStack 守护进程要求 ≥1.40（`client version 1.32 is too old. Minimum supported API version is 1.40`），且 `UnixSocketClientProviderStrategy` 固定查 `/var/run/docker.sock`（不存在）。

修复：不再依赖裸命令，改为显式环境提供 socket 与 API 版本：

```bash
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test
```

说明：Testcontainers 1.19.8 不读取 `~/.testcontainers.properties` 的 `api.version`；docker-java 只认 DOCKER_HOST 环境变量（特例）与 `api.version` 系统属性（`-Dapi.version` / surefire argLine）。`~/.testcontainers.properties` 保持原两行未改（尝试后已还原）。后续所有需要 Docker 的命令必须带上述前缀。

## 结果

| 编号 | 命令 | 结果 | 备注 |
|---|---|---|---|
| B1 | `mvn -DskipTests test-compile` | exit 0 | |
| B2 | `node --test src/test/js/*.test.js` | exit 0；tests 1568 / pass 1568 / fail 0 | 全量前端回归基线 |
| B3 | `mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | exit 1（环境，非代码） | 裸命令 Docker 发现失败；见上节修复 |
| B3r | 修复前缀版同命令（DOCKER_HOST + `-Dapi.version=1.41`） | exit 0；Tests run 39 / F 0 / E 0 | 基线耗时 ~9min（含镜像/容器启动） |

## 计划基线一致性

- `source-sha256.json` 1051 条：0 missing / 0 mismatch。
- 迁移占用：worktree 与全分支历史无 V153/V154；V150/V151/V152 为本地最高。
- `new-store-absence.txt` 对应检索（expert_manual_profile|expert_meeting_video）在 01/04 实施前仍零命中。
