# 主计划：专家—发送账号绑定（2026-08-10 批次）

> **本文件是统筹文件，不是变更计划。** 它自身**不产生任何代码改动**，不设 `## 变更文件清单`、
> 不设 `## 实现方案`、不复制任何验证命令。它只承载**单份计划无法承载的跨计划约束**。
>
> **权威边界**：每份子计划的 `## 关键不变量`、`## 变更文件清单`、`## 验证命令`、`## 验收标准`、
> `## 人工验收清单` 是各自范围内的**唯一权威文本**。本文件**不得**复述、简化或改写它们 ——
> 复述必然漂移，届时 fix-v 不知道以哪份为准。
>
> 冲突裁决：若本文件的 M-n 与某份子计划的不变量冲突，**以子计划为准**，并把冲突记为本文件的缺陷来修正。
> 唯一例外是 M-1（串行执行）与 M-2（方法级所有权），它们按定义高于单份计划。
> G-1..G-3 是**跨计划共享的语义不变量**，子计划以 `(全局 G-n)` 引用，改动须先改本文件。

## 批次目标

给 `expert_contact` 引入发件账号归属，把"同一专家先后由不同账号发信"从系统默认行为
变成受控且可审计的例外。

**触发事件**：2026-08-10 14:39:59 一封 INTRODUCTION 由已禁用账号 `LiLei`（`enabled=0`）发出。
根因不是缓存 —— `app.js:8358` 人工发送固定传 `senderAccountCode: null`，
后端 `ManualExpertMailService.kt:58` 回退到 `selectAccountForManualSending()`
（`MailSenderAccountService.kt:197-201`），其谓词 `isManualSendable()`（`:227-228`）
只排除 `SIMULATOR_NOOP`、**不看 `enabled`**，同分时 `thenBy { it.id }` 取 ID 最大者。

更深层的问题是 `expert_contact`（`V1__create_business_tables.sql:79-95`、`ExpertContact.kt:8-31`）
**没有任何 sender 归属列**，账号归属只散落在 `mail_record.sender_account_code`
（详见 `K-sender-account-selection-sites`：全仓 7 个独立选号决策点）。

| 编号 | 子计划 | 交付 | 用户可见性 |
|---|---|---|---|
| **P1** | [`sender-binding-01-schema-and-establish.md`](sender-binding-01-schema-and-establish.md) | 绑定列 + 回填 + 建立点 + 解析服务（未接入） | **无**（只写字段不消费） |
| **P2** | [`sender-binding-02-send-path-consistency.md`](sender-binding-02-send-path-consistency.md) | 四条发送路径按绑定解析 + enabled 门禁 | **高**（事故的直接修复） |
| **P3** | [`sender-binding-03-assignment-stock-balance.md`](sender-binding-03-assignment-stock-balance.md) | 分发打分计入存量绑定 | 低（分布形态变化） |
| **P4** | [`sender-binding-04-rebind-api-and-audit.md`](sender-binding-04-rebind-api-and-audit.md) | 换绑/迁移接口 + 审计 + 变更标记列 | 中（仅 API，UI 在 P5） |
| **P5** | [`sender-binding-05-frontend-visibility.md`](sender-binding-05-frontend-visibility.md) | 列表徽标 + 详情换绑 + 账号绑定数 | 高 |

**需求方已确认的四项决策**（子计划的不变量由此推导，改决策须先改本表）：

| # | 议题 | 决策 | 落在哪 |
|---|---|---|---|
| ① | 换绑后已有邮件线程 | 换绑只影响**新发起主题**；回复仍走 `mail_record.sender_account_code` | G-1 |
| ② | 绑定账号不可用时 | **报错拦截**，不降级重选（口径见 M-5 门禁矩阵） | P1 I-7 / P2 I-2 |
| ③ | 负载均衡 | 绑定强一致，存量绑定必须计入分发打分 | P3 全篇 |
| ④ | 变更标记 | 只有**运营主动换绑**打标；账号被烧后的**批量迁移不打标** | P4 I-1 |

---

## 全局不变量（子计划以 `(全局 G-n)` 引用）

