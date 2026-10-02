# 收发信箱所在地与联系时间推荐：总计划

日期：2026-10-02。状态：待执行的开发计划；本轮只完成审计、设计和计划文件。基线 HEAD：`2b036ccce7e9956f6ea27420d8bc9e057a011da2`，具体源文件散列见证据。已有工作树改动不得回滚或混入本功能。

## 需求描述

1. 用户只需选择专家所在国家；多时区国家可选具体时区，不选则使用界面明确展示的默认时区。
2. 未形成回复习惯时，推荐当地 08:00–17:00；样本积累后，按近期回复时间与频次推算常回复的两小时窗口，输出北京时间。
3. 收发信箱现有状态行追加紧凑信息：`巴西 ▾  建议北京 01:00–03:00  ⓘ`。详情按需打开，不再增加大卡片。

必须保持：现有收发信、人工草稿、关注/状态/层级/材料/排期行为；现有国家统计口径；收件处理表写入与处理状态语义；原聊天 CSS 字节基线。

范围外：自动定位、邮箱域名猜国家、城市定位、自动发信/定时发信、发送成功率预测、按星期或节假日建模、跨联系人合并身份、清洗历史机器回复、机器学习服务、后台学习任务、学习结果持久化、左列表批量推荐。预览左侧的演示国家标签不作为本轮生产改造入口；本次落实已批准的右侧紧凑状态行。

## 关键不变量

### Invariant I-1: 人工所在地独立
- Rule: 新建 `expert_contact_location` 保存联系人级人工配置；不改写 `expert_contact.country` 或 ES 国家画像。无配置行表示未配置，不能猜国家。
- Applies to: 子计划 01 的 PUT、子计划 02 的配置读取、子计划 03 的保存。
- Violation consequence: ES 回填覆盖人工时区，或改变现有国家分布统计。
- 来源: original；事实依据见 01 审计。

### Invariant I-2: 唯一时间与回复口径
- Rule: 数据库 `received_at` 按 Asia/Shanghai 还原成 Instant，再投影专家时区；回复只来自绑定该 contact 的 `inbound_mail_processing`，排除模拟器，绝不把 INBOUND `mail_record` 再算一遍。
- Applies to: 子计划 02 的查询、去重、分桶和输出。
- Violation consequence: 多算回复或整体平移 8 小时。
- 来源: K-mailbox-inbound-source-authority；TimeZoneConfig:11 与 ImapMailReceiveService:339–343。

### Invariant I-3: 建议是可解释的回复活动窗口
- Rule: 未配置无推荐；有效回复不足 3 个当地日期用工作时间；其余使用 02 中固定参数。不得宣称统计到了发送后回复概率或专家保证在线。服务端现算，前端只显示结果。
- Applies to: 子计划 02、03。
- Violation consequence: 小样本被当作稳定偏好，或两端算法漂移。
- 来源: original。

### Invariant I-4: 顺序交付
- Rule: 01 → 独立验证 → 02 → 独立验证 → 03 → 独立验证 → 人工整体验收。后续子计划修改同一服务/DTO/测试文件时，必须以已验证的上一阶段为基线。
- Applies to: 全部实施与验证。
- Violation consequence: 并行覆盖、对旧提交验证、接口漂移。
- 来源: K-master-plan-shared-file-sequential-gates。

## 现状审计

