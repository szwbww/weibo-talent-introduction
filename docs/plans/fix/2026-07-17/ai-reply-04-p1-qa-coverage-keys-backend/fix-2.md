# P1-4 QA 覆盖能力标签后端：修复计划 2

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 4 / P1）
- 子计划：`docs/plans/2026-07-15/ai-reply-04-p1-qa-coverage-keys-backend.md`
- 上轮修复计划：`docs/plans/fix/ai-reply-04-p1-qa-coverage-keys-backend/fix-1.md`

## 约束摘录

- `coverageKeys` 只能声明 QA 正文已审核覆盖的事实；未知或薄弱规则必须保持空，不得补造事实。
- Program overview/about 仅在正文真实包含时标记 funding、remote/travel、no-fee、confidentiality。
- 数据库保存 canonical 逗号分隔 key；API 始终返回按 catalog 顺序去重的 `List<String>`。
- coverage key 只供后续 AI intent matrix 使用；`QaMatchService.match/suggestComposition` 不消费它。

## 修正记录

| P1 | 触发频率 | 问题 | 证据 |
|---|---|---|---|
| P1-5 | 任一仅命中 `About the talent program` 的政府资金/企业报酬 intent | V57 正文明确说明政府研究经费与企业个人薪酬，但 V76 只回填 programme key；Phase 6 将把已审核事实误判为缺失。 | `V57__qa_material_tiering_and_funding.sql:20-25`; `V76__add_qa_rule_coverage_keys.sql:27-30` |
| P1-6 | 任一仅命中 `Full-time and part-time options` 的出差安排 intent | 正文明确可赴华并承担相关差旅费用，但 V76 未写 `work.travel_arrangement`；下游会把已覆盖的差旅安排标为缺失。 | `V3__seed_qa_rules.sql:30`; `V76__add_qa_rule_coverage_keys.sql:82-85` |
| P1-7 | 任一存量/人工维护导致 `coverage_keys` 顺序非 canonical 或含重复的规则列表/详情响应 | `parseStored()` 只 split/trim，未按目录排序也未去重，违反 API 始终返回有序去重列表的契约；异常存储值会直接泄露为不稳定 API 输出。 | `QaCoverageKeyCatalog.kt:73-74`; `QaRuleManagementController.kt:356-372` |

本轮 P1：3；上一轮 P1：4，数量严格下降，可继续收敛。

## 修复规格

### P1-5：回填 About 规则已陈述的财务能力

文件：

- `src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`

变更：为 `About the talent program` 的 canonical key 串加入 `finance.government_funding`、`finance.enterprise_compensation`，顺序遵循 `QaCoverageKeyCatalog`。保留 `coverage_keys = ''` 守卫；不得根据 keywords 推断、不得增加正文未实际陈述的工作安排 key。静态迁移测试同时断言两个 finance key 都在 About 区段。

### P1-6：回填 Full-time/part-time 规则已陈述的差旅安排

文件：

- `src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`

变更：为 `Full-time and part-time options` 的 canonical key 串加入 `work.travel_arrangement`，置于现有 `work.remote_arrangement` 与 `work.relocation` 之间。保留 subject 与空值守卫；不增补未由正文支持的其他 key。静态迁移测试断言该 subject 的区段包含 travel key。

### P1-7：从存储值恢复 canonical API 列表

文件：

- `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaCoverageKeyCatalog.kt`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`

变更：`parseStored()` 对非空 token 去重，并按 catalog 既定顺序返回合法 key。运行时 create/update 的严格校验保持不变；对异常/遗留的未知 token 采用静默忽略，避免管理列表读路径因单条坏数据整体失败。新增单元测试，输入乱序、重复和未知 key，断言输出为唯一、catalog 顺序的已知 key 列表。

## 当前状态

- 编译：未执行（用户限定仅复验 Phase 4）。
- 测试：未执行（用户限定仅复验 Phase 4）。
- 本轮未改实现；保留 main 当前工作区的无关改动。

## 合规审计

- I-1 单一目录/输入校验：✅ `QaCoverageKeyCatalog.kt:11-70` 为唯一目录并拒绝空白、重复、未知 key；`QaRuleManagementService.kt:66-72,84-109` 在 create/update 写入前调用。
- I-2 旧客户端兼容：✅ create 缺字段归一为空见 `QaRuleManagementService.kt:70-72`；update 缺字段保留、显式空清空见 `:91-109`。
- I-3 存储/API 分离：❌ 存储字段及 runtime canonical 写入正确（`QaRule.kt:23`; `QaCoverageKeyCatalog.kt:70,76-77`），但 `parseStored()` 未排序去重（`:73-74`），API 响应直接使用它（`QaRuleManagementController.kt:356-372`）。
- I-4 自动回复不变：✅ coverage key 生产引用仅在管理 service/controller；`QaMatchService.kt:1-180` 未消费该字段。
- I-5 全写路径：✅ create/update 保存字段见 `QaRuleManagementService.kt:66-118`；enable immutable copy 保留字段见 `:128-132`；delete 删除整行见 `:121-126`；V76 schema 见 `V76__add_qa_rule_coverage_keys.sql:6-7`。
- V76 已知规则回填：❌ `About the talent program` 漏两项正文已包含的 finance key（`V57__qa_material_tiering_and_funding.sql:20-25`; `V76__add_qa_rule_coverage_keys.sql:27-30`）；`Full-time and part-time options` 漏正文已包含的 travel key（`V3__seed_qa_rules.sql:30`; `V76__add_qa_rule_coverage_keys.sql:82-85`）。上轮 P1 已修复的 Program overview finance、精确 subject、Application criteria selection 均仍正确（`V76__add_qa_rule_coverage_keys.sql:22-25,62-85`）。
- API metadata/response：✅ metadata 从 catalog 映射 key/label/description/group（`QaRuleManagementController.kt:131-140`）；response 增加 `coverageKeys`（`:316-372`）。
- 测试：⚠️ `QaRuleManagementControllerTest.kt` 仍不存在，属原计划 T5 的测试覆盖缺口；未证明生产缺陷，记 P2 观察，不纳入本轮修复。
- Deleted code：✅ 无本计划要求删除的实现。
- No extras：✅ 仅审计子计划列出的范围；main 其余脏文件未读取为结论、未修改。

## 语义完整性检查

- Accumulation：✅ N/A，无时间窗口计数器。
- State machine：✅ N/A，无状态机。
- Cross-plan：❌ P1-5/P1-6 是 Phase 4 coverage key 写入与 Phase 6 intent matrix 读取间的契约缺口；会将正文已有事实标为缺失。P1-7 会破坏管理 API 的稳定列表契约。

## 本轮边界

- 按用户要求，仅执行 Phase 4 设计合规复验，未运行 Phase 2 编译/测试。
- 按用户要求，未执行 Phase 6 知识库写回，以免处理 main 当前已有知识库改动。