### G-1: 绑定 = 主题发起权归属，不是外发通道归属

- Rule: `expert_contact.bound_sender_account_code` 决定**新发起主题邮件**从哪个账号发出。
  已存在的邮件线程（回复）由 `mail_record.sender_account_code` 决定，绑定不参与。
- Applies to: `PendingMailOperationService.kt:642-647`（人工回复用收信账号，**不改**）、
  `AutoMailReplyService.processSingle(account, ...)`（自动回复用收信账号，**不改**）。
- Violation consequence: 换绑后从新账号回复旧线程，`In-Reply-To` 与 `From` 域不一致 →
  投递到垃圾箱；且旧账号信箱里的线程失去回复方。
- 来源: 决策 ①

### G-2: 绑定为 NULL 表示"未绑定"

- Rule: 未绑定用 SQL `NULL` 表示，禁止空串、`"UNKNOWN"`、`"NONE"` 等哨兵值。
- Violation consequence: `IS NULL` 判定失效，V85 回填的幂等条件与"无绑定兜底"分支静默失效。

### G-3: `SIMULATOR_NOOP` 永不进入绑定

- Rule: `MailSenderAccountService.SIMULATOR_ACCOUNT_CODE`（`:257`）不得写入
  `bound_sender_account_code`，任何路径皆然（建立、回填、换绑、迁移、存量统计）。
- Violation consequence: 真实专家的外发被路由到 NOOP 通道，邮件静默丢失。
- 来源: `K-sender-account-enabled-scope`（"`SIMULATOR_NOOP` 始终必须被真实收发路径排除"）

---

## 跨计划不变量

### M-1: 五份计划必须严格串行，禁止并行执行

- Rule: 执行顺序恒为 **P1 → P2 → P3 → P4 → P5**（P3 与 P4 的相对顺序可换，见「执行顺序」）。
  任一时刻只允许一份计划在飞。**禁止**开多个 worktree 并行执行本批次任一两份。
- 依据：与 2026-08-06 批次（四组无共同根因的独立缺陷）不同，本批次是**同一特性的顺序切片**，
  文件集**重度重叠**且是设计使然：
  - `SenderAccountBindingService.kt` 被 P1 创建、P2 扩展、P4 再扩展（3 次）
  - `ManualInitialOutreachService.kt` 被 P1 / P2 / P3 各改一次（3 次）
  - `ExpertContactRepository.kt` 被 P1 / P3 / P4 各加方法（3 次）
  - `ExpertContact.kt` 被 P1 / P4 各加字段（2 次）
  - `InitialOutreachService.kt`、`MailSenderAccountService.kt`、
    `ExpertContactManagementController.kt`、`SenderAccountBindingServiceTest.kt`、
    `InitialOutreachServiceTest.kt`、`ManualInitialOutreachServiceTest.kt` 各被 2 份计划触及
- Violation consequence: 并行会在上述 10 个文件上产生持续冲突；更严重的是
  `resolveForSend` 的签名在 P1/P2 之间演进（见 M-3），并行执行会让 P2 基于过期签名实现。
- **推论**：本批次**不适用**"一个文件只能由一份计划改动"的所有权矩阵；
  取而代之的是 M-2 的方法级所有权。

### M-2: 共享文件按"方法/字段"划分所有权，禁止跨计划顺手改

- Rule: 下方矩阵是**排他**的。执行者若发现需要改动本计划名下之外的方法/字段，
  **必须停止并上报**，由本文件裁决后再动。**禁止**"顺手改一下"。

