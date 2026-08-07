# 主计划：邮件链路可靠性与 Message-ID 收口（2026-08-06 批次）

> **本文件是统筹文件，不是变更计划。** 它自身**不产生任何代码改动**，不设 `## 变更文件清单`、
> 不设 `## 实现方案`、不复制任何验证命令。它只承载**单份计划无法承载的跨计划约束**。
>
> **权威边界**：每份子计划的 `## 关键不变量`、`## 变更文件清单`、`## 验证命令`、`## 验收标准`
> 是各自范围内的**唯一权威文本**。本文件**不得**复述、简化或改写它们 —— 复述必然漂移，
> 届时 fix-v 不知道以哪份为准。本文件只写"哪些约束不属于任何一份子计划"。
>
> 冲突裁决：若本文件的 M-n 与某份子计划的不变量冲突，**以子计划为准**，并把冲突记为本文件的缺陷来修正。
> 唯一例外是 M-1（文件所有权），它按定义高于单份计划。

## 批次目标

修复 2026-08-06 从一次真实故障排查中发现的四组缺陷。触发点是收发件箱点「处理」时详情面板整体不渲染，
顺藤查出邮件链路上另外三组独立问题。四组缺陷无共同根因，**故意拆成四份独立计划**，各自可独立部署与回滚。

| 编号 | 子计划 | 解决什么 | 用户可见性 |
|---|---|---|---|
| **P1** | `expert-profile-absence-not-error.md` | ES 无画像被当 404，打断整个来信处理面板 | 高（运营点了没反应） |
| **P2** | `inbound-message-id-vendor-prefix.md` | 中继给 Message-ID 加前缀，`IN_REPLY_TO` 关联静默失效 | 低（降级到邮箱匹配，无人察觉） |
| **P3** | `outbound-message-id-01-fill-missing.md` | 4 类外发邮件仍用 JavaMail 默认 Message-ID，暴露内网主机名 | 无（影响投递率） |
| **P4** | `material-reminder-02-headers-personalization.md` | 含任务 0：`List-Unsubscribe-Post` 值不合 RFC 8058 | 无（影响投诉率） |

已完成的前置：`material-reminder-01-threading.md`（`3bff469` 已落地）；
`docs/plans/2026-06-20/unsubscribe-suppression-02-list-unsubscribe-oneclick.md` 已追加 `## 修正记录 → 修正 1`。

---

## 跨计划不变量

### M-1: 一个文件只能由一份计划改动（文件所有权唯一）

- Rule: 下方所有权矩阵是**排他**的。任一执行者若发现需要改动不属于本计划的文件，**必须停止并上报**，
  由本文件裁决归属后再动。**禁止**"顺手改一下"。
- Violation consequence: 四份计划设计为可任意顺序、可独立回滚。一旦跨计划改同一文件，
  回滚任一份都会破坏另一份，独立性彻底失效。

**文件所有权矩阵**（并集来自四份计划的 `## 变更文件清单`）

| 文件 | 所有者 | 其余计划的约束 |
|---|---|---|
| `expert/controller/ExpertIndexController.kt` | **P1** | 其余三份不得触碰 |
| `static/app.js` | **P1** | 其余三份不得触碰（四份中唯一的前端文件） |
| `mail/service/MessageIdNormalizer.kt`（新增） | **P2** | P3 不得调用（读侧专用，见 M-2） |
| `mail/service/UnmatchedInboundMailService.kt` | **P2** | — |
| `mail/service/BounceCollectionService.kt` | **P2** | — |
| `mail/service/OutboundMessageIdFactory.kt`（新增） | **P3** | P2 不得调用（写侧专用，见 M-2） |
| `mail/service/MeetingInvitationMailComposer.kt` | **P3** | P4 的 J-4 要求此文件零改动 → **降级为"P4 不得改动此文件"**，见 M-4 |
| `mail/service/AutoMailReplyService.kt` | **P3** | ⚠️ 见 M-4，P4 的 J-4 对本文件有方法级约束 |
| `campaign/service/MeetingScheduleService.kt` | **P3** | — |
| `mail/service/SmtpMailDeliveryService.kt` | **P4** | P1/P2/P3 均已在各自 out-of-scope 中声明零改动 |
| `mail/service/IntroductionMailComposer.kt` | **P4**（仅 `ComposedMail` data class） | P3 的 I-5 已声明不改此文件 |
| `mail/service/ManualExpertMailService.kt` | **P4** | P3 的 I-5 已声明不改此文件 |
| `db/migration/V84__personalize_material_reminder_template.sql`（新增） | **P4** | 四份中唯一的迁移，见 M-6 |
| 各计划自有测试文件 | 各自 | 无交叉 |
| `src/test/js/mailboxInboundTags.test.js` | **P1**（M-1 仲裁 2026-08-06：`:84` 的 `refreshExpertTagsFromEs` 桩随 P1 任务 2.2 契约同步为对象形态；计划文件清单遗漏，修复由计划自身 Interaction point 3 规则唯一确定） | 其余三份不得触碰 |

