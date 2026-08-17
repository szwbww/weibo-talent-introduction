# Child 04 执行报告 — 专家列表可达性徽章展示

- Child: 04
- Plan: docs/plans/2026-08-16/expert-reachability-04-list-badge.md
- Plan SHA-256: `5e11c8b73349f864afb3b8c44669ea77c4cdad4e393cb7dfe8084566b19f55ca`（`plan_identity.py` 开始与结束各复核一次，未变）
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-reachability@fast/expert-reachability@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-expert-reachability`
- Pre-execution code SHA: `111aea17aa434bc5836a9409b451dc72954d62be`（child 03 code head）
- Evidence HEAD（开始/结束）: `ccaae40638386a4e1ffefc7d57615fbf365e5d78`（child 03 证据提交；本执行**未产生任何提交**）
- 执行日期: 2026-08-17
- **结果: PLAN_CONFLICT**（详见「冲突说明」）

## 变更总览（4 个授权文件，无越界）

| # | 文件 | 任务 | 状态 |
|---|------|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt` | T1 | `ExpertIndexResponse` 加 `val reachability: String? = null`；`from()` 加 `reachability = expert.reachability` |
| 2 | `src/main/resources/static/app.js` | T2/T3/T4 | `loadContacts` 两路径补 `reachability` 键；`renderContactListItems` 内新增映射表/helper/徽章渲染；复选框禁用并集 |
| 3 | `src/main/resources/static/styles.css` | T5 | S-1 CSS 块逐字追加（`.academic-enriched` 之后、`/* === 详情 sub-tab === */` 之前） |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexControllerTest.kt` | T1 测试 | 补响应字段断言（HIGH 透传 + null 默认） |

未改动：`ExpertProfile` / `sourceFields` 白名单（child 02 既有）、`loadContacts` 其余键、
`.academic-badge` / `.academic-hindex` / `.academic-enriched` / `.expert-row-sub` / `.list-item` 规则块（N-3）、
`hIndexBadge` / `enrichedBadge` / `tagsHtml` / `bindingText` / `senderChangedTag` 定义行（N-2）。

## 各任务实现要点

### T1 — 后端响应透传（I-4-1）

`ExpertIndexResponse` 构造参数末尾追加 `val reachability: String? = null`（`enrichedAt` 之后）；
`from()` 调用末尾追加 `reachability = expert.reachability`。`expert.reachability` 由 child 02 的
`ExpertProfile.reachability` 提供，逐行核对无误。

### T2 — 前端两条路径补键（I-4-1 / I-4-2）

- MySQL 路径（按状态/需人工介入筛选，走 `expert_contact`）：contact 映射末尾补 `reachability: null`
  （该路径无 ES 数据，按 I-4-2 渲染「可达 未知」为正确表现；显式写出避免后续误判为「忘了传」）。
- ES 路径（走 `/api/experts`）：补 `reachability: e.reachability ?? null`。

### T3 — 映射表与徽章渲染（I-4-2 / I-4-3 / S-1）

`renderContactListItems` 内新增（**位置见「偏差说明」**）：

```js
const reachabilityMeta = {
    HIGH:                  { label: "可达 高",       cls: "reach-high" },
    LOW:                   { label: "可达 低",       cls: "reach-low" },
    BLOCKED_UNSUBSCRIBED:  { label: "已退订 · 停发", cls: "reach-blocked" },
    BLOCKED_BOUNCED:       { label: "邮箱失效 · 停发", cls: "reach-blocked" }
};
```

未命中键（null / undefined / 空串 / 未来新增值）→ `{ label: "可达 未知", cls: "reach-unknown" }`（无 `|| "LOW"` 兜底）。
`title` 文本：HIGH/LOW 档 `邮箱来源 ${emailSourceLabel(contact.emailSource)} · 域名 ${domainOf(contact.email)}`
（`emailSourceLabel`：`PAPER_FULLTEXT`→`论文通讯邮箱`、`ORCID_PUBLIC`→`ORCID 公开邮箱`；`domainOf` 由既有
`contact.email` 派生，不新增后端字段）；UNKNOWN 档固定 `缺少邮箱来源信息，无法判定可达性`；
BLOCKED 两档分别 `该专家已退订，不再发送` / `该邮箱曾硬退（收件人不存在），不再发送`。
徽章 DOM `<span class="reach-badge ${cls}" title="...">${label}</span>`，插入位置在 `hIndexBadge` **之前**
（`${reachBadge}${hIndexBadge}${enrichedBadge}`，S-1 DOM 结构逐字吻合）。

### T4 — 复选框禁用（N-4 / S-2）

`isBlockedReach(v) = typeof v === "string" && v.indexOf("BLOCKED_") === 0`；
复选框条件由 `${!contact.contactId ? 'disabled' : ''}` 改为
`${(!contact.contactId || isBlockedReach(contact.reachability)) ? 'disabled' : ''}`（取并集，不替换既有条件）。
无新增 inline style、无 opacity / line-through（S-2）。

### T5 — 样式落地（S-1）

S-1 契约 CSS 块（`/* === 可达性徽章（列表项） === */` 至 `.reach-blocked` 收尾）原样复制到
`.academic-enriched` 规则块之后、`/* === 详情 sub-tab === */` 注释之前。**逐字校验**：
`styles.css` 新增块与计划文件 S-1 代码围栏（```css）逐字节 diff 相等（40 行 = 40 行，VERBATIM_MATCH: True）。
styles.css 全部 diff 均为新增行，受保护规则块零改动行。

