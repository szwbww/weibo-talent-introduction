# 开发计划：放宽深度发现的 MISSING_ORCID 资格门槛

> 复验对象：本计划（`2026-06-23-discovery-missing-orcid-gate-plan.md`）
> 采用方案：**方案 A —— 将 MISSING_ORCID 改为可配置（默认关闭）**
> 关联诊断：`scripts/diagnose-reject-reasons.sh` 实测 RAW=`orcid_info`：REJECTED 1175 / PASSED 426；其中 MISSING_ORCID 命中 1039（占被拒 88%），“仅缺 ORCID、其余全过”728 条。

---

## 需求描述

**可观察结果**：深度发现（L3→L2 晋升）在默认配置下，不再因“缺少 ORCID”淘汰候选人。邮箱有效、非中国籍、其余规则全过、仅缺 ORCID 的专家将被晋升到 CANDIDATE（L2），进入外联流程。预期晋升量从 426 升至约 1154（淘汰率 73%→约 28%）。

**必须不变（Must NOT change）**：
- ES 文档 ID 生成规则不变：`ExpertIdGenerator` 仍按 `orcid ?: EMAIL-<hash>` 生成，邮箱专家继续使用 `EMAIL-` 前缀 ID。
- 去重逻辑（按 email、按 orcid）不变。
- 其余所有资格规则的行为与默认值不变：`requireValidEmail=true`、`excludeChineseNationality=true`、学位/年龄/hIndex/引用/活跃度默认关闭。
- `MISSING_ORCID` 这个拒绝原因字符串保留（不重命名、不删除），以维持拒绝原因统计口径与历史数据可比。
- 富集逻辑不变：`enrichExistingExperts` 仍对 `EMAIL-` 前缀 ID 跳过 ORCID 富集。

**不在本计划范围（Out of scope）**：
- 方案 B：在主链路用“作者名+机构”反查补全 orcidId（design Step 4）。另开计划。
- 方案 C：修复 `inferCountryFromAffiliation` 国籍误判（136 条仅因国籍被拒中含误杀）。另开计划。
- 邮件文案 / 首封邮件转化率问题（与本资格门槛无关）。
- 历史 RAW 存量（已标记 `filterResult=REJECTED` 的 1175 条）的回填重算：仅在“验收/运维”一节给出操作步骤，不写入代码改动。

---

## 关键不变量

### Invariant I-1：MISSING_ORCID 仅在显式要求 ORCID 时拒绝
- 规则：`evaluateEligibility` 仅当 `candidateFilter.requireOrcid == true` 且 `expert.orcidId.isBlank()` 时，才追加 `MISSING_ORCID`。`requireOrcid` 默认 `false`。默认配置下该原因永不触发。
- 适用于：所有调用 `CandidateEligibilityService.evaluateEligibility(...)` / `isEligibleForCandidateIndex(...)` 的写路径——`ExpertDiscoveryService.processPaper`（L639）、`ExpertDiscoveryService.discoverFromOrcid`（L510）、`ExpertDiscoveryService.backfillRawEmailsAndPromote`（L928，临时 profile）、`ExpertRevalidationService`（L58 降级、L151 RAW 晋升扫描）、`ExpertIndexPromotionService`（L19）。
- 违反后果：若误把它做成无条件跳过（删掉规则）或无条件保留，则要么破坏“可配置”语义，要么继续误杀 728 条。

### Invariant I-2：身份/ID 生成与去重完全不受影响
- 规则：本计划只改“是否因缺 ORCID 拒绝”，不改 `ExpertIdGenerator.generate`、`toIndexMap` 中 `"orcidId" to esDocId` 的写法、`existsInRawIndexByEmail/Orcid` 去重查询。邮箱专家继续以 `EMAIL-<hash>` 作为 ES `_id` 和 `orcidId` 字段值落库。
- 适用于：`ExpertDiscoveryService.toIndexMap`、`promoteDiscoveredToCandidate`、`existsInRawIndexBy*`。
- 违反后果：若顺手改 ID 生成或去重，会造成重复落库或 L2/L3 ID 漂移，污染外联与回执匹配。

### Invariant I-3：本计划唯一的行为变更面
- 规则：相对当前默认配置，唯一可观察变化是“邮箱有效 + 非中国籍 + 其余规则全过 + 缺 ORCID”的专家由 REJECTED 变为 PASSED/晋升。国籍、邮箱、年龄、学位、学术类规则的判定逻辑与触发条件一律不变。
- 适用于：`CandidateEligibilityService.evaluateEligibility` 全部分支。
- 违反后果：扩大变更面会引入计划外回归，验证轮次膨胀。

### Invariant I-4：配置读取遵循既有 DB-优先-回落默认 模式
- 规则：`requireOrcid` 经由 `EligibilityFilterService.getCandidateFilter()` 读取，键名 `candidate.requireOrcid`，DB 无值时回落到 `CandidateFilterProperties.requireOrcid` 默认（`false`）。与现有 `requireValidEmail` 等键完全同构。
- 适用于：`EligibilityFilterService.getCandidateFilter()`、`getAll()`、`CandidateFilterView`。
- 违反后果：键名/回落不一致会导致后台开关失效或显示错乱。

