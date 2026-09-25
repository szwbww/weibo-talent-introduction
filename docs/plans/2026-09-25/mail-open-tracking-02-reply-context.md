# 02 — 明确回复上下文与清除引用像素

状态：待实施。顺序：01→02→03→04。本阶段不启用跟踪，可独立部署验证；新增字段为内存 DTO 字段，不是共享存储列。

## 需求描述
- O-1：所有实际回复向统一发送层明确传递回复属性，即使主题不带 Re、SMTP 线程头缺失也不能跟踪。
- O-2：人工回复引用本系统图片时，在最终正文确定前清除可识别的本系统跟踪图片。
- 不改变：N-1 原回复的收件人、主题、线程头、附件、QA 选择及幂等行为；N-2 正常图片、链接、排版和非回复介绍邮件内容。
- 范围外：修复现存线程头缺失、实现完整 HTML sanitizer、识别第三方跟踪器、剥离任意转发代理重写后的未知 URL、修改业务自动回复规则。

## 关键不变量
### Invariant I-1: 业务上下文是回复事实
- Rule: ComposedMail 尾部 isReply:Boolean=false。自动 QA 回复、自动来信触发邀约、executeManualRichSend 恒 true；人工模板发送在 command.sourceInboundId 非空或材料提醒的真实来信 anchor 存在时 true；排期发送在 schedule.sourceMailRecordId 非空时 true。不能仅依赖 mailType 或主题。
- Applies to: 全部上述 ComposedMail 构造点
- Violation consequence: 主题改写或缺头的真实回复被追踪。
- 来源: K-outbound-thread-headers-single-seam、K-mail-record-source-inbound-id
### Invariant I-2: 上下文只向发送层传递
- Rule: 新位只供 03 排除跟踪；不写 mail_record，不改 inReplyTo/references/sourceInboundId 的既有值，不变更既有回复和会话查找规则。
- Applies to: ManualExpert compose 调用链、Auto/Pending/Meeting 构造点
- Violation consequence: 为了跟踪意外改变会话或收件人。
- 来源: original
### Invariant I-3: 窄范围正文清理
- Rule: stripOpenTrackingImages 只删除 img 标签：存在 data-mail-open-tracking="1" 属性，或 src 的 URI path 以 /t/mail-open/<43位base64url>.gif 结束。标签/属性大小写不敏感、支持单双/无引号和自闭合；只删除命中标签的原始 span，其他字节原样保留；无命中返回原文。不是泛化HTML清洗。
- Applies to: MailContentService helper、人工最终canonical HTML、03的wire copy
- Violation consequence: 旧像素被引用导致回复也触发旧信号，或误删正常图片。
- 来源: original
### Invariant I-4: 清理先于最终发送身份
- Rule: PendingMailOperationService 在变量渲染后、生成最终验证/SendPayload指纹/SMTP/归档前清理 HTML；后续均消费同一 finalHtmlBody。不得在指纹生成后另改人工canonical正文。
- Applies to: executeManualRichSend
- Violation consequence: 实际外发与校验/审计/幂等不一致。
- 来源: K-smtp-idempotency-reservation-before-delivery

## 现状审计
- [共享审计](mail-open-tracking-audit.md) §1、§2、§5 给出源码位置和逐点读写证据。
- DTO 写方：IntroductionMailComposer、ManualExpertMailService、AutoMailReplyService两处、PendingMailOperationService、MeetingScheduleService、无生产调用的 MeetingInvitationMailComposer；读方 SmtpMailDeliveryService。新增位在02只传递，在03才消费。
- mail_record 的既有写方/读方均见 audit §2。本阶段不增写方、不更改其字段映射；source id 只读作回复依据，不能借机修改值。
- 正文临时存储：Pending 的 canonical finalHtmlBody 供校验、SendPayload 指纹、发送和归档读取；不得改动 attempt schema 或旧历史正文。
- IP-1：业务回复分支 → ComposedMail → 03 排除；IP-2：人工用户 HTML → canonical → 校验/指纹/外发/记录。自动模板文本已经转义，不能假定人工 HTML 也被消毒。

## 实现方案
### T1 — 添加并传递内存位（I-1/2）
文件：`IntroductionMailComposer.kt`、`AutoMailReplyService.kt`、`PendingMailOperationService.kt`、`ManualExpertMailService.kt`、`MeetingScheduleService.kt`，完整路径见清单。
1. DTO 尾部加默认 false；现有 copy 自然保留。INTRODUCTION composer 仍 false，不按发送次数改值。
2. Auto 两处 ComposedMail 恒 true；Pending 共享 executeManualRichSend 恒 true，覆盖来信回信/会话回信/日历附件/通用附件。
3. Manual 的 composeMail→composeComposeTemplate 透传一个由服务端 command.sourceInboundId 计算的布尔量，最终 `isReply = hasSourceInbound || anchor != null`；anchor 即当前材料提醒已经查得的对象，不新增第二次查来信。anchor 存在但 messageId 为空也必须 true。
4. Meeting 构造 `isReply = schedule.sourceMailRecordId != null`。
5. 不新增 API isReply 参数交给浏览器声明；不改 mailType、sender、subject、headers 或 source id。