| 文件 | P1 | P2 | P3 | P4 | P5 |
|---|---|---|---|---|---|
| `db/migration/V85__add_expert_contact_sender_binding.sql` | **新建** | — | — | — | — |
| `db/migration/V86__add_expert_contact_sender_change_mark.sql` | — | — | — | **新建** | — |
| `campaign/domain/ExpertContact.kt` | `boundSenderAccountCode` / `senderAccountBoundAt` | — | — | `senderAccountChanged` / `senderAccountChangedAt` | — |
| `campaign/repository/ExpertContactRepository.kt` | `updateBindingById` | — | `countBindingsByAccount` / `countBindingsByAccountAndCountry` + 2 投影类 | `rebindSenderAccountById` / `migrateBindingByAccount` / `clearSenderChangeMarkById` / `findAllByBoundSenderAccountCode` | — |
| `mail/service/SenderAccountBindingService.kt` | **新建**：`bindingFieldsFor` / `resolveForSend` / `bindIfAbsent` + 2 异常类 | `resolveForSend` **加 `ignoreWarmup` 形参** | — | `rebind` / `migrateAccount` / `clearChangeMark` + 命令类型 | — |
| `mail/service/MailSenderAccountService.kt` | — | `isManualSendable`（**仅加 `enabled &&` 一行**） | — | — | `bindingCountsByAccount()`（新增方法） |
| `mail/service/SenderAccountAssignmentService.kt` | — | — | 全部（快照类型 / `loadBindingStock` / `assignmentScore` / `selectAccount` 签名） | — | — |
| `mail/service/ManualExpertMailService.kt` | — | `resolveAccount` + `:55-58` | — | — | — |
| `campaign/service/MeetingScheduleService.kt` | — | `:109` 账号解析 | — | — | — |
| `campaign/service/InitialOutreachService.kt` | `:49-62` contact 构造 | — | `:32` 取快照 + `:48` 传参 | — | — |
| `campaign/service/ManualInitialOutreachService.kt` | `:573-582` 新建分支的构造 | `:272`（材料提醒轮解析）+ `:550-582`（首封轮调序） | 两轮外层取快照 + 两处 `selectAccount` 传参 | — | — |
| `audit/domain/OperatorActionType.kt` | — | — | — | 尾部加 3 个枚举 | — |
| `campaign/controller/ExpertContactManagementController.kt` | — | — | — | 3 个端点 + 3 个请求体 | `ExpertContactResponse` 加 2 字段 + 映射 |
| `expert/controller/ExpertIndexController.kt` | — | — | — | — | `ExpertIndexResponse` + `from(...)` |
| `mail/controller/MailSenderAccountController.kt` | — | — | — | — | DTO 加 `boundExpertCount` + 8 处 `toResponse` |
| `static/app.js` / `static/index.html` / `static/styles.css` | — | — | — | — | **P5 独占** |
| `test/.../SenderAccountBindingServiceTest.kt` | **新建** 10 例 | — | — | +12 例 | — |
| `test/.../MailSenderAccountServiceTest.kt` | — | 改 1 加 1（见 M-4） | — | — | — |
| `test/.../ManualExpertMailServiceTest.kt` | — | 改 1 加 4（见 M-4） | — | — | — |
| `test/.../MeetingScheduleServiceTest.kt` | — | +2 例 | — | — | — |
| `test/.../SenderAccountAssignmentServiceTest.kt` | — | — | +6 例（既有零改动） | — | — |
| `test/.../InitialOutreachServiceTest.kt` | +1 例 | — | +1 例 | — | — |
| `test/.../ManualInitialOutreachServiceTest.kt` | +2 例 | — | +1 例 | — | — |
| `test/.../BatchSendTaskRuntimeIntegrationTest.kt` | 构造实参 +1（编译修复） | — | — | — | — |
| `test/js/senderBindingDisplay.test.js` | — | — | — | — | **新建** 6 例 |

- **`MailSenderAccountService.kt` 的双计划约束**：P2 只改私有谓词 `isManualSendable`（`:227-228`），
  P5 只加公有方法 `bindingCountsByAccount()`。二者互不重叠。
  P2 的验收标准断言 `isManualSendable` 的**方法体**逐字形态，
  **不得**写成"该文件 git diff 为空"（P5 落地后必然有 diff）。
- **`ExpertContactManagementController.kt` 的双计划约束**：P4 加端点与请求体，
  P5 改 `ExpertContactResponse` 与其映射。P4 的实现方案已明示"不新增 DTO 字段（P5 负责）"。

### M-3: `resolveForSend` 的签名演进有唯一顺序，后续计划不得回退

- Rule: 该方法的签名按下表演进，每一步只由指定计划改：