---

## 现状审计

### CANDIDATE 资格判定（`CandidateEligibilityService.evaluateEligibility`）
- 当前首条无条件规则：`if (expert.orcidId.isBlank()) reasons += "MISSING_ORCID"`（无任何开关守卫）。
- 其余规则均已被对应开关守卫：`requireValidEmail`、`requireDoctoralDegree`、`enableAgeFilter`、`excludeChineseNationality`、`enableHIndexFilter`、`enableCitationFilter`、`enableActivityFilter`。
- **判定的 read 路径（谁来评估资格）**：
  1. `ExpertDiscoveryService.processPaper`（L639）—— 论文源主链路，profile 经 `buildProfile` 构造，`orcidId = authorEmail.orcidId ?: ""`，缺 ORCID 时为空 → 触发 MISSING_ORCID。**这是 728 条被误杀的真正位置。**
  2. `ExpertDiscoveryService.discoverFromOrcid`（L510）—— ORCID 源，`orcidId = record.orcidId` 一般非空，本规则基本不触发。
  3. `ExpertDiscoveryService.backfillRawEmailsAndPromote`（L928）—— 用临时 profile 评估。
  4. `ExpertRevalidationService`（L58 降级判定 / L151 RAW 晋升扫描）—— profile 由 `ExpertSearchService.toExpertProfile` 从 ES 读回，`orcidId` 取自 ES `orcidId` 字段（= 落库时的 esDocId，邮箱专家为 `EMAIL-...`，**非空**）→ MISSING_ORCID 在此路径**本就不触发**。
  5. `ExpertIndexPromotionService`（L19）。

> 关键交互点：同一个邮箱专家，在“发现主链路”被判 MISSING_ORCID 淘汰，但在“RAW 重扫/晋升”路径却因 `orcidId` 字段已是 `EMAIL-...` 而通过。**两条路径口径不一致**，本计划统一为“默认不因缺 ORCID 淘汰”。

### ES 索引字段（`orcid_info` RAW / `orcid_info_candidate` CANDIDATE）
- 写路径：
  1. `ExpertDiscoveryService.toIndexMap`/`indexToRaw` —— 写 `orcidId`(=esDocId)、`filterResult`(PASSED|REJECTED)、`filterRejectReason`(分号拼接)。
  2. `promoteDiscoveredToCandidate` —— 复制 RAW 文档 PUT 到 CANDIDATE。
  3. `ExpertRevalidationService` —— 降级删除 / RAW→CANDIDATE 晋升。
- 读路径：
  1. `ExpertSearchService.toExpertProfile` —— `orcidId = source.nullableText("orcidId") ?: "orcid" ?: "id" ?: ""`。
  2. `InitialOutreachService` 读 L2 候选（依赖 email，非 orcid）。
- 交互点：本计划**不改任何 ES 写/读字段**，仅改“是否因缺 orcid 把某条标成 REJECTED”。`filterResult`/`filterRejectReason` 的取值随判定结果自然变化（缺 ORCID 不再写入 REJECTED+MISSING_ORCID）。

### 配置链路（既有同构样板）
- `CandidateFilterProperties`（`@ConfigurationProperties`，默认值）→ `application.yml` 的 `candidate-filter` 段（env 覆盖）→ `EligibilityFilterService.getCandidateFilter()`（DB `eligibility_filter_setting` 优先，回落默认）→ `getAll()/CandidateFilterView` 暴露给 `GET/PUT /api/.../eligibility-filters` → 前端 `app.js`（L1792 起的设置项列表）。
- `eligibility_filter_setting` 由 `V26` 建表并 seed 默认键；`update(key,value)` 无白名单，任意键可写。

---

## 实现方案

> 全部任务遵循 I-1~I-4。不新增类、不新增状态、不改 ID/去重。

### 任务 1：新增配置字段（I-4）
- `config/CandidateFilterProperties.kt`：新增 `val requireOrcid: Boolean = false`。
- `resources/application.yml` `candidate-filter` 段：新增 `require-orcid: ${CANDIDATE_REQUIRE_ORCID:false}`。

### 任务 2：资格判定接入开关（I-1, I-3）
- `expert/service/CandidateEligibilityService.kt`：把
  `if (expert.orcidId.isBlank()) reasons += "MISSING_ORCID"`
  改为
  `if (properties.requireOrcid && expert.orcidId.isBlank()) reasons += "MISSING_ORCID"`
  （`properties` 即已读取的 `getCandidateFilter()` 结果；保留拒绝原因字符串不变）。
- 不动其余任何分支。

### 任务 3：DB 设置读取与后台可见（I-4）
- `expert/service/EligibilityFilterService.kt`：
  - `getCandidateFilter()` 增加 `requireOrcid = values["candidate.requireOrcid"]?.toBoolean() ?: candidateDefaults.requireOrcid`。
  - `getAll()` 的 `CandidateFilterView` 增加 `requireOrcid` 字段并赋值；同步在 `data class CandidateFilterView` 增加该字段。
