# 07a 无草稿会话回复的来源与恢复契约

前置06完成；后置仍为07-activation，不提前开启公共入队。依据用户已批准A15「授权本次及同类契约补齐」及「批准后续所有子计划 直到可以执行review-fast-p 不要直接执行review-fast-p」，将实证遗漏的来源契约独立成十文件前置，遵守每计划≤10文件/≤2子系统，不为07发明文件数例外。完整集成与总A-1～A-12不降级。

## 需求描述

已支持的无draftRef会话跟进（含未显式指定anchor的请求）能够冻结、校验并在取消后恢复原输入；恢复后仍指向原来实际冻结的邮件，不被较新的邮件替换。复用现有编辑器，保留另一个普通草稿和当前输入。

不变：旧INBOUND快照及指纹字节、schemaVersion、冻结投递authority、权限/锁/生命周期、普通DRAFT/REVIEW行为、同步兼容入口和默认关闭开关。非目标：新编辑器/模式框架、新业务/API、DB迁移、worker算法、提前接通07或生产发布。

## 关键不变量

### Invariant C-1 — 原输入与实际来源分别持久化
- Rule — 原始conversation request保留nullable/blank accountScope、nullable explicit anchor及raw正文；canonical request UUID和实际冻结的MAIL_RECORD anchor仍由现行prepare决定。首次请求不得新增anchor或draftRef必填约束。
- Applies to — Pending准备元数据、PreparedManualReply、snapshot factory/codec。
- Violation consequence — 原有请求被拒，或恢复时重新选择较新来源。
- 来源 — 总O-1/I-4/I-6；K-manual-send-fingerprint-complete-identity。

### Invariant C-2 — 严格双来源与历史兼容
- Rule — LEGACY仅允许互斥的INBOUND/OUTBOUND。INBOUND保留positive processingId匹配payload/source，禁止conversation input；历史缺originalInput仍可解码供冻结投递，restore仍明确422。OUTBOUND的inbound ids均null、inbound originalInput为null、conversation originalInput必有且无draftRef；canonical UUID、实际anchor、账号和有序附件与冻结事实相符。未知版本/非法类型/越界/矛盾来源fail closed。
- Applies to — DelayedLegacyRestore、factory、codec、restore校验。
- Violation consequence — 用null混淆来源、旧缓存失效或原输入成为投递authority。
- 来源 — 总I-3/I-4/I-6/I-7；A7/A8兼容决定。

### Invariant C-3 — 来源锁不由请求UUID绕过
- Rule — 保留既有contact/source锁与事务顺序。INBOUND同actor/contact/processing身份规则不变；OUTBOUND以同actor/contact/frozen account/actual sourceAnchor识别未决来源。换UUID不能绕过同来源WAITING/SENDING/UNKNOWN；不同实际anchor/account不能仅因inbound=null被合并。非法未决快照不得当成合法无关来源。不同actor/origin不能接管现有job。
- Applies to — DelayedManualReplyService未决来源判断及same-identity校验。
- Violation consequence — 重复外发或无关来源被误锁。
- 来源 — 总I-2/I-3/I-6/I-10。

### Invariant C-4 — 恢复权限与真实来源权威
- Rule — 只有当前原actor/admin且FULL可恢复。OUTBOUND须核对当前real mail_record的contact/account、OUTBOUND/SENT、非模拟与冻结source/Message-ID；返回restore-only sourceKind、actual anchor、typed原输入及有序附件元数据。不得扩展公共任务/READ投影或返回路径/密码/trusted refs/dispatch token。源已变更则拒绝，不能重新挑锚点。
- Applies to — DelayedManualReplyController.restore及现有permission/lifecycle消费。
- Violation consequence — 跨专家泄露或恢复到错误线程。
- 来源 — 总I-4/I-6/I-7；A15来源响应补齐。

### Invariant C-5 — 编辑器复用而不伪造普通草稿
- Rule — OUTBOUND LEGACY恢复仅使用现有editor/attachment renderer/HTTP adapter；本地source marker与持久普通draft分开，不假造draft id，不执行普通draft save/GET/bind/autosave。普通DRAFT路径不变。当前普通稿或未保存输入有冲突时保留两份并提示，不能覆盖；取消事务不因恢复冲突回滚。
- Applies to — mailbox-chat恢复路由、编辑/保存/发送分支及生命周期。
- Violation consequence — 把LEGACY劫持成DRAFT、覆盖运营新输入。
- 来源 — 总I-6/I-9；K-mailbox-draft-cache-owner-capture。

