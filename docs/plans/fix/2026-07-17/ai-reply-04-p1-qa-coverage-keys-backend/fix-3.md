# P1-4 QA 覆盖能力标签后端：修复计划 3

## 原计划 / 子计划引用

- 总计划：`docs/plans/2026-07-15/ai-reply-p0-p2-master-plan.md`（Phase 4 / P1）
- 子计划：`docs/plans/2026-07-15/ai-reply-04-p1-qa-coverage-keys-backend.md`
- 上轮修复计划：`docs/plans/fix/ai-reply-04-p1-qa-coverage-keys-backend/fix-2.md`

## 约束摘录

- `coverageKeys` 只能声明 QA 正文已审核覆盖的业务事实；未知或薄弱规则必须保持空，不得补造事实。
- Program overview/about 仅在正文真实包含时标记 funding、remote/travel、no-fee、confidentiality。
- coverage key 表示审核过的回答能力，不是关键词命中；后续 intent matrix 依此判定缺失。
- `QaMatchService.match/suggestComposition` 不消费 coverage key，行为不变。

## 修正记录

| P1 | 触发频率 | 问题 | 证据 |
|---|---|---|---|
| P1-8 | 任一只询问远程、差旅或搬迁安排的 intent | V76 的工作安排标签与最终 QA 正文不一致：Program overview 明确“不需要搬迁”却漏 `work.relocation`；Workplace arrangement 没有远程安排却标 `work.remote_arrangement`；Full-time/part-time 没有搬迁信息却标 `work.relocation`。Phase 6 会把已覆盖的“不搬迁”报缺口，或把未覆盖的远程/搬迁事实误报为已覆盖。 | `V70__tighten_ai_reply_action_boundaries.sql:13-21`; `V3__seed_qa_rules.sql:30-31`; `V76__add_qa_rule_coverage_keys.sql:24,79,84` |

本轮 P1：1；上一轮 P1：3，数量严格下降。

## 修复规格

### P1-8：按最终正文修正工作安排标签

文件：

- `src/main/resources/db/migration/V76__add_qa_rule_coverage_keys.sql`
- `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`

变更：

1. 为 `Program overview` 的 canonical key 串加入 `work.relocation`；V70 正文明确说明 “no relocation ... required”。
2. 从 `Workplace arrangement` 回填移除 `work.remote_arrangement`；保留正文实际支持的 `work.travel_arrangement` 与 `work.relocation`。
3. 从 `Full-time and part-time options` 回填移除 `work.relocation`；保留正文实际支持的 `work.remote_arrangement` 与 `work.travel_arrangement`。
4. 增加静态迁移测试，逐段断言上述三项存在/不存在；不按 keywords 推断，不改 QA 正文或自动匹配逻辑。

## 当前状态

- 编译：未执行（用户限定仅复验 Phase 4）。
- 测试：未执行（用户限定仅复验 Phase 4）。
- 本轮未改实现；保留 main 当前工作区既有改动。

## 合规审计

- I-1 单一目录、未知/重复/空白拒绝：✅ `QaCoverageKeyCatalog.kt:11-77` 是唯一目录，并在写入前拒绝空白、重复与未知 key。
- I-2 旧客户端兼容：✅ create 缺字段保存空值见 `QaRuleManagementService.kt:70-72`；update 缺字段保留、显式空清空见 `:91-109`。
- I-3 存储/API 分离：✅ domain 使用 String 见 `QaRule.kt:23`；写入 canonical 串见 `QaCoverageKeyCatalog.kt:70,80-81`；读取去重并按目录排序见 `:73-78`；API 返回 List 见 `QaRuleManagementController.kt:316-372`。
- I-4 自动回复不变：✅ `QaMatchService.kt:17-97` 不读取 `coverageKeys`；生产引用仅为管理 service/controller。
- I-5 全写路径：✅ create/update 写入见 `QaRuleManagementService.kt:66-118`；enable 的 immutable copy 保留字段见 `:128-132`；delete 删除整行见 `:121-126`；V76 添加列见 `V76__add_qa_rule_coverage_keys.sql:6-7`。
- V76 已知规则回填：❌ P1-8。Program overview 漏搬迁能力（`V70__tighten_ai_reply_action_boundaries.sql:19`; `V76__add_qa_rule_coverage_keys.sql:24`）；Workplace 误标远程、Full-time/part-time 误标搬迁（`V3__seed_qa_rules.sql:30-31`; `V76__add_qa_rule_coverage_keys.sql:79,84`）。上轮 P1-1 至 P1-7 的空白拒绝、财务/差旅回填与 API canonical 输出仍满足要求。
- API metadata/response：✅ metadata 由 catalog 映射 key/label/description/group（`QaRuleManagementController.kt:131-140`）；响应使用 canonical `parseStored`（`:356-372`）。
- 测试：⚠️ `QaRuleManagementControllerTest.kt` 仍不存在，属原计划 T5 的测试覆盖缺口；未证明生产缺陷，P2 观察。
- Deleted code：✅ 无本计划要求删除的实现。
- No extras：✅ 仅审计 Phase 4 变更文件；main 其他脏文件未修改、未作为结论依据。

## 语义完整性检查

- Accumulation：✅ N/A，无时间窗口计数器。
- State machine：✅ N/A，无状态机。
- Cross-plan：❌ P1-8 破坏 Phase 4 写入 coverage key 与 Phase 6 读取 intent coverage 的契约，会产生 false positive/false negative 缺口。

## 本轮边界

- 按用户要求，仅执行 Phase 4 设计合规复验，未运行 Phase 2 编译或测试。
- 未更新知识库命中计数，以保留 main 当前已有改动。
