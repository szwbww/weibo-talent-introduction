# Child Brief — 04-registry（新发现人物主档与邮箱成员）

- Child ID: `04-registry`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-13/discovery-person-04-registry.md`（此 worktree 内已提交；含 A11 修订；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`（I-4/I-5/I-8、范围只限 discovered、开发顺序 01/02 之后）。
- child_base_sha: `abdf9f4645958e1ef7ef7c44963b51c4bc5898d4`（前序子计划 02-replay 的 Code head，已 LIGHT_PASS 验证）。
- 起始工作树 HEAD（开始前必须核对且 `git status` 干净）: 以 dispatch 消息给出的 `docs(fast-p): prepare child 04-registry` 提交 SHA 为准（含前序 fast-p 记账提交 docs/plans/fast/**，属 controller 记账，不得回退、不得纳入你的提交）。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- 要求使用 `execute-p` 技能执行本子计划。

## 前置已完成（可引用）
- 01/02 已验证：`DiscoveryIdentityRepairService`/`DiscoveryIdentityRecoveryService` 为补证接口基线（05 依赖）；本计划不改它们。
- 当前最大迁移 = V155（已核对），V156 未被占用。

## Authorized files（精确 7 个；如需第 8 个文件先停止并返回 PLAN_CONFLICT）

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V156__create_discovery_person.sql` | 新增：三表（person/member/author_key） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryPerson.kt` | 新增：主档/成员/结果 DTO |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepository.kt` | 新增 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonService.kt` | 新增 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonServiceTest.kt` | 新增 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryPersonRepositoryTest.kt` | 新增：真实数据库事务/并发/唯一冲突 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | A11：31 处全量迁移最新版本断言 155→156 + V156 三新表存在性断言 |

## Required commands（JDK 11 前缀不可省略；Docker 前缀按本机 OrbStack 约定；记录退出码与精确计数）
- C1（真库事务/并发；Testcontainers 引导参照 `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailOpenTrackingRepositoryIT.kt`：`@DataJdbcTest`+`Replace.NONE`+`MySQLContainer`+`@DynamicPropertySource`+`-DmysqlIt=true`）：
  `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmysqlIt=true -Dtest=DiscoveryPersonRepositoryTest test`
- C2：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonServiceTest test`
- C3（V156 真迁移，A11 后）：
  `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test`
- 若上述 Docker 前缀在本机需微调才能运行，采用可运行形式并把**实际精确命令**记录进报告（不得跳过 C1/C3）。
- 每次 `mvn test` 自动执行 JS 门禁；失败数必须为 0（基线 1657/1657/0）。

## 必须兑现的合同要点（以子计划正文为准；这里是索引）
- I-1：`personId` 为不带邮箱语义的稳定 UUID；每个原 docId 只隶属一个人物；主档只记录代表档案引用，不复制第二份准入/邮箱验证结论；主邮箱仅为展示默认值，不是发送许可。
- I-2：只有有效绑定当前姓名/邮箱的凭证内 ORCID/OpenAlex 键可占用作者唯一键；全名规范化一致、每类作者键不冲突、层间身份一致才可归入；无稳定键或缺绑定证据的 discovered 记录可按原 docId 一对一登记单例，但不创建作者键、不增加身份可信度或准入、不据同名归组；两个已有人物根被新证据连接 → 返回 `NEEDS_REVIEW`，不自动吞并根；按排序后的作者键/人物行加锁，唯一键冲突后重新读并复验，禁止捕获冲突后无条件当成功。
- I-3：登记输入必须由服务端核实发现来源、当前 discovered 标签、真实 docId 及证据指纹；成员指纹变化即失效待重核；读/发信不得盲信旧成员关系；跨范围 email/作者/联系人别名关联只返回 `SCOPE_CONFLICT`，不迁移范围外对象。
- 存储：V156 三表（`discovery_person`、`discovery_person_member`、`discovery_person_author_key`），字段/类型/COLLATE 按计划实现方案 1；email 仅索引；author_key 带 `ORCID:`/`OPENALEX:` 前缀；`evidence_json` 留存合并依据与规则版本；`revision` 仅人物关系/代表引用变化时递增；不新增既有共享表/ES 字段。
- 事务登记：Repository 使用显式事务及唯一约束；Service 只接收通过服务端校验的事实快照；返回 `CREATED / ATTACHED / UNCHANGED / NEEDS_REVIEW / SCOPE_CONFLICT / STALE`（后三者不写关系）；首次代表按已有可追溯主档优先、否则最小 docId 固定，之后新增邮箱不悄悄换代表；失败事务回滚，不暴露空主档。
- 读契约：批量 docId→personId、人物成员/作者键、revision、指纹查询；06/08 使用前重核；**05/10 是仅有登记调用者**；GET 与发送预估不得登记；默认开关关闭（无旧数据隐式全表迁移）。
- 边界：不修改已应用迁移；不动现有 ES/联系人 schema；无前端；无生产动作；`docs/plans/fast/**` 不得进入你的提交。

## 下游接口（05/06/08/10 依赖，保持稳定）
- `DiscoveryPersonService` 登记入口（服务端校验快照 → 结果枚举）与 `DiscoveryPersonRepository` 读查询（批量映射/成员/作者键/revision/指纹）。
- `NEEDS_REVIEW`/`SCOPE_CONFLICT`/`STALE` 语义（05 复用、10 只读复用、06/08 只用读查询）。
- V156 三表结构（10 复用同一 service/repository，不另建表）。

## 报告与提交
- 完整结果写入：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/children/04-registry/execution.md`（逐命令退出码/计数，实际 Docker 前缀，改动摘要，未决问题）。
- 本地提交仅含 7 个授权文件：`feat(fast-p): implement 04-registry`。
- 返回仅限：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不审查后续子计划、不修复无关行为、不 push/merge/改写历史；遇到计划未覆盖的行为决策或需要新文件：返回 BLOCKED/PLAN_CONFLICT。