### Invariant C-6 — 原正文和提交来源不漂移
- Rule — 保存的原始input保留null/blank；恢复后的HTTP用单独验证过的actual anchor填既有anchorMailRecordId参数，不添加trusted参数，服务端仍重验。仅把原来隐式的同一anchor显式化不得重生request UUID；真实subject/body/HTML/附件身份改变沿用既有新请求身份规则。raw/template仅在当前text和HTML基线都未变时复用；格式变化也禁止旧raw覆盖。保留附件顺序。
- Applies to — restore marker、request builder、既有conversation transport及最终prepare。
- Violation consequence — 恢复后发给较新线程，旧模板吞掉编辑，或用旧UUID发送新正文。
- 来源 — 总I-3/I-4/I-6；K-ai-preview-raw-adoption-boundary；06已关闭F06-01。

### Invariant C-7 — 异步所有权与关闭开关
- Rule — 每个异步恢复捕获user/session epoch/contact/account/target/job/revision/editor revision，await后重验。切上下文/unmount/失权使active marker失效，但不能删除另一份普通稿或擦除输入，旧回包不得重建当前编辑器。取消快照仍是持久恢复源。本前置不增加公共入队分支、不启用开关。
- Applies to — mailbox-chat异步回包/清理、Pending准备元数据。
- Violation consequence — 跨上下文写入或前置未完成即改变外发行为。
- 来源 — 总I-9/I-11；K-mailbox-draft-cache-owner-capture。

## 样式契约

### S-C1 — 不变的编辑器与附件DOM
- 复用 `mailbox-chat.js:6499–6565` 的manualComposeHtml、现有`.mc-compose[data-role="manual-compose"]`、subject input和`.mc-editor[contenteditable][data-role="mc-editor"]`；`renderDraftEditor:1359–1373`复用原renderer。只改变其数据来源，不新增/修改DOM元素、selector、class或HTML骨架。
- 附件复用 `outboundFileCardHtml:7283–7300`、`outboundDraftFilesHtml:7304–7309`、`refreshOutboundFilesCard:7437`，输入为原ready-item形态和有序服务端元数据；禁止替代卡片、占位下载地址或猜文件名。
- 不授权CSS文件、inline style、新class或既有CSS规则修改；06 S-1～S-4的footer/图标/44px/390px无溢出合同不变。原有anchor note由`manualComposeHtml:6535–6536`的既有分支呈现。

### S-C2 — 统一缓存版本
- index.html只将全部现有20处版本键（7CSS+13JS）同步改为`20261105-manual-reply-source`，保留资源集合/顺序/DOM。不得只改mailbox-chat引用。
- 反查旧key的src/test结果为空；mailboxDelayedReply现有动态统一键合同仍执行。后续如出现具体新兼容证据，先记录精确授权，不能猜测测试清单。

## 现状审计

审计者Impl07Activation，2026-11-05，产品基线`4e64c1f4da4eb5acdac282731f58f2798ab9ff5d`。完整专用grep调用与原输出、模型/transport事实：`local://07-source-contract-amendment-evidence.md`，含末尾670–692的anchor澄清；该记录只做研究，未运行检查或编辑产品。07 execution.md保留原pre-edit PLAN_CONFLICT，不重写已完成01～06证据。

- 生产者：Pending `sendConversationManualRichReply:407–527`和controller `:429–450`接受null draftRef/optional anchor；`:483`按现行规则解析actual SENT anchor，`:509–510`冻结anchor/UUID。
- 缺口：Models `DelayedLegacyRestore:191–192`和codec `:475–478`要求nonnull positive inbound；factory `:752–755`明确抛`Legacy conversation replies require a saved draft before delayed delivery`。
- 存储写链：现有Pending prepare→Prepared→snapshot factory/codec→DelayedManualReplyService→既有repository写snapshot_json。只增加恢复元数据，不新增写者/列/迁移。retry/cancel/claim/finalize维持既有事务，恢复元数据不替代冻结payload。
- 读链：codec/factory、Service未决来源与sameIdentity、Controller.restore、mailbox-chat已有LEGACY action。Worker/Pending rehydration已有实际anchor校验且仍只消费冻结payload，不改投递算法。公共query/read-only DTO不扩张。
- Service `:619–635`只比较decodeLegacyInboundId；新增OUTBOUND后null==null会错误合并。repository已有nullable inbound的contact row锁，不新增repository接口/锁形态。
- Controller `:59–67`只校验真实inbound；app.js `:232–234`只接受positive processing ID。mailbox-chat `:1104–1111`现将全部LEGACY交给该adapter。保留app.js仅做INBOUND，当前计划在mailbox-chat按显式sourceKind分流。
- 前端生产者现 `:9745`总带draftRef；旧无稿conversation HTTP仍是支持路径，其取消job会真实出现在公共timeline。故仅修factory不修恢复消费者不满足I-6。
- 原始scope/anchor与提交actual anchor澄清：`saveDraftFromInputs:7640–7656`按subject/text/HTML/anchor修改使requestId失效，`ensureOutboundRequestId:7713–7732`保留或生成UUID；send `:9730–9738`可填ordinary anchor，`:9760–9772`调用既有transport。app.js `:21835–21838`与`:22095–22111`全量透传requestBody，只追加既有安全确认，不丢弃anchor；无需改app.js。
- 样式事实来自上述原renderer，不改变其DOM/CSS。grep `\\?v=`读取index返回11–17、2547–2559共20引用；grep字面量`20261104-manual-reply-delay`搜索src/test无命中。具体回执在来源证据中，不靠假定引用数量。