| 已证实的事实 | 代码证据 | 设计后果 |
|---|---|---|
| country 是可空 VARCHAR(128)，首信从专家画像写入，另有回填 | V48:1；InitialOutreachService:89；ManualInitialOutreachService:903；ContactCountryBackfillService:69–75 | 新建窄配置表，避免改已有国家字段 |
| country 被账号分布与邮件统计读取 | ExpertContactRepository:126–132；MailRecordRepository:366–393 | 保留原字段读写闭包 |
| 会话 summary DTO 没有所在地 | MailboxConversationController:52–76 | 新增当前联系人接口，不扩散列表 DTO |
| 来信权威是 linked processing，账号范围排除模拟器 | MailboxConversationRepository:434–499；MailboxConversationService:529–531 | 直接读时间小投影，配置/习惯按联系人聚合 |
| 时区默认被设置为上海；IMAP 收到的时间转成系统 LocalDateTime | TimeZoneConfig:11；ImapMailReceiveService:339–343 | 明确按北京还原历史 DATETIME |
| 原聊天 CSS 有逐字相等测试 | src/test/js/mailboxChatStyle.test.js:14–47 | 只在 styles.css 追加新作用域规则 |
| 状态行 renderHeaderMeta 会被多处重新渲染 | mailbox-chat.js:1808；完整命中见 grep-receipts | 在渲染函数内正式接入，不用 MutationObserver 补丁 |
| JDK 11 实测 tzdb=2021e，系统 IANA 数据=2026c | timezone-audit.md 与 jdk11-zone-audit.tsv | 先满足 G-0；不能用旧时区库声称当前换算正确 |

证据包：[原始检索](contact-timing-evidence/grep-receipts.md)、[代码与前端基线](contact-timing-evidence/code-baseline.md)、[时区审计](contact-timing-evidence/timezone-audit.md)、[计划自检](contact-timing-evidence/plan-self-review.md)。检索范围明确限定仓库 `src/main`、`scripts`、`tools`；没有把注释、旧计划或未检查的线上进程当作运行事实。旧知识只作检索入口，当前行号以证据为准。（来源: K-plan-quantified-claims-need-grep-receipts）

## 实现方案

### G-0：运行时前提

在开发和实际部署的 JDK 11 上记录 tzdb 版本与目录兼容结果；要求数据至少达到本次目录版本 2026c，418 个目录 id 均可解析，日期偏移断言通过。当前本机 2021e 不满足。更新 Java 11 补丁版本/时区数据是环境前提，不修改 Java 主版本、不改 pom、不在本轮自动部署。生产 JVM 尚未检查，不推断生产与本机相同。环境未满足时，允许继续编写不依赖新库的代码，但不得通过时区验收或发布本功能。

### 顺序子计划

| 阶段 | 文件范围 | 可独立验证的产物 | 前置 |
|---|---:|---|---|
| [01 所在地配置](contact-timing-01-location.md) | 9 个 | 目录、数据库持久化、配置 GET/PUT | G-0；迁移号未冲突 |
| [02 推荐算法](contact-timing-02-recommendation.md) | 8 个 | 只读 timing API、默认工作时间、衰减分桶 | 01 已验证 |
| [03 紧凑前端](contact-timing-03-compact-ui.md) | 5 个 | 状态行、配置弹窗、依据弹窗、刷新/竞态保护 | 02 已验证 |

独立子系统只有所在地后台、推荐计算、前端三个，已拆开；每个子计划不超过 2 个。01 新建配置表，不在任何现有共享表增加字段；02 不增加持久字段；03 不增加浏览器持久字段。

### 共用接口边界

- `GET /api/mail/contact-locations/countries`：只读目录。
- `GET /api/mail/contact-locations/{contactId}`：配置；未配置返回 `configured:false`。
- `PUT /api/mail/contact-locations/{contactId}`：`{countryCode, zoneId:null|string}`；返回已持久化的配置。
- `GET /api/mail/contact-locations/{contactId}/timing`：02 新增，返回配置和推荐。前端不提交当前时间、不提交历史回复样本。
- 配置是 contactId 级共享数据，不是当前操作员个人偏好。有效写入按数据库最后完成的写入为准，本轮不增加版本锁。
- 推荐聚合该联系人的全部非模拟器业务账号；页面日期/方向/单账号筛选不改变个人习惯。依据中明确写“基于全部业务账号的已关联来信”。
- 不新增轮询。选择会话、现有刷新成功、保存所在地后回读 timing；下次读取自动纳入新来信、人工绑定旧来信与时间衰减。