### T1 测试 — 响应字段断言

- 既有 `listExperts prefers mysql operatorStatus over elasticsearch`：`ExpertProfile` 加
  `reachability = "HIGH"`，新增断言 `assertEquals("HIGH", response.experts[0].reachability)`；
- 新增 `listExperts passes through null reachability when profile lacks it`：默认 null 透传断言。

## 验证命令（全部以 JDK 11 实际执行）

| 命令 | 结果 | 证据 |
|------|------|------|
| `node --check src/main/resources/static/app.js` | PASS | exit 0（APPJS_OK） |
| `node --test "src/test/js/*.test.js"`（mvn 内嵌 Node 套件） | PASS | 584 pass / 0 fail（基线同 584） |
| `JAVA_HOME=...zulu-11... mvn test -Dtest=ExpertIndexControllerTest` | PASS | exit 0，BUILD SUCCESS；Tests run: 19, Failures: 0, Errors: 0, Skipped: 0（基线 18 + 新增 1） |
| `JAVA_HOME=...zulu-11... mvn test`（全量回归） | **FAIL（1）** | Tests run: 2484, Failures: 1, Errors: 0, Skipped: 4；唯一失败 `OperatorStatusWriteSeamGuardTest`，见冲突说明 |
| `git diff --check` | PASS | exit 0，无空白/换行告警 |

说明：2484 = 基线 2483（child 02+03 后）+ 本计划新增 1 个测试用例。除守卫外全部通过。

## 验收标准逐项核对

- I-4-1：`renderContactListItems` 函数体内 `emailSource` 仅出现在 `emailSourceLabel` helper 定义与
  `title` 文本拼装中，档位判定唯一依据 `reachabilityMeta[contact.reachability]` 映射（不按其他字段重算）；
  `enrichedAt` 仅出现在既有 `enrichedBadge`（N-2 保护行，未改动）。
- I-4-2：`reachabilityMeta` 未命中默认分支返回 `reach-unknown`；`grep 'reachability || "LOW"|reachability ?? "LOW"'`
  零命中（内置 grep 复核）。
- I-4-3：`grep -c "已退订 · 停发\|邮箱失效 · 停发"` = 2（≥ 2 达标）。
- S-1：styles.css 新增块与计划契约代码块逐字节一致（python 提取 + diff，40 行全等）；
  `.academic-badge` / `.academic-hindex` / `.academic-enriched` / `.expert-row-sub` / `.list-item` 规则块零改动行。
- S-2：`renderContactListItems` 内无新增 `style=`，无 opacity / text-decoration: line-through 新增。
- N-2：app.js diff 中 `hIndexBadge` / `enrichedBadge` / `tagsHtml` / `bindingText` / `senderChangedTag`
  定义行零改动（diff 仅含授权新增行与两处 T2 键、一处复选框条件、一处 row-sub 插入行）。
- 回归：全量测试 2484 中 2483 通过，**1 个失败**（见冲突说明）。

## 偏差说明（一处，强制）

**T3 的映射表与 helper 定义在 `renderContactListItems` 函数体内，而非计划 T3 所述的「函数之前」。**

