## Light Verification: LIGHT_PASS
Child: fast-p child 01（docs/plans/fast/mailbox-refinement/children/01/brief.md；计划：docs/plans/2026-09-09/01-mailbox-refinement-data.md）
Boundary: af25bf54df2bc70dd0fe9e3254b246a49395219c..cf257779ae420ab4c745b20aa4de6e6942a66b18（实现提交 cf257779 `feat(fast-p): implement 01`，父提交 351d69a=计划 seed；head 提交相对 351d69a 恰好 9 文件）
Verifier: Verifier01
验证环境：worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-refinement（branch fast/mailbox-refinement）；JAVA_HOME=zulu-11；隔离容器 mailbox-refinement-mysql（mysql:8.0.36, root/root, 127.0.0.1:3306），运行前探针 `SELECT @@hostname, VERSION()` = `5016f88e1b86 / 8.0.36`；IT/ControllerTest 均带 DB_URL 指向 127.0.0.1:3306/talent_introduction（容器内库）`&allowPublicKeyRetrieval=true`。

### Four Gates

| Gate | Result | Evidence |
|---|---|---|
| 1 变更文件边界 | PASS | `git diff-tree --name-only -r 351d69a..cf257779` = 9 文件，与白名单逐字相等（5 main + 4 test，含两个新增文件 MailSubjectDecoder.kt / MailSubjectDecoderTest.kt）；SqlCompat 按已知事实扩展在 #6 文件内（MailboxConversationRepositoryIT.kt 内第二类，未新建文件）。边界 af25bf5..cf257779 内另有 17 个 docs/plans/2026-09-09/** 变更，全部来自 seed 提交 351d69a（351d69a^ = af25bf5，计划/审计文档），非产品/测试代码。提交无 resources/db/migration、无 ES/静态资源改动；worktree 干净，仅未跟踪 docs/plans/fast/**（控制器另行提交的证据）。 |
| 2 需求与不变量证据 | PASS | 见下表逐条 file:line 证据。I-1..I-6 与 T1..T4 全部有实现+测试双侧直接证据；P1/P2/P3/P4 保留项有回归证据。 |
| 3 必需命令新鲜执行 | PASS | (a) `mvn test -Dtest=MailSubjectDecoderTest,ImapMailReceiveServiceTest,MailboxConversationControllerTest,MailboxConversationRepositorySqlCompatTest` → exit 0 BUILD SUCCESS；MailSubjectDecoderTest 12/0/0、ImapMailReceiveServiceTest 16/0/0、SqlCompat 2/0/0、ControllerTest 类级跳过 1（mysqlIt 门禁，既有设计）。(b) `DB_URL=…&allowPublicKeyRetrieval=true mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT`（先探针确认 3306 为容器）→ exit 0 BUILD SUCCESS；IT 21/0/0、SqlCompat 2/0/0。(c) 全量 `mvn test` → exit 0 BUILD SUCCESS；JVM Tests run 3234 / Failures 0 / Errors 0 / Skipped 9（基线 3218/9：+16 = Decoder 12 + Imap 3 + SqlCompat 1，其余新增在 mysqlIt 门禁类内，仅 -Pmysql-it 轮执行）；node tests 733 / pass 733 / fail 0（基线 733 pass 持平）。补充：`-Pmysql-it -Dtest=MailboxConversationControllerTest` → exit 0，21/0/0（T2/T3/T4 控制器用例 + waitingReply 兼容用例实库执行）。与 execution.md 记录一致，无记录性失败复现。 |
| 4 child 02 下游接口 | PASS | brief 契约逐项落地：messages items `tags: List<TagView>`（OUTBOUND 恒 []，Controller:96、Service:238；02 直读 timeline.tags 不再逐封 /thread）；summaries `expertTags: List<String>?`（null=不可得 [] =已读无标签，Controller:65、Service:275-317 语义，02:61/:190 消费 null/[] 区分）；recipientEmail/keyword trim→null、≤255、超长 400（Service:99-102 + validateTextFilter:401-405，ControllerTest:459 验证）；subject 保持只搜主题（IT:557 断言）；无任意 orderBy 参数（Controller:122-147 参数表无 orderBy），服务端排序唯一，排序语义=master R1 矩阵（orderByClause Repository:230）；waitingReply 参数+DTO 保留（Controller:126/:140、DTO:61、Service:79/114/156 旧派生，兼容用例 ControllerTest:335 实库通过；02:19 新 UI 不发送）；无 per-message /thread 依赖（tags 内联于响应）。 |

