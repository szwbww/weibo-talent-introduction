# 专家会议确认邮件开发总计划

状态：PLAN_READY_FOR_REVIEW；2026-09-09。依据用户已认可的交互预览及本次明确要求：会议时区可搜索、CSS/DOM完整、现状以源码为证。仅编制开发计划，尚未实施本功能。

## 需求描述

| 编号 | 可观察结果 |
|---|---|
| R1 | 人工回复的“链接”右侧出现“会议确认”；弹窗配置专家称呼、起止日期时间、可搜索时区、Zoom和签名。 |
| R2 | 专用模板生成邮件正文，日期/星期/时区由同一配置生成；预览期间可下载ICS查看。 |
| R3 | “确认并填入回复”只生成当前草稿正文和1个待发日历；支持编辑、移除、切换草稿隔离。 |
| R4 | 人工点击发送后实际邮件带ICS；历史发件可下载当时发送的原件。 |

必须保留：M1原主题与富文本/QA/RAG/安全确认；M2用户/账号/专家/来信目标隔离、失败不丢草稿；M3刚合并的收发件箱标签/翻译/列表/滚动和原材料功能；M4旧会议业务及无来信模板跟进。范围外：Zoom建会、AI猜会议时间/姓氏/职称、RSVP接受拒绝、会议状态自动流转、通用附件上传、硬刷新后草稿持久化、主题编码再改、生产发布和向真实专家发信。

## 关键不变量

### Invariant I-1: 草稿与发送边界
- Rule：预览、下载预览、确认填入均不发送、不写meeting_schedule/专家状态/发送计数；只人工发送按钮触发原manual-rich-reply。
- Applies to：01预览API、04确认、03发送入口。
- Violation consequence：误发或仅填草稿就改变业务状态。
- 来源：用户；MeetingScheduleService.confirmMeetingAndEmail:88..171代码证据。

### Invariant I-2: 时间和附件同源
- Rule：使用IANA时区与明确起止日期；服务端java.time检查DST跳时/回拨；邮件、ICS、北京时间同源；已发送记录保存原件，下载不重新渲染。
- Applies to：01生成、02投递快照、03下载、04表单绑定。
- Violation consequence：错时区/下载文件与实际发件不同。
- 来源：用户；RFC5545、Java11 ZoneRules；01 I-3/I-6。

### Invariant I-3: 附件进入原防重链
- Rule：规范会议语义加入发送指纹，无会议的旧指纹字节不变；预留认领在SMTP前，UNKNOWN拒绝自动重试；所有成功/失败存档分支覆盖快照。
- Applies to：02 ManualReplySendAttemptService、03 Pending桥接。
- Violation consequence：不同会议误去重或相同会议重复发送。
- 来源：K-manual-send-fingerprint-complete-identity、K-smtp-idempotency-reservation-before-delivery。

### Invariant I-4: 会话所有权与样式固定
- Rule：沿最新user|accountScope|contactId缓存及contact:processing:account草稿key；晚响应不串写；04 S-1..S-5是唯一视觉合同，不允许执行者重新设计页面。
- Applies to：04全部UI改动、05资源注册。
- Violation consequence：丢草稿、错目标或样式偏差。
- 来源：用户；K-state-input-no-per-keystroke-innerhtml；当前mailbox-chat源码。

### Invariant I-5: 存储与模板域不混用
- Rule：新MANUAL_MEETING_CONFIRMATION模板只供会议弹窗，不进入通用单发；仅mail_record新增calendar_attachment_json一列；不将日历作为专家申报材料，不把source_inbound_id当processingId。
- Applies to：01模板/普通单发门禁、02存档、03元数据、04附件卡。
- Violation consequence：未展开模板变量被发送、材料统计被污染或附件归属错误。
- 来源：K-mail-record-source-inbound-id、K-attachment-metadata-consumer-chain；代码D1/D4。

## 样式契约

唯一可执行的全量CSS/DOM为[04前端计划](04-meeting-confirmation-frontend.md) S-1..S-5；新增CSS全文已嵌入，不以截图代替数值。原样副本[CSS](meeting-confirmation-evidence/meeting-confirmation.target.css)、[dialog DOM](meeting-confirmation-evidence/meeting-dialog.target.html)。修改前最新源码原文见[前端基线](meeting-confirmation-evidence/frontend-before.md)。05只增加资源节点，其S-1给出完整注册行。

已确认截图用于目测：

- [时区搜索](meeting-confirmation-evidence/preview-timezone-search.jpg)
- [完整弹窗](meeting-confirmation-evidence/preview-dialog.jpg)
- [正文与附件](meeting-confirmation-evidence/preview-reply.jpg)

明确落地差异：生产表单不预填截图的日期/Zoom；姓名/签名来自真实数据；服务端生成ICS；预览正文显式13px/#334155以修正全站p污染；新界面保留最新人工编辑器100..240px；不复制预览导航/mock脚本。这些是明确合同，不授权自由微调。

## 现状审计

[完整代码审计](meeting-confirmation-audit.md) D1..D7为本总计划及子计划的共同组成部分；原始grep、schema、source-hash和前端逐字摘录在[证据目录](meeting-confirmation-evidence/)。

关键事实：生产已有手工富文本发送与尝试防重，当前没有ICS附件载体；通用模板只解析`${...}`而预览使用`{{...}}`；现有旧会议确认会直接发送并改状态；账号只有name/title/team/country无整段signature。上述事实分别落在01/02/03合同中，未用知识条目代替重新读代码。