### 参数为什么这样选

采用可解释的 48 个半小时桶、30 天半衰期、每日样本权重上限 2；3 个不同回复日是进入习惯模式的最低门槛。参数是首版工程设定，不是已经从生产数据拟合出的最优值。算法同时保留 08:00–17:00 先验，样本多且持续时允许推荐落在工作时间外。详情只显示样本数、日期数、当地/北京区间和少量最近时间，不做置信度百分比或复杂图表。

## 变更文件清单

本文件是编排文件，不授权额外代码修改。生产/测试文件的穷尽清单分别在 01、02、03；三个清单并集为 18 个文件（2026-10-02 A2 将 03 授权文件扩至 7 个），重复项按顺序接续。规划证据、知识计数和计划文件不计入实施文件范围。

## 验收标准

- I-1：01 的配置持久化测试和既有 country 不变断言通过。
- I-2：02 的真实 MySQL 投影测试、重复源测试、北京时间→专家当地时间测试通过。
- I-3：02 的固定时间算法用例与 03 的文案/界面用例一致；不得展示假置信度。
- I-4：每一子计划都有对应代码基线、验证结果；共享文件无跳阶段并行修改。
- G-0：保留实际 JVM 输出与时区日期断言；不得把本机系统 zoneinfo 版本当 JVM 版本。
- 每阶段运行本阶段明确的检查；最终运行现有 Maven/Node 回归门禁。MySQL 集成测试使用独立测试数据库，不对业务数据库执行清理 fixture。
- 回滚应用时保留新增配置表数据；旧应用不读取它。不得为回滚删除已应用 Flyway 迁移或人工所在地记录。

## 人工验收清单

### A-1: 从配置到默认推荐
- 前置条件: 测试环境有无回复的测试联系人；所在 JVM 已满足 G-0。
- 操作步骤: 1. 打开收发信箱并选中该联系人。2. 选择印度、保存。3. 刷新页面并点 ⓘ。
- 预期结果: 主行显示“印度”和“建议北京 10:30–19:30”；依据显示当地 08:00–17:00、样本不足、使用默认工作时间；重载仍为印度。
- 覆盖: I-1/I-2/I-3；需求 1、2、3；01→02→03 数据链。

### A-2: 习惯学习与时区覆盖
- 前置条件: 使用 02 的人工验收 fixture，巴西测试联系人在最近三个当地日期各有一封 14:15 来信。
- 操作步骤: 1. 刷新会话，点 ⓘ。2. 将时区由默认 Sao_Paulo 改为 Manaus 并保存。3. 再刷新。
- 预期结果: 历史回复数仍为 3，回复日数仍为 3；默认时区下当地峰值为 13:00–15:00、北京次日 00:00–02:00；修改后同批真实时刻按新时区重新分桶，依据显示 America/Manaus，不能保留旧的当地时间标签。
- 覆盖: I-1/I-2/I-3；需求 1、2；保存→重新分桶→界面刷新。

### A-3: 紧凑布局与既有功能回归
- 前置条件: 03 完成；测试联系人有可编辑草稿、材料、排期。
- 操作步骤: 1. 在 1440px 和 760px 宽度查看状态行，打开/关闭两个新弹窗。2. 编辑草稿后切换专家再返回。3. 操作关注、专家管理、材料入口和排期入口。4. 查看原国家分布统计。
- 预期结果: 新信息只占状态行必要行高，无蓝色大卡片；草稿内容保留；原按钮仍打开原入口；国家统计不因新所在地配置改变；邮件数量和处理状态不因查看推荐改变。
- 覆盖: I-1/I-4；需求 3；全部必须保持项；03 的 S-1/S-2/S-3。

开始人工验收时再从各计划 A-n 导出 acceptance 文件；现在不生成第二份清单。