原因：本仓库 Node 测试（`src/test/js/senderBindingDisplay.test.js`、`loadContactsFilter.test.js` 等，共 584 例，
是 `mvn test` 全量回归的一部分）用 `extractFn()` 以「单个函数」为单位正则抽取 app.js 源码到 `vm` 沙箱执行，
只抽取 `escapeHtml` / `renderContactListItems` / `loadContacts` 等点名函数，**顶层 `const` 与 helper 不会进入沙箱**。
若按计划字面将 `reachabilityMeta` / `emailSourceLabel` / `isBlockedReach` 等放在 `renderContactListItems` 之前
并被其引用，沙箱内调用即抛 `ReferenceError`（已用最小复现脚本实证：
`ReferenceError: reachBadgeFor is not defined`），`senderBindingDisplay` 与 `loadContactsFilter` 两套件必然转红，
全量回归无法通过。JS 测试文件不在本计划授权 4 文件内，不可修改。

因此将映射与 helper 全部置为 `renderContactListItems` 函数内局部声明（函数自包含，抽取后无悬挂引用），
功能与语义与计划 T3 完全一致。计划全部验收标准均为位置无关的 grep/diff 检查，本偏差不影响任何验收项
（I-4-1/I-4-2/I-4-3/S-1/S-2/N-2 全部达标；改动后 JS 套件 584/0 全绿）。

## 冲突说明（PLAN_CONFLICT 依据）

**现象**：全量回归唯一失败为 `OperatorStatusWriteSeamGuardTest.operator_status write sites exactly match whitelist`。

**根因**：该守卫测试（`src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`，
**不在本计划 4 个授权文件内**）以「文件路径 + **绝对行号** + 上下文」三要素排除
`ExpertIndexController.kt` 内两处 DTO 回显的噪声命中（`EXCLUDED_NOISE_SITES`：
:94 `operatorStatus = contact?.operatorStatus ...`、:483 `operatorStatus = operatorStatus ?: expert.operatorStatus ...`）。
本计划 T1（授权文件 `ExpertIndexController.kt` 的 `ExpertIndexResponse` 数据类新增 `reachability` 字段）向
该文件插入了 1 行构造参数（数据类声明区，位于 :483 之前），使第二条钉死点位移至 **:484** →
排除项失效 → 该行重新进入违规集合（守卫设计上「宁可误报、不放过」）；守卫的 staleExclusions 自检
（:135-141）同样要求同步更新 `EXCLUDED_NOISE_SITES`。:94 钉死点位于新增行之前，不受影响。

**不可避性论证**：`reachability` 字段必须声明于 `ExpertIndexResponse` 数据类构造参数中（文件行
~380-455，全部位于钉死点 :483 之前），`from()` 赋值 `reachability = expert.reachability` 亦在
`from()` 体内（:456-502）。**任何**合法的 T1 实现都会在 :483 之前至少增加 1 行，使该钉死点必然位移。
不存在不改动守卫测试即可让全量回归通过的 T1 实现方式。这与 child 03 的 A1 情形（T4 端点插入使
90→94、431→483）完全同构。

**所需的最小修复（未经授权，未执行）**：按守卫自身协议与仓库先例（guard 提示文案
「排除名单失效请同步更新 EXCLUDED_NOISE_SITES 的 path/line/context（宁可误报、不放过）」；
child 03 的 A1 批准先例；同一守卫的「A5 授权行号修正」注释先例），将
`OperatorStatusWriteSeamGuardTest.kt` 的 `EXCLUDED_NOISE_SITES` 中
`ExpertIndexController.kt` 噪声项行号 **483→484** 更新（上下文子串不变，语义零变化；
:94 无需改动）。该文件不在本计划授权清单内，execute-p 规则禁止编辑未授权文件；
完成全量回归门禁因此需要**计划外授权**（本计划无对应 amendment；ledger 中 A1 仅授权计划 03）。

**请求裁决**：授权把 `OperatorStatusWriteSeamGuardTest.kt` 纳入本计划改动范围（仅 1 个噪声行号 483→484，
context 不变，白名单闭包与断言语义零变化），或提出替代方案。授权后本实现可立即完成收尾
（回归全绿、单次提交 `feat(fast-p): implement 04`、剔除 fast-p 报告文件）。

## 其他说明

- 全程未产生任何提交（`git log` HEAD 仍为 ccaae40）；工作区仅含 4 个授权文件的改动 +
  `docs/plans/fast/expert-reachability/ledger.md`（控制器既有改动，与本实现无关，提交时剔除）。
