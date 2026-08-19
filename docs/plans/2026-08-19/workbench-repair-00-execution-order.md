# 可信化回复台修复：执行顺序（2026-08-19）

四份子计划，**必须按序执行**，每份独立通过 fix-v 与人工验收后再开下一份。

| 顺序 | 计划 | 文件数 | 子系统 | 依赖 |
|---|---|---|---|---|
| 1 | `workbench-repair-01-tab-focus-selector.md` | 2 | 1 | 无，可随时合入 |
| 2 | `workbench-repair-02-claim-paragraphs.md` | 3 | 1 | 无 |
| 3 | `workbench-repair-03a-per-request-evidence-version.md` | 9 | 2 | 建议 02 先（都动 `assemble`） |
| 4 | `workbench-repair-03b-source-version-split.md` | 8 | 2 | **强制** 03a 先 |

## 为什么拆成四份

原本设想「03 一份、内部分两阶段」。Phase 1b 的实测把它否掉了：

```
$ grep -rl "evidenceSetVersion\|sourceVersion" --include=*.kt src/main    → 9 个文件
$ grep -rl "evidenceSetVersion\|sourceVersion" src/main/resources/static/*.js → 2 个文件
$ grep -rl "evidenceSetVersion\|sourceVersion" --include=*.kt src/test    → 9 个文件
$ grep -rl "evidenceSetVersion\|sourceVersion" src/test/js/*.js           → 5 个文件
```

逐个判定「真正需要改」后，两阶段合计约 17 个文件，超过 create-p 的 10 文件硬限。拆开后 03a = 9、03b = 8，各自过线。

## 三个决定性的实测结论

1. **线上格式已支持逐条 evidence 版本**（03a 现状审计 C-4）：`TrustReplyLockedItemRequest` 与 `TrustReplyItemVersion` 本来每条就带一个 `evidenceSetVersion`，前端也已逐条拷贝。**因此不改字段名**——原本设想的「改名 requestEvidenceVersion」会把变更面从 9 撑到 17，已否决。
2. **evidence 是两层全局耦合，不是一层**（03a 现状审计 C-1）：`baseEvidence` 走 `sendQaRuleIds` 并集、`mappingCanonical` 走全矩阵。只切 mapping 不够。
3. **训练知识被拼进 `profileText`**（03b 现状审计 D-3）：运营编辑一条命中本信的训练知识就会改 `sourceVersion` 触发全量重置。这才是最频繁的触发源，不是「专家来新信」。

## 两个跨计划的安全底线

- **02 不改 composer**：段落规范化放在版本创建时（`materializeVersion`），composer 仍逐字组装。依据 K-locked-item-assembly-list-not-set。
- **03b 不放松研究匹配门禁**：`AiReplyIntentCatalog.kt:567` / `:705` 的 `requiresProfile && !profileSufficient → MISSING` 必须保留。画像归 evidence 组（硬失效），只有训练知识与 mailHistory 归 context 组（降级为提示）。

## 一个记录但不修的观察

`generate()` 的 FULL_DRAFT 分支用 base evidence（无 mapping）造初始 version，与 adjust/assemble 的 mapping-bound 口径不一致。但前端恒传 `operation: "ADJUST_ITEM"`，且已有 5 行测试断言工作台永不发 FULL_DRAFT（03a 现状审计 C-6）。**不可达，不修**，交人评审。