研究期间检测到mailbox-refinement合并；已重读最新源码并保存初次/最终两套证据，最终HEAD见[source-revision.txt](meeting-confirmation-evidence/source-revision.txt)。不宣称读过生产库或部署版本。IP-1模板→预览/发送；IP-2身份→配置；IP-3配置→指纹/MIME；IP-4存档→下载；IP-5日历/材料隔离；IP-6草稿→切换/发送；IP-7dialog→搜索/键盘；IP-8资源→运行，均有A-n覆盖。

## 实现方案

严格按顺序执行，后阶段依赖前阶段，不并行修改共享文件。每个子计划独立机器验证；最后统一浏览器人工验收。

| 次序 | 子计划 | 实施文件数 | 子系统 | 独立可验证结果 |
|---|---|---:|---:|---|
| 01 | [模板、时区、只读预览API](01-meeting-confirmation-preview-api.md) | 9 | 2 | 通过真实目标生成邮件/ICS JSON；不发信 |
| 02 | [MIME、防重、邮件存档](02-meeting-confirmation-delivery.md) | 8 | 2 | 投递器/尝试服务可处理规范ICS；旧调用默认null |
| 03 | [人工发送、历史下载](03-meeting-confirmation-send-download.md) | 10 | 2 | 现有API可发送带ICS；SENT原件可下载 |
| 04 | [前端逐字样式与交互](04-meeting-confirmation-frontend.md) | 7 | 1 | 独立组件行为测试可挂载；未注册时旧UI可用 |
| 05 | [资源激活与整体检查](05-meeting-confirmation-assets.md) | 9 | 1 | 真实页面加载组件、端到端人工验收 |

拆分依据create-p：单计划不超过10文件、2独立子系统、每共享存储最多1新列。不是拆成多个独立产品；01..05完整后才交付用户可用功能。重复涉及的Flyway测试在01/02顺序更新，不能跨阶段任意编辑白名单外文件。

执行统一步骤：先核对该阶段源码哈希和定位；按该阶段I/S写有意义的测试和实现；运行该阶段列明的检查；记录实际PASS/FAIL/NOT_RUN；仅修改该阶段白名单。出现代码演进导致白名单之外改动时，先修订当前计划再执行，不顺便扩展功能。迁移V122/V123执行前核对是否占用及部署顺序，禁止改已应用版本。

## 变更文件清单

本总计划是编排文档，不另授权生产改动。精确文件白名单分别在01/02/03/04/05，数目9/8/10/7/9；每一阶段都≤10。计划文档/证据/知识写回不计作生产实施文件；本轮没有修改src业务文件、没有创建migration、没有运行SMTP。

## 验收标准

- I-1：01Controller无写入及04确认无send调用，03只有原发送入口。
- I-2：样例Istanbul10:00→UTC07:00/北京15:00；DST异常明确拒绝；preview→MIME→历史下载SHA完全相同。
- I-3：无会议golden fingerprint不变，会议语义变更分离，随机DTSTAMP不制造新发送，SENT/UNKNOWN/安全失败状态路径覆盖。
- I-4：04 CSS逐字diff+真实浏览器样式/键盘/手机；异步目标切换、QA采用、发送锁不串草稿；/talent部署前缀也验证。
- I-5：普通模板列表/直接ID拒绝新type；新增列null旧行兼容；日历不增加材料计数、不走材料下载。
- 样式：04 S-1..S-5、05 S-1全部PASS；同日改版CSS不改，导航/主题/翻译/材料功能回归。
- 构建：每阶段的unit/Node检查；整体mvn test；01/02 Docker迁移测试`-DmigrationIt=true`；03原MySQL门禁测试必须配置独立空测试库。所有跳过项单列NOT_RUN，不当作通过。

## 人工验收清单

权威详单位于各子计划A-n，当前不生成`-acceptance.md`副本。人工验收开始时从各计划本节导出，逐条记录验收人/日期/结果/截图/下载SHA；更改清单先改计划再导出。

### A-1: 完整会议路径
- 前置条件：隔离环境01..05完成机器验证，SMTP仅测试邮箱；按04统一前置准备A/B专家、真实来信及LuKai账号；所有API连接该隔离环境。
- 操作步骤：1. 依次执行01 A-1..A-3、02 A-1..A-2、03 A-1..A-2、04 A-1..A-8、05 A-1。2. 保存ICS实际下载、实际MIME、存档下载及哈希。3. 保存1440×1000与390宽截图。
- 预期结果：四个R均可操作，下载/投递/存档三份字节SHA相同；确认只填草稿，实际手工发送1封/1附件；所有样式与04逐字合同一致；共16个子计划验收场景有结果。
- 覆盖：I-1..I-5；R1..R4；IP-1..IP-8；04 S-1..S-5/05 S-1。

### A-2: 既有行为保留
- 前置条件：同A-1；B有编辑中的非会议正文/QA，C仅有发件无来信；模板管理页可启停专用模板。
- 操作步骤：1. 运行04 A-4..A-8中的原文/QA、切换、失败、无来信场景。2. 普通模板单发列表检查新type隔离。3. 打开旧会议管理，查看原记录状态。4. 比较材料数量与原下载。
- 预期结果：原主题和B/I/列表/链接可用；B草稿不被A晚响应覆盖；C仍模板跟进；旧会议状态未因草稿而变；材料数量不因ICS增加；全站标签/翻译/列表/滚动保持改版后的行为。
- 覆盖：M1..M4；I-1/I-4/I-5；IP-1/IP-5/IP-6/IP-8。