**零交叉确认**：除 `AutoMailReplyService.kt` 外（见 M-4），四份计划的文件集合两两不相交。

### M-2: Message-ID 的写侧与读侧职责分离，互不假设对方格式

- Rule: **写侧**（P3 的 `OutboundMessageIdFactory`）决定生成什么格式；
  **读侧**（P2 的 `MessageIdNormalizer`）必须**格式无关**，只做有界前缀剥离 + 精确相等。
  两个 object **禁止互相引用**，禁止共享常量。P3 新增的任何 `kind`（`meeting-invitation` /
  `auto-reply` / `meeting-confirmation`）**不得**出现在 P2 的代码或测试断言中。
- 依据：P2 的 I-1（禁止对 `+` 之后内容做格式假设，由 2026-07-05 那封 JavaMail 默认格式邮件证明）
  与 P3 的 I-3（唯一性只依赖 UUID，`kind` 段禁止被解析）本就是同一原则的两侧表述。
- Violation consequence: 一旦读侧开始识别写侧格式，历史数据（JavaMail 默认格式、
  `manual-outreach-`、`manual-rich-`）与未来新增类型全部成为特例，规则会持续膨胀。
- **推论**：P2 与 P3 **无依赖关系，可任意顺序落地**。P3 先落地不要求 P2 改动；P2 先落地不要求 P3 改动。

### M-3: 四份计划均不得新增 Flyway 迁移（P4 的 V84 除外）

- Rule: P1 / P2 / P3 的 `## 变更文件清单` 均已声明"数据库迁移：无"。P4 的 `V84` 是本批次唯一迁移。
  **禁止**任何执行者以"顺手加个索引/加个归一化列"为由新增迁移。
- Violation consequence: `mail_record.message_id` 无索引是既知事实（`V1:102`），
  P2 已明确以"≤3 次精确查询"规避而非加索引。加索引属独立的性能议题，需单独评估写入放大与数据量。
- 另：`V84` 的版本号需在 P4 执行前复核当前最大版本，若已被占用则顺延；**永不修改已应用的迁移**。

### M-4: `AutoMailReplyService.kt` 的双计划约束 —— "零改动"断言必须是方法级

- Rule: P3 修改 `AutoMailReplyService.kt` 的 `:567` 与 `:958` 两处 `ComposedMail(` 构造；
  P4 的 J-4 验收标准要求 `AutoMailReplyService.mailTemplateVariables()` **零改动**。
  两者指向**同一文件的不同方法**。因此：
  - P4 的 J-4 断言**必须以方法为粒度**（grep 该方法体前后一致），
    **不得**写成"`AutoMailReplyService.kt` 文件 git diff 为空"。
  - 若 P3 先落地，P4 执行者不得因该文件出现 diff 而判定 J-4 失败。
  - 若 P4 先落地，P3 执行者不得触碰 `mailTemplateVariables()`。
  - `MeetingInvitationMailComposer.kt` 同理：P4 的 J-4 提到它"零改动"，
    实际含义是**P4 自己不改它**，而非禁止 P3 改。P3 拥有该文件（M-1）。
