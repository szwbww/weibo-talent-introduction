# P1-4 QA 覆盖能力标签后端：修复计划 1

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 4 / P1）
- 子计划：`docs/plans/2026-07-15/ai-reply-04-p1-qa-coverage-keys-backend.md`

## 约束摘录

- 后端 `QaCoverageKeyCatalog` 是唯一合法 key 目录；未知 key、重复 key、单项空白均在 create/update 拒绝。
- API 输入与存储分离：存储为 canonical 逗号分隔字符串，API 为按目录排序且去重的列表。
- V76 只以稳定 `reply_subject` / id 回填正文已审核覆盖的事实；未知或薄弱规则必须保持空，不得以 keywords 推断。
- Program overview 只有在正文实际包含时才标注 funding、remote/travel、no-fee、confidentiality。
- Application criteria/process/materials 应按正文分别标注 selection、steps/timeline、required_materials。
- `QaMatchService.match/suggestComposition` 不消费 coverage key，行为不变。

## 修正记录

| P1 | 触发频率 | 问题 | 证据 |
|---|---|---|---|
| P1-1 | 任一运营请求携带 `coverageKeys: ["", "company.legal_name"]` | 单项空白被静默丢弃，服务仍保存其余 key；违反“单项空白拒绝”，调用方无法获知 payload 非法。 | `QaCoverageKeyCatalog.kt:55` |
| P1-2 | 所有新库执行 V76 | Program overview 正文明确包含政府 funding 与企业 compensation，但回填未标两个 finance key，后续 intent matrix 会把已审核事实误判缺失。 | `V70__tighten_ai_reply_action_boundaries.sql:17`; `V76__add_qa_rule_coverage_keys.sql:24` |
| P1-3 | 所有新库执行 V76 | 两条回填的 `reply_subject` 与既有种子不一致，因此 Full-time/part-time 和 Project sensitivity 的实际行保持空 coverage。 | `V3__seed_qa_rules.sql:30`; `V38__restructure_qa_categories_and_seed_new_rules.sql:110`; `V76__add_qa_rule_coverage_keys.sql:75,85` |
| P1-4 | 所有新库执行 V76，且后续出现 researcher-selection intent | `researcher.selection` 错标到仅说明可选工作角色的 `Possible role`，而正文含 eligibility 标准的 `Application criteria` 未标；后续会把“如何筛选研究者”误判为已有依据。 | `V3__seed_qa_rules.sql:27-28`; `V57__qa_material_tiering_and_funding.sql:27-32`; `V76__add_qa_rule_coverage_keys.sql:62-65` |

## 修复规格

### P1-1：拒绝单项空白

文件：

- `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`

变更：在 trim/filter 前检测任一 list item 为 blank；`null` 与空 list 仍分别保持“创建为空”和“更新清空”语义。抛出与 unknown/duplicate 一致的 `IllegalArgumentException`，且不得调用 repository `save`。新增 create 与 update 的单项空白回归测试。

### P1-2：补全 Program overview 的财务能力

文件：

- `src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`

变更：在 Program overview 的 canonical key 串中，按 catalog 顺序加入 `finance.government_funding`、`finance.enterprise_compensation`。静态迁移测试须同时断言这两个 key 存在，且只针对 `Program overview` 的稳定 subject 回填。

### P1-3：修正 V76 稳定 subject

文件：

- `src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`

变更：将 V76 两个 WHERE 值改为既有的精确 subject：`Project sensitivity concerns`、`Full-time and part-time options`；保留 `coverage_keys = ''` 守卫。静态迁移测试必须断言旧的两个错误字符串不再出现。

### P1-4：把 researcher selection 标到正文真实来源

文件：

- `src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`

变更：移除 `Possible role` 的 `researcher.selection` 回填；按计划把该 key 回填到 `Application criteria`。不得给 `Possible role` 增加 selection，也不得通过 keywords 推断。静态迁移测试应同时断言 `Application criteria` 有该 key，`Possible role` 无该 key。

## 当前状态

- 编译：通过（定向 Maven 执行已完成 Kotlin 编译）。
- 测试：`QaRuleManagementServiceTest` 18/18 通过；`QaMatchServiceTest` 34/34 通过；`QaRuleManagementControllerTest` 尚不存在。Surefire 父进程检查产生 `PpidChecker` 告警，但已写入的两份目标报告均为 0 failures / 0 errors。
- 未执行代码修复；main 既有改动未改动。

## 合规审计

- I-1 合法目录与未知/重复拒绝：❌ `QaCoverageKeyCatalog.kt:55` 过滤空白项，未拒绝单项空白；目录本身见 `:11-47`。
- I-2 旧客户端兼容：✅ create 的 null 归一为空见 `QaRuleManagementService.kt:70-72`；update 缺字段保留、显式空清空见 `:91-109`。
- I-3 存储/API 分离：✅ domain String 字段见 `QaRule.kt:23`；canonical 输出见 `QaCoverageKeyCatalog.kt:68-75`；响应 List 见 `QaRuleManagementController.kt:356-372`。
- I-4 自动回复不变：✅ `QaMatchService.kt` 无本计划生产改动；coverage key 仅在管理 service/controller 使用。
- I-5 全写路径：✅ create/update 写入见 `QaRuleManagementService.kt:66-118`；enable immutable copy 保留字段见 `:128-132`；delete 删除整行见 `:121-126`。
- V76 schema：✅ `V76__add_qa_rule_coverage_keys.sql:6-7` 新增 `VARCHAR(2000) NOT NULL DEFAULT ''`。
- V76 已知规则回填：❌ Program overview 缺 finance key（`:24`）；两个 subject 不匹配（`:75,85`）；selection 标注来源错误（`:62-65`）。
- API metadata/response：✅ `QaRuleManagementController.kt:131-140,316-372`。
- 删除代码：✅ 无本计划要求删除的实现。
- No extras：✅ 仅审计子计划列出的范围；`QaMatchServiceTest` 的 V75 改动属于其他 Phase，未纳入本次结论。

## 语义完整性检查

- Accumulation：✅ N/A，无时间窗口计数器。
- State machine：✅ N/A，无状态机。
- Cross-plan：❌ P1-2 至 P1-4 是 Phase 4 写入的 coverage key 与 Phase 6 intent matrix 读取的契约缺口；会导致已覆盖事实漏标或未覆盖事实误报覆盖。

## 观察（非阻断）

- 子计划 T5 指定的 `QaRuleManagementControllerTest.kt` 尚未创建；这是 API 测试覆盖缺口，当前未证明生产行为错误，记为 P2 观察，不纳入本轮 P1 修复范围。