Gate 2 逐条证据：
- I-1 分页排序合同：实现 MailboxConversationRepository.kt:230-244 `orderByClause`（唯一私有片段，全部视图 pending 首项 + NULL 来信置底 + `MAX(CASE WHEN u.source='INBOUND_PROCESSING' THEN u.event_at END)` DESC + contactId DESC；关注/待处理/旧 waitingReply 省略 pending 首项），page :171 与 explain :212 同片段；latest_event_at 仅保留为旧 row 映射投影（:160 选中、:639 映射），无表字段/无新响应字段。测试：IT :496-548 四专家矩阵（全部 D,A/B,C 两页、关注 B,D,A,C、待处理 D,A、D 处理后 A,B,D,C、X2 新发件不提升 C）；SqlCompat :892-925 源码级契约（两处共用 orderByClause、禁旧 latest_event_at DESC、禁 OVER）；改造旧 IT :313（同秒游标稳定翻页无重漏）、:400（仅发件专家置底按稳定 id）。
- I-2 单消息合取与全范围聚合：实现 membership 仅在消息 EXISTS 内加 keyword（subject OR cleaned_body OR body，:539/:557）与 recipientEmail（出站 ec.expert_email :538 / 入站 impi.from_email :556），方向 OR 组整层括号后再与 q/followed AND（expertPredicates :582-605，修复审计 X1）；summary SELECT 不取正文。测试：IT :557 keyword 正文命中且聚合 receivedCount 全历史、subject 仍只搜主题；:585 出站专家邮箱/入站别名分侧匹配；:608 方向/主题/日期不得跨消息拼凑；:633 q/followed 不可被另一方向 EXISTS 绕过（真实 MySQL 证明括号回归）；:656 筛选后 pendingCount/latestInbound 仍全账号范围。
- I-3 窗口标签批量与来源：实现 MailboxConversationService.kt:205-218（仅真实 INBOUND_PROCESSING id、listTagsBatch 恰一次、空集不请求）、:238-246（OUTBOUND 恒空，绝不用 source_inbound_id/数值巧合）。测试 ControllerTest:502（20 封 1 次批量+真实 id）、:524（同数值 id 不串标签）、:564（纯 OUTBOUND 窗口 0 次调用）、:575（落库→timeline 返回 tagId→删除→消失）。
- I-4 MIME 主题：实现 MailSubjectDecoder.kt（null/普通文本不变；unfold 一次 + MimeUtility.decodeText 单次；无 HTML unescape/循环；异常回退原串不抛）；应用点 ImapMailReceiveService.kt:339（fetchEnvelopeHeaders Subject，唯一新收信差异；:299 单调用点 convertToReceivedMail=接收路径，列表读取不经过）、Service:163 latestMessage.subject、:228 timeline.subject（历史 OUTBOUND 读兼容，不 UPDATE）。测试 MailSubjectDecoderTest 12 例（UTF-8 Q 两段折叠/Windows-1252 Q/UTF-8 B/普通文本/下划线/html/未知 charset/损坏输入/round-trip/敌意输入不抛）；ImapMailReceiveServiceTest:131（ContentBlockedMessage：contentAccesses=0、streamAccesses=1=walkContent 既有行为）、:155（writeTo 往返解码）、:167（未知 charset 原样且不阻断收信）；ControllerTest:608（历史 encoded subject API 解码可读、DB 原样未改写）。
- I-5 零业务状态副作用：GET 路径仅 DB 读 + ES 画像读 + 附件元数据复用；提交无迁移/ES 写/schema 改动（9 文件清单佐证）；waitingReply 旧参数+DTO+派生保留（Controller:61/126/140、Service:79/114/156），兼容用例 ControllerTest:335 实库通过；旧用例（follow 幂等、游标绑定、附件元数据解析器、参数白名单）全部保留并在 21/0/0 轮通过。
- I-6 本页专家标签投影：实现 Service:275-317 currentPageExpertTags（页空 0 查询；本页 id findAllById 一次 :277；按合法层级分组去重每层 ≤1 次 searchByOrcidIds :280-292，全页 ≤3 层=ExpertIndexLevel 3 值；按 (level,orcidId) 回填原 SQL 行序 :296-313；tags trim/去空/去重保 ES 序不截断；画像无 tags → []；无 ORCID/层级非法/画像缺失/该层异常 → null 不伪称 []、不跨层；每层异常 catch+log 不 500 不扰分页 :284-291）。测试 ControllerTest:649（一页 20 同层 1 次批量、ES 乱序返回仍 SQL 序）、:686（跨三层恰 3 次按层 orcid + verifyNoMoreInteractions）、:724（画像缺失 null 非 []、存在画像取值）、:753（某层 ES 失败仅该组 null、页 200、计数不变）、:784（空页/无合法 level-orcid 0 查询）、:807（expertTags 与邮件 tags 完全隔离 + trim/去重保序 + summary 无 tags 泄漏）。

### AUTO_FIX (F-id list or N/A)
N/A

### RECORD_ONLY (O-id list or N/A)
N/A

### Required Action
- COMPLETE_CHILD

附注（供 controller 记录，不构成 finding）：全边界 af25bf5..cf257779 内 docs/plans/2026-09-09/** 变更属计划 seed 提交 351d69a（harness 证据），非 child 01 产物；child 01 实现提交 cf257779 相对父提交恰为 9 白名单文件。验证轮对隔离容器执行过 IT/ControllerTest（容器库 talent_introduction 为隔离数据，IT cleanup 自理），未触碰任何业务/开发库。