### T2 — 清理本系统像素（I-3/4）
文件：`MailContentService.kt`、`PendingMailOperationService.kt`。
- 新增独立 helper；扫描 img 起止标签时必须识别属性引号内的 `>`，不能只用 `<img[^>]*>`；仅解析识别需要的 marker/src 属性，无需引入新依赖或 DOM 重序列化。
- src 仅做识别所需的实体解码与 URI path 提取；支持 context path；路径大小写保留，base64url token 严格43位。标记匹配精确属性值1，不因正文出现标记字样误删。
- Pending 在最终 HTML 的 `.let { normalizeManualRichHtmlLineBreaks(it) }` 前接清理，后续仍用 finalHtmlBody；text 不插图、不追加 URL。
- 未知第三方/代理改写 URL 不承诺清理。本文定义的是本系统直接注入像素的去除范围，不引入邮件外链抓取。

### T3 — 用实际发送入口验证（I-1～4）
文件：清单的四个既有 service test。通过捕获交给 MailDeliveryService 的 ComposedMail 断言，不只测试一个布尔函数。
- Auto 的 QA 与会议回复，去掉 Re 前缀仍 true。
- Pending 普通/会话/带附件回复 true；清理后 canonical 与 attempt payload 一致，重复相同请求复用原尝试。
- Manual 非回复单发/批量 false；有 source true；材料 anchor 有/无 messageId 均true；无anchor false。
- Meeting 有来源true/无来源false。
- helper边界放 Pending 测试文件的独立测试类或本类用例；覆盖引号中的 `>`、属性大小写、self-closing、无命中逐字相等、相似非保留路径与正常img保留，避免另加第11个文件。

## 变更文件清单
共10文件，一个子系统：外发上下文与正文确定。

| # | 文件 |
|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/IntroductionMailComposer.kt` |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt` |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt` |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt` |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleService.kt` |
| 6 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailContentService.kt` |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt` |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt` |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailServiceTest.kt` |
| 10 | `src/test/kotlin/com/weibo/talentintroduction/campaign/service/MeetingScheduleServiceTest.kt` |

## 验收标准
- I-1：各入口真实调用覆盖上述矩阵；auto/Pending 不带主题前缀、无回复头仍 true，普通介绍 false。
- I-2：新位默认false且无数据库列/HTTP请求字段；捕获收件人、主题、头与原期望一致；copy保留。
- I-3：匹配矩阵只删除命中img；正常图片/链接/其余字节一致；同正文执行两次结果一致。
- I-4：含旧像素的人工回复，其发送/归档/SendPayload均使用清理后正文；已有幂等与附件用例全部通过。
- 命令：JDK11下 `mvn test -Dtest=AutoMailReplyServiceTest,PendingMailOperationServiceTest,ManualExpertMailServiceTest,MeetingScheduleServiceTest -DskipNodeTests=true`。

## 人工验收清单
### A-1: 真实回复标记（联03验收）
- 前置条件: 01～03部署到隔离实例，开关开启；使用自己的测试收件箱产生一封来信。
- 操作步骤: 1. 对该来信人工回信，删掉Re前缀。2. 对另两封测试来信触发QA回复与会议邀约。3. 查看所收邮件原始HTML。
- 预期结果: 三封均没有 data-mail-open-tracking 属性和 /t/mail-open/ 图片；主题、收件人与原选择相同。
- 覆盖: O-1、N-1、I-1/I-2、IP-1
### A-2: 引用像素与正常图片
- 前置条件: 测试回复编辑器粘贴一个 src 为 https://test.invalid/t/mail-open/ 后接43个a及.gif的img，再插入一张可识别的正常测试图片及文字。
- 操作步骤: 1. 发送到自己的测试邮箱。2. 查看原始HTML和系统发送历史。
- 预期结果: 保留正常图片和文字；保留路径的img被删除；系统与外发HTML均不含该img。
- 覆盖: O-2、N-2、I-3/I-4、IP-2
### A-3: 附件与重复提交
- 前置条件: 测试来信可打开人工回复；准备一份小文件和可用会议邀约。
- 操作步骤: 1. 发送含通用附件及日历的回复。2. 对同一提交重复操作。3. 再发送一封普通介绍邮件。
- 预期结果: 首次收取的文件与日历可用、重复提交不多发；介绍邮件主题和正文文字与模板预览一致。
- 覆盖: N-1/N-2、I-2/I-4、IP-2