| 阶段 | 签名 | 所有者 |
|---|---|---|
| 1 | `resolveForSend(contact, manual: Boolean)` | P1 |
| 2 | `resolveForSend(contact, manual: Boolean, ignoreWarmup: Boolean = false)` | P2 |

  P3 / P4 / P5 **不得**再改该签名。P2 加形参时必须用**默认值 `= false`**，
  保证 P1 期写的 `SenderAccountBindingServiceTest` 10 个用例零改动通过。
- Violation consequence: 这是本批次唯一的"跨计划 API 演进"，也是并行执行会立刻炸掉的地方（M-1）。
  若 P2 改成必填形参，P1 的测试与 P4 的调用点会同时编译失败，且失败原因指向错误的计划。

### M-4: 三条锁定既有决策的测试，处置权归属必须明确

- Rule: 下面三条测试锁定的是**既有的、刻意的**设计决策，不是遗留缺陷。
  只有 P2 有权改其中一条，其余两条**任何计划都不得改**。

| 测试 | 锁定的既有决策 | 处置 | 依据 |
|---|---|---|---|
| `MailSenderAccountServiceTest.kt:35-46` `selectAccountForManualSending selects account at daily limit` | 人工发送脱离每日配额 | **任何计划不得改** | `K-operator-send-quota-paths`（hit_count 11，已提升进 `CLAUDE.md`） |
| `MailSenderAccountServiceTest.kt:48-57` `includes auto-paused accounts` | 人工发送不受自动暂停阻塞 | **任何计划不得改** | 同上 |
| `MailSenderAccountServiceTest.kt:62-74` `includes disabled accounts` | `enabled=false` 仅禁自动外发 | **仅 P2** 改写为 `excludes disabled accounts` | `K-sender-account-enabled-scope`（hit_count 8），收窄口径见 M-5 |
| `ManualExpertMailServiceTest.kt:351-363` `succeeds when selectAccountForManualSending returns disabled account` | 同上 | **仅 P2** 改写为绑定禁用时抛异常 | 同上 |

- Violation consequence: 前两条一旦被"顺手修正"，就等于静默推翻一个 hit_count 11 的团队决策；
  更糟的是它不会立刻暴露 —— 人工发送在额度满时静默失败，运营只会以为"系统卡了"。

### M-5: `enabled` 门禁的收窄口径是**账号来源**，不是**发送动作**

- Rule: 本批次拦截的对象是「**由绑定解析出的账号**」，不是「人工发送」这个动作。
  完整门禁矩阵（P1 的 I-7 是唯一权威实现，本表仅供跨计划核对）：

| 绑定账号状态 | 自动路径（`manual=false`） | 人工路径（`manual=true`） |
|---|---|---|
| `enabled=false` | 拦截 | **拦截**（本批次新增） |
| `autoSendPaused=true` | 拦截 | **放行**（保留既有决策） |
| 今日额度已满 | 拦截 | **放行**（保留既有决策） |
| `SIMULATOR_NOOP` | 拦截 | 拦截（既有） |

  显式指定收信账号的**回复**路径（`PendingMailOperationService.kt:642-647`、
  `AutoMailReplyService`）**完全不经过该矩阵**，禁用账号仍能收信并回复 ——
  `K-sender-account-enabled-scope` 的原始场景（2026-07-06 的
  `pending-reply-account-consistency-and-disabled-receive`）完整保留。
- Violation consequence: 若把矩阵理解成"人工发送一律要 enabled"，
  会连带禁掉禁用账号的回复能力，直接推翻 2026-07-06 那份计划的成果。
- 依据：需求方决策 ② 与 `K-sender-account-enabled-scope` / `K-operator-send-quota-paths`
  的调和结论。

### M-6: 迁移只有两个，版本号执行前复核，永不修改已应用迁移

- Rule: 本批次只有 P1 的 `V85` 与 P4 的 `V86`。P2 / P3 / P5 的变更文件清单均已声明"迁移：无"。
  **禁止**任何执行者以"顺手加个复合索引 / 加个归一化列"为由新增迁移。
- 执行前必须复核当前 `src/main/resources/db/migration/` 的最大版本号
  （规划时为 `V84__add_required_keys_to_compose_template.sql`），若已被占用则顺延。
