# 专家材料手动上传：主计划修订 01

## 需求描述

在不修改产品、测试、数据库迁移或人工验收范围的前提下，替换 I-8 中因本机 Docker API 协商失败而不可执行的迁移验证命令。可观察结果是 V130 专项迁移用例在兼容 Docker Engine API 的 JVM 配置下退出码为 0。

不得改变：I-1～I-7、V130 的 MySQL 迁移断言、全量/定向 Java 与 Node 门禁、A-1～A-8 人工验收，以及发布顺序。

范围外：修复既有 `V124` 迁移测试、升级 Testcontainers/docker-java 依赖、修改 Docker Engine、产品代码、测试代码、迁移 SQL、人工验收结论。

## 关键不变量

### Invariant I-A1: 仅环境兼容替代
- Rule: 本修订仅以 `-Dapi.version=1.43` 解决 docker-java 1.32 与 Docker Engine 最低 API 1.40 的客户端协商；只运行 V130 专项迁移用例，且命令必须退出码 0。它不豁免 V130 验证，不把既有 V124 红灯归因于本边界，也不替代人工 A-8 的 MySQL 5.7 发布演练。
- Applies to: aggregate/master migration verification.
- Violation consequence: 环境问题会被静默跳过，或既有无关 V124 失败被误作本功能缺陷。
- 来源: original; human approval 2026-09-20 (`批准`).

### Invariant I-A2: 其他合同不变
- Rule: 除本修订明确替换的一条迁移命令外，主计划 I-1～I-8 的其余契约、允许文件范围与人工验收均保持原样。
- Applies to: aggregate/master review.
- Violation consequence: 环境规避扩张为未授权的质量门禁放宽。
- 来源: original.

## 现状审计

### 迁移验证环境
- Schema/mapping: V130 新增手动上传 owner 表及 `mail_attachment.manual_upload_id`；专项断言位于 `FlywayMigrationIntegrationTest#V130*`。
- Write paths: 无。本修订不修改产品或测试写路径。
- Read paths: 无。本修订不修改产品或测试读路径。
- Interaction points: Testcontainers 1.19.8 的 docker-java 默认 API 1.32 低于本机 Docker Engine 最低 API 1.40。`-Dapi.version=1.43` 仅改变客户端协商；先前 V130 专项命令已记录为 exit 0。

## 实现方案

### 阶段 1：应用验证合同修订（I-A1、I-A2）

1. 将本修订作为主计划 I-8 的已批准补充输入；不编辑主计划、子计划、产品、测试或配置。
2. aggregate reviewer 仅把以下命令替换为迁移门禁，并保留原始命令失败及其 Docker API 原因作为历史 RECORD_ONLY 证据：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -DargLine="-Dapi.version=1.43" -DskipNodeTests=true -Dtest='FlywayMigrationIntegrationTest#V130*' test
```

3. 对同一产品边界重新执行完整 aggregate review。替代命令必须 exit 0；若失败，仍为 MACHINE_BLOCKED。其他主计划命令、范围校验和人工项不变。

## 变更文件清单

| # | 受控文件 | 作用 |
|---|---|---|
| 1 | `docs/plans/2026-09-20/00-manual-expert-material-upload-main-amendment-01.md` | 已批准的 I-8 迁移环境兼容替代合同 |

文件数：1；不涉及产品子系统。

## 验收标准

- I-A1：替代命令 exit 0，且只执行 `FlywayMigrationIntegrationTest#V130*`；报告保留原始 Docker API 1.32/1.40 阻断为历史证据。
- I-A2：`git diff --name-only` 相对产品边界不新增产品/测试文件；重新 review 仍运行所有未替换的主计划命令，并保留 A-1～A-8。

## 人工验收清单

### A-A1: 验证豁免边界确认
- 前置条件: 已读取本修订和 aggregate machine report。
- 操作步骤: 1. 核对替代命令；2. 核对报告中的 V130 结果；3. 核对 A-8 仍为待人工执行。
- 预期结果: 仅 Docker API 协商命令被替换；V130 专项为 exit 0；MySQL 5.7 发布演练未被标记通过。
- 覆盖: I-A1、I-A2。
