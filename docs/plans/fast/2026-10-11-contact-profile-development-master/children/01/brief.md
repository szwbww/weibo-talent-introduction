# Child Brief — 01 (storage)

- Child ID: `01`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-11/contact-profile-01-storage.md`（此 worktree 内已提交；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-11/contact-profile-development-master.md`（I-1..I-5、保持项、范围外、执行门槛）。
- child_base_sha: `91a79dcda9d4ddf55525c801f9c9b9b454321f63`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master`
- Branch: `fast/2026-10-11-contact-profile-development-master`
- 要求使用 `execute-p` 技能执行本子计划。

## Authorized files（精确 9 个；其余文件一律不得修改，测试证据产物 target/ 除外）

| # | 文件 | 操作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V153__create_expert_manual_profile.sql` | 新建：独立人工档案表 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertManualProfile.kt` | 新建：DTO、分组白名单、枚举、业务异常 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertProfileIdentityResolver.kt` | 新建：已存在专家身份校验 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertManualProfileService.kt` | 新建：单一 SQL 读写所有者 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileController.kt` | 新建：GET/PATCH 与局部异常映射 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertProfileIdentityResolverTest.kt` | 新建：身份、大小写、晋升、失败语义 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertManualProfileServiceTest.kt` | 新建：19 字段、分组与冲突 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertManualProfileControllerTest.kt` | 新建：鉴权与 API 契约 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 修改：V153 真库迁移与并发持久化验证 |

## Required commands（全部必须真实运行并记录精确计数/退出码）

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertProfileIdentityResolverTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest test`
- `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`
  - 必须使用这个带 DOCKER_HOST/api.version 前缀的精确形式：本机 OrbStack 守护进程要求 Docker API ≥1.40，而 Testcontainers 1.19.8 的 docker-java 默认 1.32；已验证该前缀下基线为 Tests run 39 / F 0 / E 0。不得跳过该 IT。

## 必须兑现的合同要点（以子计划正文为准，这里是标识索引）

- 19 个字段/列名/类型/上限/分组逐项按子计划 §1 表格；`expert_key` PRIMARY KEY `VARCHAR(64) COLLATE utf8mb4_bin`；真实独立列，禁止 JSON/EAV；`version BIGINT NOT NULL DEFAULT 0`、`updated_by VARCHAR(64) NOT NULL`、`updated_at DATETIME(3) NOT NULL`；仅匹配进度 `NOT NULL DEFAULT 'NOT_MATCHED'`，其余业务列可空；不加无请求的全局唯一约束。
- I-1 稳定身份：`expert_key = ExpertIdNormalizer.normalize(trim(expertId))`，1–64 字符；不得用邮箱/姓名/contactId/猜 `_id`；全部接口需登录会话。
- I-2 唯一所有者：仅 `ExpertManualProfileService` 写新表；现有共享表/ES 新增字段数 = 0。
- I-3 分组与版本：GET 无行→默认 DTO+version=0 且不插入；PATCH 只更新指定组全部字段、拒绝额外键、NULL/空白=清空、expectedVersion CAS、成功 version+1、冲突统一 409、失败事务不改字段。
- I-4 文本/审计：Unicode code point 计长；CRLF/CR→LF、trim；不静默截断；单行列拒绝换行；updated_by 取 session 用户（≤64）；updated_at 服务端 Asia/Shanghai 毫秒；不接受客户端审计字段；证件值不得进日志。
- I-5–I-23：逐字段语义见子计划（人才编号保留前导零；birthDateText 仅 YYYY 或真实 YYYY-MM-DD、年份 0001–当前年、不晚于当天、不补 1 月 1 日；documentNumber 非编辑态末四位遮罩；researchAbility 六档枚举含 NULL=未评估；companyMatchStatus 三档默认 NOT_MATCHED；matchedCompanies 字符串数组、去空白/空行、精确去重、保序、库内换行连接、单项无换行、总量≤2000、MATCHED 至少一家、改进度不清空）。
- 身份核验：`requireKnown` 先规范化+长度校验；已有档案行或 `ExpertContactRepository.existsByOrcidId`（原始 trim 与规范值去重）证实存在；否则 `ExpertSearchService.findByOrcidId` 依次选中层再 RAW/CANDIDATE/APPLICATION（按原始 trim/规范值去重）；返回记录规范化后必须等于请求 key；全层未命中 404；查询服务失败 503；不新建空专家；level 仅现有三种枚举。
- API：`GET /api/experts/manual-profile?expertId=...&level=RAW` → `{expertId,version,identity,education,technology,matching,updatedBy,updatedAt}`；空 companies 返回 []（库 NULL 对应），不用空串作第二空值；`PATCH /api/experts/manual-profile` `{expertId,level,group,expectedVersion,values}`，成功 200 返回完整最新 DTO；组 SQL 固定、参数化、不拼接客户端列名；existing row 用 `UPDATE ... WHERE expert_key=? AND version=?` 影响 0 行→409；首次 expectedVersion=0 INSERT 完整默认值+本组、version=1，重复键 409；验证在事务写入前。
- 错误映射（本 controller 局部 `@ExceptionHandler`，不动全局处理器）：401 未登录、400 无效参数、404 专家不存在、409 PROFILE_CONFLICT、503 EXPERT_LOOKUP_UNAVAILABLE、500 PROFILE_STORE_FAILED；`{code,message,detail}`；内部 SQL/路径不外泄。
- 迁移测试：只把「迁移到最新」的 152 断言改 153；历史 target 与 V152 测试名保留；禁止全文件盲替换 152；覆盖 152→153 与全新库→153，不破坏现有 notes/drafts 外键与数据。

## Downstream interfaces（后续子计划依赖，必须保持）

- 02 消费 GET/PATCH 的 DTO JSON 键名与四组 PATCH 语义。
- 04 复用 `ExpertProfileIdentityResolver.requireKnown` 做视频身份核验（同 404/503 语义经显式映射）。
- 03 不新增接口依赖（复用既有 contact-notes）。

## Global constraints

- 既有共享存储（expert_contact、ES 三层索引、材料、备注表）**新增字段数 0**；不创建联系人、不发邮件、不推进状态。
- Flyway：只新增 V153，绝不改写已应用迁移。
- 命令用 JDK 11（JAVA_HOME 如上）；遵循仓库 Kotlin/Spring Data JDBC 现状模式，不引入新框架。
- 不修改 plan/审计/其他 docs；不提交或篡改 `docs/plans/fast/**` 下文件（仅按下方报告路径写入/追加本 child 的 execution.md，且不得提交它；证据提交由 controller 统一完成）；不新增授权外文件；工作区不留未跟踪残留文件。
- 本地提交恰好一次：`feat(fast-p): implement 01`（不得 push/merge/rebase/amend/squash）。
- 报告路径：`docs/plans/fast/2026-10-11-contact-profile-development-master/children/01/execution.md`（写入完整结果：改动文件、命令与结果、偏差、下游接口事实、遗留风险；不得写入该目录以外证据文件）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + 报告路径（紧凑 JSON）。
