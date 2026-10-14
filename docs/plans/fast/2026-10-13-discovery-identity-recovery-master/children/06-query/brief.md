# Child Brief — 06-query（人物列表、精确计数与详情读接口）

- Child ID: `06-query`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-13/discovery-person-06-query.md`（此 worktree 内已提交于 36075ec；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`（I-5/I-6，研究门禁：流式完整匹配集 + 批量人物映射精确聚合；不新增 ES 冗余人物字段）。
- child_base_sha: `7a96b5957b75096110e392b25a98bb9ea44990a5`（05-ingestion 的 Code head，已 LIGHT_PASS_WITH_NOTES 验证）。
- 起始工作树 HEAD（开始前必须核对且 `git status` 干净）: 以 dispatch 消息给出的 `docs(fast-p): prepare child 06-query` 提交 SHA 为准（含前序 fast-p 记账提交 docs/plans/fast/**，属 controller 记账，不得回退、不得纳入你的提交）。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- 要求使用 `execute-p` 技能执行本子计划。

## 前置已完成（可直接引用）
- 04-registry：读查询 `findPersonIdsByDocIds`（批量）、`findMembersByPerson`、`findAuthorKeysByPerson`、`findPerson`（revision）、`findMember`（fingerprint）；V156 三表。
- 05-ingestion：所有成功出口已登记人物关系（发现/补证/恢复）。
- 本计划只读消费人物关系（`DiscoveryPersonQueryService`），不新增投影写路径；发送侧低层搜索方法单位不变。

## Authorized files（精确 8 个；如需第 9 个文件先停止并返回 PLAN_CONFLICT）

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryService.kt` | 新增（含只读响应 DTO） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt` | 修改：流式查询支持（保持旧低层方法单位不变） |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | 修改 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt` | 修改 |
| 5 | `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonQueryServiceTest.kt` | 新增 |
| 6 | `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt` | 修改 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt` | 修改 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementControllerTest.kt` | 修改 |

## Required commands（JDK 11 前缀不可省略；记录退出码与精确计数）
- C1：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonQueryServiceTest,ExpertSearchServiceTest,ExpertIndexControllerTest,ExpertContactManagementControllerTest test`
- 每次 `mvn test` 自动执行 JS 门禁；失败数必须为 0（基线 1657/1657/0；本子计划不改前端）。
- 性能门禁说明：计划要求先在 51,829 条真实形状脱敏数据上测量 p95≤2s / 额外堆≤128MiB，本环境无该规模数据 —— **不得伪造测量**；把它作为未决项记录（保留给人工/上线前验证），但必须覆盖"超过预算=查询不可用"的失败语义（人物分区每批上限 100,000 条 / 10 秒）与关闭 scroll。

## 必须兑现的合同要点（以子计划正文为准；这里是索引）
- I-1 完整匹配集先归人：在当前层级执行**完整原筛选**，再按有效人物键聚合、排序、分页；整套条件必须由**同一成员**满足；范围外 key 仍用原 docId/contactId；不按姓名合并；默认排序取命中成员中原排序最前者，最后以 personId/docId 保证稳定；不因主档缺失于本层而隐藏整个人。
- I-2 详情与操作的引用不丢：响应新增只读 `person` 对象（personId/revision、代表 docId、邮箱成员、各成员原层级/contactId/状态及证据来源）；不得把 personId 填进旧 `orcidId` 参数；每个邮箱资格来自自身；人工资料/运营状态冲突展示"存在差异"，不覆盖任何成员；旧链接定位人物并标出原邮箱；编辑/发送/材料操作仍明确指向选中的原成员。
- I-3 精确性与时效：流式查询必须**完整结束才返回精确 total**；超预算返回可重试的查询不可用，不返回部分数或近似数；人物关系批量加载并核验当前标签/证据指纹；失效关系暂按独立档案显示并标记待核（发送另由 08/09 阻断）；GET 不登记、不修复、不创建联系人；不声称 ES 与 SQL 是事务快照。
- 实现步骤（详见计划 1-4）：①`DiscoveryPersonQueryService`：复用 `ExpertSearchService` 原筛选构造，把混合查询分成 discovered 与其他两部分——前者流式读取、按 500 条批量取人物关系、最小投影后精确归组；后者保持原 ES 精确 count、仅取合并排序所需前 from+size 条；同一排序键归并前缀后截取最终页；`total=其他原始匹配数+discovered 人物数`；各聚合桶同样分区相加；关闭 scroll；②`ExpertIndexController`：专家列表/筛选计数走新服务返回人物口径 `totalHits`（原 raw 文档搜索 API 保留给后台任务）；桶内按人去重（不要求各桶相加等于总人数）；新增只读 `GET /api/experts/person?docId=...&level=...`；③`ExpertContactManagementController`：新增可选 `groupDiscoveredPersons=true`（默认保持旧客户端契约），成员级完成既有状态/域名条件后聚合，返回总人数、person 信息及所有原 contactId；④四份定向测试覆盖混合范围、跨页、跨层、过滤不拼接、精确桶数、超时失败及只读。
- 边界：只读 ES/SQL（不新增投影写路径）；不改邮箱箱会话分组；不复制学术/准入真值；无迁移/新字段；无前端改动；`docs/plans/fast/**` 不得进入你的提交。

## 下游接口（07/08 依赖，保持稳定）
- `GET /api/experts/person?docId=...&level=...` 的只读 person 响应结构（07 两详情宿主与列表消费；08 只读复用）。
- `groupDiscoveredPersons=true` 分支响应（总人数、person 元数据、全部原 contactId；默认分支不变）。
- 聚合语义：bucket 按人去重、精确 total=其他+discovered 人物数（07 不再前端二次去重）。

## 报告与提交
- 完整结果写入：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/children/06-query/execution.md`（逐命令退出码/计数，性能门禁未决项，改动摘要，未决问题）。
- 本地提交仅含 8 个授权文件：`feat(fast-p): implement 06-query`。
- 返回仅限：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不审查后续子计划、不修复无关行为、不 push/merge/改写历史；遇到计划未覆盖的行为决策或需要新文件：返回 BLOCKED/PLAN_CONFLICT。