- Violation consequence: 这是本批次唯一的文件级交叉，也是最可能产生假 P1 的地方 ——
  验证方看到文件有 diff 就判 J-4 违反，触发一整轮无意义返工。

### M-5: 三份计划都要改同一条知识，写回必须串行且后写者复核前写者

- Rule: `docs/knowledge/mail/K-message-id-fingerprint.md`（hit_count 8）有**两处**将被不同计划更正：

  | 更正内容 | 由谁写回 |
  |---|---|
  | "落库 `message_id` 与实际发出值一致"被证伪（中继会加前缀） | **P2** Phase 6 第 1 条 |
  | 修正表两处失准：缺失数 5→4；`PendingMailOperationService` 实为域名问题非缺失；`ManualExpertMailService` 已由 `3bff469` 修复 | **P3** Phase 6 第 1 条 |

  后落地的计划在写回前**必须先读一遍该文件的当前内容**，确认前一份的更正已在，
  然后**追加**而非覆盖。两份都写完后，该条目应同时包含这两处更正。
- Violation consequence: 覆盖式写回会让先落地那份的更正凭空消失，
  而知识库正是下一轮 create-p 的 Phase 0 输入 —— 丢失的更正会重新污染未来所有计划。
- 另：P3 还会新建 `K-outbound-message-id-single-factory.md`，P2 还会新建 `K-vendor-message-id-prefix.md`，
  两者主题不同、不冲突，但**必须互相 `[[链接]]`**，因为它们是同一事实的写侧与读侧。

### M-6: 本批次不解决、也不得假装解决的两件事

- Rule: 以下两项**任何子计划的验收标准都不得**将其作为通过条件：
  1. **Gmail 出现退订按钮** —— RFC 8058 §4 要求 List-\* 头被 DKIM `h=` 覆盖，
     实测腾讯企业邮 `h=Date:From:To:Message-ID:Subject:MIME-Version` 不含它们，`h=` 由中继决定，
     代码侧无解。P4 的 J-7 已显式禁止把该现象写入判据。
  2. **库内 `message_id` 等于实际投递值** —— 中继改写发生在我方之后，写侧拿不到改写后的值。
     P2 已选择在读匹配侧兼容，而非追求两侧相等。
- Violation consequence: 把不可达目标写进判据，会让正确的实现被判失败并触发返工。

---

## 执行顺序

四份计划**均无技术依赖**（M-1 保证文件不相交，M-2 保证 P2/P3 无序）。以下顺序按
「风险 × 收益 × 回归面」排序，是建议而非强制：

```
① P4-任务 0 ──► ② P1 ──► ③ P3 ──► ④ P2 ──► ⑤ P4-其余阶段
   一行改动      用户可见     投递率      静默缺陷      待①②③④ 观察后决策
```

- **① P4 的任务 0 单独先行**：一行改动、零回归面、合规性问题。**不需要等 P4 的其余阶段**——
  P4 文首决策点已声明任务 0 不在放弃范围内。可作为独立提交先落地。
- **② P1**：唯一有用户可见故障的一份，运营每天都在撞。含前端改动，回归面独立于其余三份。
- **③ P3**：改变 4 类邮件的 MIME 头形态，建议与 ④ 分开发布以便观察投递率变化。
- **④ P2**：纯读侧兼容，改动最保守，但需要真实收发信往返才能人工验收，放最后不阻塞前面。
- **⑤ P4 其余阶段**：文首决策点要求先观察 `material-reminder-01` 上线后
  `MATERIAL_REMINDER` 是否已进入 Gmail「主要」标签页，再决定是否执行阶段 A。**不要提前动。**

**并行执行**：若人手允许，① ② ③ ④ 可完全并行 —— 这正是拆四份的目的。
唯一的串行约束是 M-5（知识写回）与 ⑤（需前序观察期）。

---

## 联合验收（任何单份计划都验不到的场景）

以下场景**跨计划**，只在四份全部落地后执行。单份计划的验收标准不覆盖它们，
本节也**不重复**任何单份计划已有的判据。

### J-1: 外发 → 回信 → 关联的完整闭环（P3 × P2）