- `resources/db/migration/V33__seed_require_orcid_setting.sql`（**新建迁移，不改已应用迁移**）：
  `INSERT INTO eligibility_filter_setting (setting_key, setting_value) VALUES ('candidate.requireOrcid', 'false');`
  （与 V26 seed 风格一致；幂等性由唯一键保证，迁移仅执行一次。）

### 任务 4：前端开关项（I-4）
- `resources/static/app.js`（L1792 起列表）：新增一行
  `{ key: "candidate.requireOrcid", label: "要求 ORCID", type: "bool" }`。
  纯展示/透传，沿用既有 PUT `/eligibility-filters` 流程，无新接口。

### 任务 5：测试（I-1, I-3）
- `test/.../CandidateEligibilityServiceTest.kt` 与 `CandidateEligibilityServiceEnhancedTest.kt`：
  - 新增：`requireOrcid=false` 且 orcid 空、邮箱有效、非中国籍 → `eligible=true`，`rejectReasons` 不含 MISSING_ORCID。
  - 新增：`requireOrcid=true` 且 orcid 空 → `rejectReasons` 含 MISSING_ORCID。
  - 回归：`requireOrcid=false` 但中国籍/邮箱无效仍各自被拒（确认 I-3 未扩面）。
- 若测试通过 mock `EligibilityFilterService` 提供 `CandidateFilterProperties`，需在构造里补 `requireOrcid` 默认值。

---

## 变更文件清单

| # | 文件 | 改动 |
|---|---|---|
| 1 | `src/main/kotlin/.../config/CandidateFilterProperties.kt` | 新增 `requireOrcid` 字段 |
| 2 | `src/main/resources/application.yml` | 新增 `require-orcid` 配置 |
| 3 | `src/main/kotlin/.../expert/service/CandidateEligibilityService.kt` | MISSING_ORCID 加开关守卫 |
| 4 | `src/main/kotlin/.../expert/service/EligibilityFilterService.kt` | 读取 `candidate.requireOrcid` + `CandidateFilterView` 增字段 |
| 5 | `src/main/resources/db/migration/V33__seed_require_orcid_setting.sql` | 新建迁移，seed 默认 false |
| 6 | `src/main/resources/static/app.js` | 新增后台开关项 |
| 7 | `src/test/kotlin/.../expert/service/CandidateEligibilityServiceTest.kt` | 用例 |
| 8 | `src/test/kotlin/.../expert/service/CandidateEligibilityServiceEnhancedTest.kt` | 用例 |

合计 8 个文件（≤10）。单一子系统（资格过滤）。新增共享存储字段 0 个 ES 字段、1 个 DB 设置键。

---

## 验收标准

- **I-1**：
  - 单测：`requireOrcid=false` + orcid 空 + 邮箱有效 + 非中国籍 ⇒ `eligible=true`，无 MISSING_ORCID。
  - 单测：`requireOrcid=true` + orcid 空 ⇒ rejectReasons 含 MISSING_ORCID。
- **I-2**：
  - 代码 diff 审查：`ExpertIdGenerator`、`toIndexMap`、`existsInRawIndexBy*` 无任何改动。
  - 集成/手测：发现一条无 ORCID 的论文专家，确认 ES `_id` 仍为 `EMAIL-<hash>`、不产生重复文档。
- **I-3**：
  - 单测回归：`requireOrcid=false` 下，中国籍 ⇒ CHINESE_NATIONALITY 仍被拒；邮箱无效 ⇒ INVALID_EMAIL_FORMAT 仍被拒。
  - 全量 `mvn test` 通过（JDK 11）。
- **I-4**：
  - 启动后 `GET /api/.../eligibility-filters` 返回含 `requireOrcid=false`；DB 改 `candidate.requireOrcid=true` 后 1 分钟缓存过期内生效；前端开关可见可切换。
- **集成场景（跨交互点）**：
  - 部署后跑一轮深度发现，对比 `scripts/diagnose-reject-reasons.sh` 输出：MISSING_ORCID 计数应大幅下降，PASSED 显著上升。

### 运维步骤（存量回填，非代码改动）
部署后，针对历史 RAW 中 728 条“仅缺 ORCID”的存量：触发一次 RAW 晋升扫描（`ExpertRevalidationService.promoteEligibleRawExperts`，即 `discover(includeRawScan=true)` 或其对应入口）。注意该路径读回的 `orcidId` 已是 `EMAIL-...`，本就不触发 MISSING_ORCID，故扫描后即可把符合条件的存量晋升至 L2。建议先在小批量/可观测下执行，再放量。

---

## Phase 4 自检

- [x] 关键不变量含每个新字段/状态≥1条（requireOrcid → I-1/I-4）
- [x] 现状审计经 grep 实证列全 evaluateEligibility 的写/读路径
- [x] 无未被不变量覆盖的新写路径（未新增写路径）
- [x] 文件数 8 ≤ 10
- [x] 子系统数 1 ≤ 2
- [x] 每个任务标注其支配不变量编号
- [x] 验收标准每条不变量≥1项检查
- [x] 文件清单无“相关文件/等”模糊项
- [x] Out of scope 明确顺延方案 B/C 与存量回填