## 实现方案

### T1 准备与codec补全（C-1/C-2/C-6/C-7）

仅改Pending的准备元数据：将原conversation输入携带到PreparedManualReply，保留raw字符串/可空字段；不加activation依赖、public revision参数、result字段或enabled分支。Models在既有LEGACY model尾部增加nullable `originalConversationInput: ConversationManualRichReplyRequest? = null`，将既有inboundProcessingId改nullable但不重排构造参数；不用平行HTTP DTO。

factory/codec区分互斥来源；OUTBOUND必须有原request、实际positive anchor、匹配的canonical UUID与frozen source/account。原explicit anchor为空仍合法；非空时匹配actual anchor。原input不得含draftRef或覆盖冻结投递事实；正文/附件与原restore字段及有序快照一致，完整应用现有长度/type/integral/range验证。schemaVersion不变，历史INBOUND可投递/不可伪造恢复政策不变。保持既有fingerprint fullHex/shortKey字节。

### T2 来源识别和restore响应（C-2/C-3/C-4）

Service仅补全既有LEGACY来源谓词和sameIdentity，保留source锁/事务顺序/FAILED和CANCELLED语义；按actual source而非UUID或null识别同源未决任务，坏快照fail closed。

Controller在原权限后按真实INBOUND/OUTBOUND分支校验当前来源，增加restore-only sourceKind、actual anchor和typed原conversation input/有序附件元数据；旧INBOUND字段兼容，公共任务投影不变。不得在恢复时enrichment、重建已批准正文或更换来源。

### T3 既有编辑器的窄来源分流（C-5/C-6/C-7/S-C1/S-C2）

mailbox-chat保留INBOUND host adapter；OUTBOUND恢复到既有manual-compose renderer，使用与普通持久draft区分的本地source marker。对该marker跳过saved-draft-only save/GET/bind/autosave，正常send走既有conversation adapter、无draftRef、携当前CANCELLED revision。保留originalInput，同时用响应actual anchor明确提交；同一implicit anchor的显式化不换UUID，真实内容身份改变才沿用既有新输入规则。

完整捕获/复验owner/target/epoch/revision，先处理待保存普通稿，冲突保持双方而非覆盖。原raw仅在text/HTML都未修改时复用；格式编辑、附件变更不可回滚。仅复用原renderer/transport，无新CSS/DOM设计。index统一新缓存键。

### T4 直接回归和真实运行证据（C-1～C-7/S-C1/S-C2）

SnapshotTest补历史和新来源roundtrip/type/range/互斥/指纹不变；ControllerTest补真实当前OUTBOUND来源/权限/脱敏/历史INBOUND；LifecycleIT用真实MySQL事务覆盖同anchor换UUID不能绕锁、不同anchor/account不被null合并、actor隔离和坏快照fail closed；mailboxDelayedReply覆盖真实consumer恢复→builder→transport、原输入冲突/迟到响应、raw文本/格式编辑、附件顺序和显式原anchor。

所有改动完成后执行必跑命令，并用隔离真实Spring/MySQL/SMTP和实际浏览器证明新增恢复消费者。可用既有临时fixture调用真实准备/factory/03生命周期生成任务；不得伪造API/fetch或开启07公共入队。未完成的完整public-enqueue/重启/60秒矩阵仍由07执行，不能把本前置component证据改称全链路通过。

## 变更文件清单

|#|文件|授权边界|
|---|---|---|
|1|src/main/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationService.kt|仅原conversation输入准备元数据；不激活入口|
|2|src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt|既有LEGACY双来源、factory/codec兼容|
|3|src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyService.kt|既有来源谓词/sameIdentity；不改生命周期|
|4|src/main/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyController.kt|restore-only来源响应及直接验证|
|5|src/main/resources/static/mailbox-chat.js|既有editor/transport窄来源分流|
|6|src/main/resources/static/index.html|全部现有版本键同步替换，无其他DOM修改|
|7|src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplySnapshotTest.kt|来源/兼容/指纹直接回归|
|8|src/test/kotlin/com/weibo/talentintroduction/mail/controller/DelayedManualReplyControllerTest.kt|restore来源/权限直接回归|
|9|src/test/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyLifecycleIT.kt|真实来源锁和身份回归|
|10|src/test/js/mailboxDelayedReply.test.js|恢复consumer/编辑/冲突/资源合同|