- 前置：四份全部落地。
- 步骤：① 触发一次**会议邀请**发送（P3 新收口的类型之一）；
  ② 记录 `mail_record` 中该行的 `message_id`；
  ③ 用测试专家邮箱在 Gmail 中对该邮件点「回复」；
  ④ 等收信轮询入库后，在收发件箱打开该来信。
- 预期：② 的值形如 `<meeting-invitation-{ORCID}-{UUID}@talents.szwebotech.cn>`（P3）；
  ④ 的候选列表首项为 `IN_REPLY_TO` / 置信度 90（P2 —— 尽管来信的 `In-Reply-To` 带
  `[0-9A-F]{16}+` 前缀而库内值不带）。
- 意义：这是本批次唯一能验证"P3 的新格式经过中继改写后仍被 P2 正确归一"的场景。
  P3 单测只覆盖生成，P2 单测只覆盖归一，二者的接缝无人覆盖。

### J-2: 无画像专家的完整处理流程（P1 × P2）

- 前置：四份全部落地；测试专家 `TEST-LUKAI-18014905480`（MySQL 有联系人、ES 无画像）。
- 步骤：① 打开其回信的处理面板；② 查看候选专家列表；③ 完成一次绑定或人工回复。
- 预期：面板完整渲染、标签区显示不可用提示（P1）；候选列表含 `IN_REPLY_TO` 项（P2）；
  整个处理流程可走通到底。
- 意义：这两份计划都以这个测试专家为验收对象，但各自只验自己那一半。

### J-3: 全量回归门禁

- 执行**各子计划 `## 验证命令` 节中的全量测试命令**（四份内容相同，均取自 `CLAUDE.md` 项目元信息）。
- 本文件**不复制该命令**（M-0 权威边界）。
- 通过判据以各子计划的 `## 验证命令` 节为准。

---

## 不属于代码、需要指派负责人的事项

以下两项不产生任何代码任务，但若无人跟进，本批次的部分投入不会转化为效果：

| # | 事项 | 为什么代码解决不了 | 建议动作 |
|---|---|---|---|
| 1 | **DKIM `h=` 未覆盖 List-\* 头** | 签名由腾讯企业邮 `bizesmtp.qq.com`（`s=card2607`）完成，`h=` 由中继 MTA 决定 | 联系腾讯企业邮支持要求加入 List-\*；或评估更换服务商；或自签第二份 DKIM（**不得签 `Message-ID`**，中继会改写它） |
| 2 | **发件域名 SPF** | 基础设施配置 | `material-reminder-01` 已记录该前置条件仍未解决，会抵消本批次的投递率收益 |

---

## 已知遗留（本批次刻意不做，已各自立项或记为观察项）

| 遗留 | 归属 |
|---|---|
| Message-ID 域名硬编码 `@weibo.com`（`ManualInitialOutreachService:587`、`ManualReplySendAttemptService:35`） | `outbound-message-id-02-domain-alignment.md`，待 P3 落地 + 需求方定 `manual-outreach-` 前缀取舍 |
| `ManualInitialOutreachService:587` 的 `.copy()` 覆盖了 composer 已生成的正确 Message-ID | 同上（是同一处代码的两个面向） |
| `expert_contact.current_index_level` 与 ES 实际层级不一致 | 数据问题非代码问题，P1 已列 out-of-scope |
| `mail_record.message_id` 无索引 | 性能议题，M-3 已声明不在本批次处理 |
| 退订 token 无有效期、收件人邮箱明文出现在 URL 中 | 符合 RFC 8058（HMAC 即其要求的 hard-to-forge 组件），记为观察项，不建任务 |

---

## 本文件的维护规则

- 子计划的任何**范围变更**（新增/移出文件、放弃某阶段）必须同步更新 M-1 的所有权矩阵。
- 子计划落地后，在「执行顺序」的对应节点标注落地 commit。
- 若某份子计划被 fix-v 判定"原计划存在结构性缺陷"需要重写，**先回到本文件复核 M-1 / M-4**，
  再用 create-p 重写该份 —— 重写极易无意中扩大文件范围。
- 四份全部落地且 J-1 / J-2 通过后，本文件归档，不再维护。