- P3 已显式声明**不加** `(bound_sender_account_code, country)` 复合索引：
  该表为万级数据量，两个既有单列索引（`idx_expert_contact_bound_sender` / `idx_expert_contact_country`）
  足以支撑每批 2 次的 GROUP BY；加索引属独立性能议题，且会与 `V86` 撞版本号。

### M-7: 知识写回串行，后写者必须先读当前内容再追加

- Rule: 本批次会触及以下知识条目，**必须按落地顺序串行写回，追加而非覆盖**：

| 条目 | 动作 | 由谁写回 |
|---|---|---|
| `mail/K-sender-account-enabled-scope.md` | 规划阶段已追加"收窄口径"补充段；**P2 落地后**须把"计划中"改为"已生效 + commit" | **P2** Phase 6 |
| `mail/K-sender-account-selection-sites.md`（规划阶段新建） | P2 落地后更新 A/B 两类的现状（已收口到 `resolveForSend`） | **P2** Phase 6 |
| `common/K-custom-exception-http-status-mapping.md`（规划阶段新建） | 无需再改 | — |
| `mail/K-operator-send-quota-paths.md` | **禁止改写既有正文**（见 M-4）；如需补充只能追加"本批次未改变该决策"一句 | **P2**（可选） |
| `campaign/K-expert-contact-two-write-sites.md` | P1 落地后追加"新增第 3 个写路径：`updateBindingById` 列级补写" | **P1** Phase 6 |
| 新建 `campaign/K-sender-binding-stock-balance.md` | 存量均衡的系数量纲原则（I-2） | **P3** Phase 6 |
| 新建 `frontend/K-contact-list-dual-path-field-parity.md` | 列表新字段必须同时进两条路径 DTO（`tags` 已踩过） | **P5** Phase 6 |

- Violation consequence: 覆盖式写回会让先落地那份的更正凭空消失，
  而知识库正是下一轮 create-p 的 Phase 0 输入 —— 丢失的更正会重新污染未来所有计划。

### M-8: 本批次不解决、也不得假装解决的五件事

- Rule: 以下五项**任何子计划的验收标准都不得**将其作为通过条件：

| # | 事项 | 为什么本批次不做 |
|---|---|---|
| 1 | **存量再平衡**（把已绑定专家迁到别的账号以均衡分布） | P3 只影响**新增**分配；批量迁移由 P4 的 `migrateAccount` 显式触发，不得自动化（P4 I-6） |
| 2 | **人工发送超出 `dailySendLimit`** | 是"人工脱离配额"既有决策的自然后果（P2 IP-2）。材料提醒批量会持续给绑定账号加计数至超限，**不视为缺陷** |
| 3 | **ES 索引持有绑定/标记字段** | 绑定是 MySQL-only 事实（P5 I-2）。任何计划不得写 ES mapping |
| 4 | **按「已变更」筛选专家列表** | 新增筛选控件须同步注册 5 处（`K-expert-filter-registration-sites`），单独立项 |
| 5 | **DB 路径专家标签恒为空**（`app.js` 写 `tags: c.tags`，但 `ExpertContactResponse` 无该字段） | 既有缺陷，P5 已在审计中记录并声明不修，只保证**不重蹈**（P5 I-1） |

- Violation consequence: 把不可达或不在范围的目标写进判据，会让正确的实现被判失败并触发返工。

---

## 执行顺序

```
① P1 ──► ② P2 ──► ③ P3 ──┐
   基座      修事故      均衡  ├──► ⑤ P5
         └─────────► ④ P4 ──┘   前端
                      换绑API
```

| 顺序 | 计划 | 为什么在这个位置 | 可回滚性 |
|---|---|---|---|
| ① | **P1** | 一切的前提。落地后系统行为**零变化**（只写字段不消费），可安全先行观察回填质量 | 高：回滚只需删两列 |
| ② | **P2** | 事故的直接修复，**优先级最高**。必须紧跟 P1，不要在 P1 与 P2 之间插入其他工作 | 中：回滚后退回"每次重新选号" |
| ③④ | **P3 / P4** | 二者**互不依赖，相对顺序可换**（P3 只碰打分，P4 只碰换绑与标记，M-2 矩阵无交叉） | 高：各自独立 |
| ⑤ | **P5** | 硬依赖 P3（`countBindingsByAccount`）与 P4（`senderAccountChanged` 列 + 换绑接口），必须最后 | 高：纯展示层 |