- 4 个授权文件的改动已全部就绪且逐项核验（见上），epoch 2（授权后）可 resume 直接收尾。
- 观察项（非本计划范围）：HIGH/LOW 档 title 的 `邮箱来源` 标签读取 `contact.emailSource`，
  而当前 `/api/experts` 响应 DTO（`ExpertIndexResponse`）不含该字段 —— 前端运行期该标签为空串
  （title 显示「邮箱来源  · 域名 …」）。计划 I-6 / T1 明确「不新增后端字段」「新增 ES 字段 0」，
  故按计划忠实实现；如需完整展示邮箱来源需另行授权（不属本计划 4 文件可完成范围）。
- 未 review 后续子计划（05/06）；未 push / merge / rebase / amend；未执行 `mvn clean package`
  （brief 必跑命令清单不含构建命令）。

---

# Epoch 2 执行记录（A2 授权后 resume，2026-08-17）

- Resume 依据：brief.md（epoch 2, amended）；amendment A2（ledger 已由控制器记录于 9e92424/9ced588）。
- 执行者：Reachability04ImplementerE2
- **结果: READY_FOR_VERIFICATION**
- 实现提交：`8530af4 feat(fast-p): implement 04`（5 个授权文件一次性提交；docs/plans/fast/ 报告文件未纳入，工作区提交后干净）。

## 已完成

1. **复核 epoch-1 保留改动**：逐文件审阅 `git diff`（ExpertIndexController.kt T1、app.js T2/T3/T4、
   styles.css T5、ExpertIndexControllerTest.kt T1 测试），与计划 T1-T5、S-1/S-2 契约一致；T3 函数体内
   放置的偏差（Node `extractFn` vm 沙箱限制）经确认成立，未回移。
2. **A2 守卫 pin 修正**（第 5 个授权文件）：`OperatorStatusWriteSeamGuardTest.kt` 的
   `EXCLUDED_NOISE_SITES` 中 ExpertIndexController.kt 噪声项行号 **483→484**（context 子串
   `operatorStatus = operatorStatus ?: expert.operatorStatus` 不变；:94 pin 未动；守卫断言语义零变化）。
   已用 grep 实证修改后钉死点在 ExpertIndexController.kt 的实际行号 = 484。
3. 提交前工作区仅含 5 个授权文件改动，无越界。

## 验证命令（全部以 JDK 11 实际执行）

| 命令 | 结果 | 证据 |
|------|------|------|
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `JAVA_HOME=...zulu-11... mvn test -Dtest=ExpertIndexControllerTest` | PASS | exit 0，BUILD SUCCESS；Tests run: 19, Failures: 0, Errors: 0（含内嵌 Node 套件 584 pass / 0 fail） |
| `JAVA_HOME=...zulu-11... mvn test`（全量回归） | PASS | exit 0，BUILD SUCCESS；**Tests run: 2484, Failures: 0, Errors: 0, Skipped: 4** |
| `git diff --check` | PASS | exit 0，无空白/换行告警 |

全量回归 = 基线 2483 + 本计划新增 1 个测试用例 = 2484；epoch-1 唯一失败的
OperatorStatusWriteSeamGuardTest 经 A2 pin 同步后转绿。

## 验收标准核对（epoch 1 已逐项达标，epoch 2 复核未变）

- I-4-1：`renderContactListItems` 内 `emailSource` 仅用于 title 拼装；档位唯一依据
  `reachabilityMeta[contact.reachability]` 映射。
- I-4-2：默认分支 `reach-unknown`；无 `reachability || "LOW"` / `?? "LOW"` 兜底。
- I-4-3：`已退订 · 停发` / `邮箱失效 · 停发` 两条文案分离（grep ≥ 2）。
- S-1：styles.css 新增 41 行与计划契约代码块逐字一致；受保护规则块零改动行。
- S-2：复选框条件取并集 `(!contact.contactId || isBlockedReach(contact.reachability))`；无行级置灰/透明度/删除线。
- N-2：`hIndexBadge` / `enrichedBadge` / `tagsHtml` / `bindingText` / `senderChangedTag` 定义行零改动。

## 偏差

无新增偏差。保留 epoch-1 记录的唯一偏差（T3 helper 置于函数体内，A2 裁决确认接受）。