两个相依子系统：服务端原来源/恢复契约、同契约的前端消费者。所有既有共享表新增列为零；无新永久产品文件。未列文件一律不授权，缺口先记录证据再走既有批准边界。

## 验收标准

- C-1/C-2：有/无explicit anchor、null/blank scope保留原值；实际冻结来源与raw输入分开；旧INBOUND json仍解码且unknown版本拒绝；原缺完整输入restore422；错误混合来源拒绝；不新增敏感字段；旧fullHex/shortKey不变。
- C-3：真实MySQL同actor/contact/account/anchor换UUID仍冲突；不同actual anchor/account不因null冲突；跨actor/origin不可接管；非法未决snapshot fail closed。
- C-4：实际SENT OUTBOUND与冻结来源一致才可恢复；删源/换contact/account/Message-ID拒绝；READ/非owner无编辑/附件数据；公共任务DTO字段不增加。
- C-5/C-7：取消后restore不产生普通draft写入、不覆盖新版本/本地输入；switch/unmount/失权使迟到响应无效，既有普通draft读写继续工作。
- C-6：恢复后插入较新SENT邮件，提交仍显式指向旧actual anchor，未修改UUID不变；真实编辑重建请求身份；text/format-only改动都发送当前内容，顺序附件仍一致。
- S-C1/S-C2：无新增DOM/CSS设计；实际1440/390旧editor可操作无横向溢出；全部20引用保留并同键；focused/fullJS与语法实际通过。
- 必跑（JDK11，隔离MySQL；写明执行数/跳过数/失败日志，不能用旧结果替代）：
  1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipTests test-compile`
  2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DelayedManualReplySnapshotTest,DelayedManualReplyControllerTest,DelayedManualReplyWorkerTest,PendingMailOperationServiceTest`
  3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DelayedManualReplyLifecycleIT -DmysqlIt=true`
  4. `node --test src/test/js/mailboxDelayedReply.test.js`
  5. `node --test src/test/js/*.test.js`
  6. `node --check src/main/resources/static/mailbox-chat.js`

## 人工验收清单

### A-C1 无稿跟进恢复原线程
- 前置条件 — 隔离MySQL/SMTP，admin，一封真实SENT出站；通过既有fixture从无draftRef且原explicit anchor为空的真实准备结果冻结该来源并调用03入队/取消。
- 操作步骤 — 刷新页面；在原任务后再插入较新SENT邮件；从取消卡片恢复并查看原输入，捕获正常提交参数。
- 预期结果 — 原input的optional字段不变；restore-only actual anchor指向第一封；既有编辑器出现原正文/顺序附件；提交无draftRef且显式旧anchor/当前revision，未改内容UUID不变。无伪普通稿写入；当前公共取消任务仍受已有同步guard，真正重排交07验证。
- 覆盖 — C-1/C-2/C-4/C-5/C-6，总O-1/I-6。

### A-C2 新稿冲突与编辑边界
- 前置条件 — 同一取消任务、另一个浏览器已有更高版本普通稿或本地新输入。
- 操作步骤 — 尝试恢复，观察冲突；在无冲突独立目标恢复后分别只改文字、只改格式；切换账号/专家并触发迟到响应。
- 预期结果 — 既有稿不被覆盖，取消快照仍可读取；编辑后的请求不复用旧raw，真实内容改变按旧规则使用新请求身份；迟到回包不改当前编辑器。普通draft autosave未退化。
- 覆盖 — C-5/C-6/C-7，总I-6/I-9与06 F06-01。

### A-C3 权限、来源和旧路径
- 前置条件 — admin、原owner/FULL、READ，旧INBOUND快照，源已删除/账号改变的OUTBOUND快照。
- 操作步骤 — 分别请求恢复；正常旧INBOUND恢复；在1440/390查看新来源使用的同一editor和附件。
- 预期结果 — READ/非owner拒绝且无私有恢复数据；变更来源拒绝而非换源；旧INBOUND仍走原详情adapter；原renderer/附件顺序和无横向溢出保持，未启用新入队。
- 覆盖 — C-2/C-4/C-7/S-C1/S-C2。

## 修正记录

- A23/A24（适用用户A15同类契约补齐及后续子计划连续执行授权）：07实现前发现既有支持的无稿OUTBOUND无法表示；按create-p文件上限拆出本十文件前置，再顺序执行原七文件07-activation，不修改已完成01～06计划/日志，不降低总验收，不直接执行review-fast-p。来源/transport证据由Impl07Activation提供，顶层拆分与约束由controller确定；本计划记录的是待执行合同，不是验证结果。