**强制串行**（M-1）：即使 ③④ 无依赖，也**不得并行执行** ——
二者都要改 `ExpertContactRepository.kt`（P3 加 2 个查询，P4 加 3 个更新 + 1 个 finder）。

**建议的观察间隔**：② 与 ③ 之间至少跑一轮完整的批量外联，确认 P2 的门禁没有误伤
（尤其是无绑定历史 contact 的兜底路径，P2 A-7）。

---

## 联合验收（任何单份计划都验不到的场景）

以下场景**跨计划**，只在全部落地后执行。单份计划的验收标准不覆盖它们，
本节也**不重复**任何单份计划已有的判据。

### J-1: 事故复现验证（P1 × P2 × P5）

- 前置：全部落地。选一位已绑定账号 `X` 的专家。
- 步骤：① 在账号池把 `X` 禁用；② 在专家详情页选任一模板点「发送」；
  ③ 查 `SELECT COUNT(*) FROM mail_record WHERE expert_contact_id=<id>
  AND direction='OUTBOUND' AND created_at > <步骤②时刻>;`；
  ④ 检查该专家的收件箱。
- 预期：② 弹出 400 错误且文案含 `X` 与 `DISABLED`（P2 门禁 + P5 的 I-6 原文透传）；
  ③ 返回 `0`；④ 未收到任何邮件。
- 意义：这是唯一能端到端证明 2026-08-10 事故已修复的场景。
  P2 的单测只覆盖服务层抛异常，P5 的 JS 测试只覆盖渲染，**报错能否走到运营眼前无人覆盖**。

### J-2: 一位专家的完整生命周期账号一致性（P1 × P2 × P4）

- 前置：全部落地。取一位全新专家（无 contact）。
- 步骤：① 跑一次首封批量把他纳入；记录发件账号 `A`；
  ② 让他回信，触发收信；③ 在收发件箱对来信做一次人工回复；
  ④ 在详情页创建并「确认并发邮件」一次会议安排；
  ⑤ 用 P4 接口把他换绑到 `B`；⑥ 再发一封模板邮件；⑦ 让他再回信一次并再次人工回复。
- 预期：①④⑥ 的 `mail_record.sender_account_code` 依次为 `A` / `A` / `B`
  （④ 用绑定，⑥ 用新绑定）；③⑦ 均为 `A`（收信账号，G-1）；
  ⑤ 后列表出现「发送账号已变更」标签。
- 意义：G-1 的完整证据链。P2 只验"新发起用绑定"，P4 只验"换绑写库"，
  **"回复不跟着换"这条横跨三份计划**，单份验不到。

### J-3: 账号被烧的完整应急流程（P3 × P4 × P5）

- 前置：全部落地。账号 `A` 名下有 ≥5 位专家，其中 1 位已被主动换绑过（带标签）。
- 步骤：① 把 `A` 禁用（模拟被封）；② 跑一次材料提醒批量，观察跳过统计；
  ③ 调 P4 迁移接口把 `A` 名下全部迁到 `C`；④ 刷新专家列表与账号池；
  ⑤ 再跑一次材料提醒批量。
- 预期：② 任务状态 `COMPLETED`（非 FAILED），`A` 名下专家全部记为
  「绑定账号不可用（A/DISABLED）」跳过（P2 I-4）；
  ③ 返回 `migrated` 等于 `A` 的绑定数；④ 列表账号列变为 `C`，
  **除原本那 1 位外均无「已变更」标签**（决策 ④）；账号池 `A` 绑定数为 0、`C` 增加相应数量；
  ⑤ 全部发送成功且发件账号为 `C`。
- 意义：这是决策 ④ 存在理由的完整演示 —— 也是本批次最可能被误实现成"迁移也打标"的地方。

### J-4: 存量均衡在真实数据上的方向性验证（P1 × P3）

- 前置：全部落地，且 `expert_contact` 已有 ≥1000 条绑定记录（V85 回填后的真实分布）。
- 步骤：① 记录 `SELECT bound_sender_account_code, COUNT(*) FROM expert_contact
  WHERE bound_sender_account_code IS NOT NULL GROUP BY 1 ORDER BY 2 DESC;`；
  ② 跑一次 100 人的首封批量；③ 重新执行 ① 的查询，对比增量。
- 预期：本批新增的 100 条中，落到 ① 中**存量最少**账号的数量显著高于落到存量最多的；
  但存量最多的账号（若权重高、额度空）**不为 0**（M-8 第 1 项：不做再平衡，只调新增倾向）。
- 意义：P3 的单测用构造数据验打分方向，**真实分布下会不会退化成单账号独吞**只能在此验证。

### J-5: 全量回归门禁

- 执行**各子计划 `## 验证命令` 节中的全量测试命令**（五份内容一致，均取自 `CLAUDE.md` 项目元信息）
  以及 P5 的 `node --test` 前端门禁。
- 本文件**不复制这些命令**（见文首权威边界）。通过判据以各子计划的 `## 验证命令` 节为准。

---

## 不属于代码、需要指派负责人的事项

| # | 事项 | 为什么代码解决不了 | 建议动作 |
|---|---|---|---|
| 1 | **发件账号被烧后的运营 SOP** | P4 交付的是 API，何时迁移、迁到哪个账号是运营判断 | 明确责任人与触发条件（如 `auto_send_paused_reason` 出现 `BOUNCE_RATE_HIGH:` 时），否则 P2 的门禁会把专家卡住而无人处理 |
| 2 | **批量迁移的 UI 入口** | P5 已 out-of-scope，当前只能 curl 调用 | 若运营无法自行调 API，需在 P5 之后单独立项；否则 J-3 的应急流程在生产不可用 |
| 3 | **绑定账号与 SPF/DKIM 域的对应关系** | 绑定强一致后，专家与发件域长期锁定，域名声誉问题的影响面从"随机分散"变成"整批集中" | 与 `docs/plans/2026-08-06/00-main-plan-mail-reliability.md` 的遗留项 2（发件域 SPF）合并评估 |

---

## 已知遗留（本批次刻意不做）

| 遗留 | 归属 |
|---|---|
| 存量再平衡（把已绑定专家按权重重新摊开） | M-8 第 1 项；需先观察 P3 上线后的分布收敛情况再立项 |
| 人工发送可超 `dailySendLimit` | M-8 第 2 项；属 `K-operator-send-quota-paths` 既有决策，如要改需单独立项并同时改选号入口与自增两侧 |
| 按「发送账号已变更」筛选列表 | M-8 第 4 项；须同步注册 5 处（`K-expert-filter-registration-sites`） |
| DB 路径专家标签恒为空 | M-8 第 5 项；既有缺陷，与本批次无关 |
| 账号池按绑定数排序 | P5 out-of-scope |
| `(bound_sender_account_code, country)` 复合索引 | M-6；性能议题，待存量过十万级再评估 |
| 换绑时对"存在活跃会话"的硬阻断 | P4 out-of-scope，当前只在审计 `note` 里提示（G-1 已保证回复不受影响） |

---

## 本文件的维护规则

- 子计划的任何**范围变更**（新增/移出文件、放弃某阶段）必须同步更新 M-2 的所有权矩阵。
- 需求方**改变四项决策中的任何一项**，必须先改本文件的决策表，再由 create-p 重写受影响的子计划 ——
  决策是子计划不变量的上游，反向修改必然产生不一致。
- 子计划落地后，在「执行顺序」表格的对应行标注落地 commit。
- 若某份子计划被 fix-v 判定"原计划存在结构性缺陷"需要重写，**先回到本文件复核 M-2 / M-3 / M-4**，
  再用 create-p 重写该份 —— 重写极易无意中扩大文件范围或改到 M-4 保护的测试。
- 五份全部落地且 J-1..J-4 通过后，本文件归档，不再维护。
