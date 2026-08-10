# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

This is a Kotlin + Spring Boot 2.7 (Java 11) Maven project. **JDK 11 (zulu-11) is required** — newer JDKs will fail the build.

```bash
# Build a WAR (packaging is war; tomcat is provided-scope)
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# Run all tests
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# Run a single test class
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaMatchServiceTest

# Run a single test method
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=QaMatchServiceTest#methodName

# Run locally (needs MySQL; RabbitMQ only if mail-queue enabled)
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn spring-boot:run
```

Kotlin sources live in `src/main/kotlin` / `src/test/kotlin` (configured via the kotlin-maven-plugin, not the default Maven layout). The kotlin `spring` (all-open) compiler plugin is enabled, so Spring beans need not be marked `open`.

## Architecture

The system automates outbound recruiting of academic experts: it pulls candidates from Elasticsearch, sends a fixed introduction email, ingests replies over IMAP, auto-answers FAQs, escalates interested experts to a meeting invitation, and finally hands off to a human. `docs/design.md` is the authoritative spec for the business flow, conversation states, and data ownership — read it before changing mail/conversation logic.

### Data ownership split
- **Elasticsearch** holds layered expert profile data across three indices: Level 3 raw (`orcid_info`), Level 2 candidate (validated, eligible for first outreach), Level 1 application (replied). Promotion is explicit: L3→L2 after eligibility checks, L2→L1 after a reply. See `expert/service` (`ExpertSearchService`, `ExpertIndexService`, `ExpertIndexPromotionService`, `CandidateEligibilityService`). ES is accessed via raw `RestTemplate` calls with basic auth — there is no ES client library.
- **MySQL** is the transactional source of truth for everything stateful: sender accounts, templates, QA rules, campaigns, expert contact state, mail records, attachments, handoffs. Persistence uses **Spring Data JDBC** (not JPA/Hibernate) — domain classes are immutable Kotlin `data class`es annotated with `@Table`/`@Id`, and repositories extend `CrudRepository`. There is no lazy loading or entity graph; write explicit queries.

### Module layout (`com.weibo.talentintroduction.<module>`)
Each feature module follows `controller` / `service` / `domain` / `repository`. Modules: `expert` (ES search + index promotion), `campaign` (contacts, conversation state, outreach, meeting scheduling), `mail` (the largest — send/receive/classify/reply), `qa` (keyword rule matching), `template` (mail template rendering), `task` (execution records + scheduler), `handoff`, `document`, `common` (frontend controller, global exception handler, `ConversationStatus`), `config`.

### Mail pipeline (the core)
- **Outbound**: `InitialOutreachService` reads L2 candidates, assigns a sender account, renders the `INTRODUCTION` template (`IntroductionMailComposer` / `MailTemplateService`), sends via `SmtpMailDeliveryService`, and records contact + mail state.
- **Inbound**: `MailReceiveService` (impl `ImapMailReceiveService`) fetches unread mail → `AutoMailReplyService` cleans the body (`MailBodyCleaner`), persists attachments (`MailAttachmentService`), classifies intent (`InboundIntentClassifier`), and acts: QA auto-reply (`QaMatchService`), meeting invitation, or escalation. `BatchAutoMailReplyService` runs this across all accounts.
- **Sender account pool**: accounts have strategy weights, daily send limits, and per-day counters. `SenderAccountAssignmentService` balances expert distribution (first dimension: `country`) and `MailSenderAccountService` selects accounts to avoid hot mailboxes.

### Conversation state machine
Conversation status (`common/domain/ConversationStatus`) flows `NEW → INTRO_SENT → WAITING_REPLY → {QA_AUTO_REPLIED | MEETING_INVITATION_SENT → WAITING_MEETING_CONFIRMATION → MANUAL_HANDOFF}`. The `CLOSED` state is removed completely; historical data mapping reads `CLOSED` as `MANUAL_HANDOFF`. `MANUAL_HANDOFF` is the single "needs a human" state (with auto reply disabled) — both the inbound auto-pipeline (unclear intent, attachments, paused auto-reply, QA miss…) and operator-triggered "切换为人工回复" land on it. The operator can switch back to `WAITING_REPLY` via "切换为自动回复" which completes the handoff ticket and enables auto-reply. **Always transition state through `ConversationStateService.transition(...)`** — it persists the contact and appends an `ExpertContactStatusHistory` row in one place. Don't mutate `currentStatus` directly.

### Async / scheduling (both opt-in)
- **RabbitMQ** (`mail/queue`, `config/RabbitMailQueueConfig`): gated by `talent-introduction.mail-queue.enabled=true`. When enabled, automation publishes jobs (`RabbitMailQueuePublisher`) consumed by `MailQueueConsumer`; when disabled the publisher bean is absent and callers fall back to synchronous execution via `ObjectProvider.getIfAvailable()`. Preserve this fallback pattern.
- **Scheduling** (`task/service/MailAutomationScheduler`): gated by `talent-introduction.scheduling.enabled=true` with cron expressions for auto-reply-all and initial outreach. All scheduled work is wrapped by `TaskExecutionService.runAndRecord(...)` which persists a `TaskExecution` audit row.

### Configuration
Runtime config is `src/main/resources/application.yml`, all overridable via env vars. Typed config classes live in `config/` (`@ConfigurationProperties`): ES connection, candidate eligibility filters, mail-queue topology, scheduling, attachment storage. Eligibility filters (doctoral degree, age, nationality, valid email) are intentionally in config now with the plan to later move to DB-backed admin settings.

### Database migrations
Flyway runs on startup from `src/main/resources/db/migration` (`V1`..`V10`). **Schema changes must be new `V<n>__*.sql` migrations** — never edit an applied migration. Seed data (templates, QA rules) also ships as migrations.

### Frontend
A static admin UI (`src/main/resources/static/` — `index.html`, `app.js`, `styles.css`) is served by `common/controller/FrontendController` and talks to the REST controllers under `/api/*` (`/api/mail`, `/api/mail/sender-accounts`, `/api/expert-contacts`, `/api/experts`, `/api/qa`, `/api/task-executions`).

## 团队沉淀知识
- 人工 QA 组装路径必须让 UI 预览、payload、外发正文和审计 ordinal 共用同一 `qaRuleIds` 顺序契约，避免运营调整顺序只影响日志不影响正文。(K-composed-reply-order-contract)
- 前端邮件正文展示点分散在 app.js 多处（专家详情、收发件箱、未匹配详情、自动回复预览等），统一类名 `.pre`；任何要在「所有正文位置」统一加能力的需求须按全集逐点改，改前先 grep `class="pre"` 复核行号。(K-mail-body-display-sites)
- QA 复合覆盖规则与缺口检测共存时，缺口检测必须用覆盖前命中集计算覆盖度，或把 `supersedesChildren=true` 复合规则视为覆盖总览型多主题意图；否则概览型多问来信会被误转人工。(K-overview-gap-supersede)
- 新增前端侧栏 Tab/视图须四处同步注册：① `index.html` 侧栏 `.nav-tab[data-view]`；② `index.html` `<section class="view" id="view-<name>">`；③ `app.js` `viewMeta`；④ `app.js` `refreshCurrentView()`。缺一即切换报错。(K-view-registration-triad)
- AI 草稿链路里"给 LLM 的 QA 知识范围（promptRuleIds，可回退全集）"与"发送审计子集（sendQaRuleIds，只能是真实匹配/显式勾选）"是两个语义，进 `mail_record_qa_rule` 的只能是后者；模拟路径产物永不落审计。(K-ai-reply-prompt-vs-send-rule-ids)
- 人工富文本回复只要携带 QA 规则，就必须同时写 `mail_record_qa_rule` 并沿用 `SEND_MANUAL_COMPOSED_REPLY` 审计；纯人工空规则回复不得用 QA 全集兜底。(K-rich-reply-qa-audit-reuse)
- 多问题 AI 回复必须保留按原邮件顺序的 request→factRuleIds→groundingStatus 矩阵，生成与 fallback 共用矩阵，发送审计规则集单独维护。(K-request-facts-not-flat-pool)
- QA 使用审计的实际选用规则必须以 `mail_record_qa_rule` 为准；操作日志仅记录上下文，只有历史关联缺失时才允许回退日志字段。(K-audit-selected-source)
- QA 人工外发回复的纯文本与 HTML 渲染、风险校验、落库和审计必须共享同一 canonical `qaRuleIds`，并在 SMTP 前完成校验。(K-qa-outbound-render-seams)
- 新增 enrichment ES 字段必须只在 `ExpertDiscoveryService.updateExpertAcademicFields()` 的 doc map 显式写入，并依赖该方法对 RAW/CANDIDATE/APPLICATION 三层的按需 `_update`；晋升路径保持 `_source` 全量透传。(K-enrichment-write-three-layers)
- 回复 frame（问候、致谢、结束语）的现存消费者集中在 `AiReplyPointByPointComposer` Grounded 组装及 `AiReplyDraftService` matched/FREE_FORM prompt/fallback；改 Grounded frame 只改前者，改全局 snippet 前重新 grep 全部 `resolveManualFrame/resolveAck`。(K-manual-frame-three-consumers)
- AI 草稿生成有训练模拟、收发件箱工作台、Grounded 自动 decision 三个生产入口；跨入口 prompt/结构/claim/action gate 必须收口在 `AiReplyDraftService.generate()`，deterministic fallback 与自动 fail-closed 门禁仍是独立边界。(K-ai-generate-single-freeform-seam)
- AI 回复模型由浏览器传稳定业务枚举、由服务端映射 provider model id；回复专用扩展须使用窄 seam，禁止扩改全局 `chat/stitchDraft` 签名。(K-reply-model-stable-enum-mapping)
- AI 失败 fallback 可在“采用到编辑器”边界按当前草稿 `usedLlm/generationState` 禁用；但人工自行撰写后的最终发送不得读取历史 READY/NEEDS_REVIEW/BLOCKED、draft identity 或审计作为审批条件。(K-ai-generation-observability-not-send-gate)
- 人工采用后的最终发送只依据当前服务端事实、最终正文与当前发送上下文；不得以历史草稿、readiness、draftHash 或前端 preflight 作为发送 authority。(K-ai-adopt-direct-send-no-residual-gates)
- 若未来重新启用 AI 草稿审核发送闸门，authority 必须以服务端 current identity/readiness/canonical snapshot 为准；当前“采用后直接人工发送”不以 identity、readiness 或审计记录决定外发。(K-ai-review-server-authoritative-snapshot)
- 多请求 grounded LLM 首轮与修复只返回 exact claimKey→text/actionText；服务端 plan 持有 request/source/paragraph/missingFacts/review 元数据，绑定完整 key set 后再做 claim/trust/action policy，raw JSON 不得进入 response。(K-grounded-json-materialize-before-policy)
- Grounded 段落上限必须由服务端 plan 在组装前分组并保证每个 claim 恰好覆盖一次；composer 禁止末端 `take(N)` 或任何静默截断。(K-grounded-paragraph-cap-never-drop-claims)
- LLM JSON 协议的所有标识必须是可转换范围内的 integral number；拒绝浮点、溢出和截断后才能比对 request/rule 矩阵。(K-ai-reply-json-integral-identifiers, K-ai-reply-json-integral-range)
- `GROUNDED/PARTIAL/UNSUPPORTED` 只属于操作端审核状态；PARTIAL 只含有据事实；UNSUPPORTED 的自动/QA-grounded 路径不生成事实答案，只有显式人工说明路径可生成空 claims 的待采用版本，且不得升级为证据或自动回复；materializer 必须拒绝内部状态 token。(K-grounding-status-ui-only)
- AI 回复 loading 必须挂在稳定的 `.ai-chat-panel`，由共享 helper 在 finally 恢复遮罩和控件状态；训练模拟与收发件箱共用 requestSeq/邮件/模型快照防陈旧响应。(K-ai-reply-loading-panel)
- LLM 流式进度不得伪造完成率；前端应展示稳定阶段、provider 活动、TTL 已用比例与最近活动，并按 generationId/progressSeq 隔离陈旧事件。(K-ai-stream-progress-no-fake-percent)
- AI 聊天每个草稿条目必须自带 raw/rendered 与 `usedLlm/generationState` 采用边界；采用旧稿不可复用最后一次响应，fallback 不可采用；该状态不升级为人工最终发送审批。(K-ai-draft-review-state-per-draft)
- AI 草稿是否被编辑只能决定 raw/rendered 采用边界，不能证明缺失事实已解决；复验必须针对当前全文和服务端事实重新执行，且当前策略不得把编辑差异变成历史审核发送闸门。(K-ai-draft-edit-not-review-confirmation)
- 删除或重构跨详情调用的前端 modal/workflow helper 时，必须同步删除 reset、详情切换和全局事件绑定的引用，并用 DOM stub 覆盖这些入口，避免遗留未定义函数。(K-ai-reply-modal-helper-scope)
- 动作安全 sanitizer 无违规时必须逐字返回原文；有违规时按原始 span 删除且只清理接缝，禁止全局压缩空白破坏布局。(K-action-sanitizer-preserve-layout)
- 只读专家画像的 ORCID 缺失、允许层无文档和查询异常都必须统一产生 profile-not-found；研究请求还要标记 research-context-insufficient，且不得触发 enrichment。(K-ai-reply-profile-absence-warning)
- 采用 AI 草稿时编辑器展示 rendered 值，但未编辑路径必须保留 raw 及 text/HTML baseline；任一内容或格式改动后禁止 raw 重渲染覆盖编辑。(K-ai-preview-raw-adoption-boundary)
- AI 训练知识进入 prompt 前必须按当前 inbound 定向筛选；QA_GROUNDED/FREE_FORM 可消费，QA_MATCHED verbatim 路径禁止注入。(K-training-knowledge-injection-points)
- AI prompt 配置表只存自定义覆盖；默认生效值必须由后端 `AiPromptConfigService` 单源提供，前端不得另写默认 prompt。(K-prompt-config-effective-default)
- `CompositionSuggestResult.gapItems` 只服务建议展示与 AI 请求矩阵；自动回复可共享 tokenizer/count，但不得消费 gapItems 展示结构或 AI grounding 状态。(K-gap-items-compose-only)
- 需保留段落的外发邮件必须同时提供 plain text 与 HTML multipart；HTML 由纯文本转换或安全渲染，审计仍持久化 plain text。(K-plaintext-reply-client-reflow)
- 研究匹配必须同时具备专家画像与 `programme.scope` 审核依据；任一缺失即 UNSUPPORTED，且只读画像不得触发 enrichment。(K-research-fit-dual-evidence)
- 复合 request 必须先拆成稳定原子 intent，再按当前 request 的 QA coverage 独立取证；任一子 intent 缺证都不能把整组标为完整。(K-compound-request-coverage-intent-atomic)
- AI 审核 canonical snapshot 必须先严格校验 key/index/intent/count 的类型、格式、唯一性和数量一致性，再做确认集合比较。(K-ai-review-canonical-key-uniqueness)
- OpenAlex enrichment 已有 `OpenAlexDataSource`/`ExpertDiscoveryService`/`POST /api/expert-discovery/enrich` 基础设施，扩展 enrichment 应改造现有链路而非新建服务。(K-openalex-enrichment-existing)
- intent catalog 与回归必须以逐字原始验收 fixture 断言精确 intent；英美拼写、连字符、词序差异须边界安全 alias 匹配，语义改写不能替代原文。(K-ai-reply-intent-alias-fixture-fidelity)
- AI CTA 拦截 regex 须同时覆盖祈使句、疑问式请求和材料同义词，所有变体须经 findViolations、sanitize 与运行时最终 gate 验证。(K-ai-reply-action-cta-variant-coverage)
- 高风险 claim family 的答案与来源必须用同一边界安全 matcher 遍历同一 family；答案命中任一 alias 时，来源必须命中该 family。(K-high-risk-phrase-family-symmetric-match)
- 条件性 QA 的 modality 校验须大小写不敏感拒绝强承诺词，family short-circuit 不得绕过；同 family 仅允许普通 will/shall，不能覆盖 guaranteed/absolutely。(K-ai-reply-modality-plain-will)
- 手动/批量发送选项只来自 enabled COMPOSE_TEMPLATE；前端须用 templateCode/mailType 识别模板，预览走权威 preview endpoint，禁止用名称/ID/顺序猜测。(K-manual-send-options-sources)
- `batch_send_setting` 是旧 typed API 的 KV 兼容表，不是列式任务配置 SSOT；新任务配置须独立实体/迁移，不得与 KV 双写。(K-batch-send-setting-kv)
- dry-run/自动回复预览必须复用 `AutoMailReplyService.processSingle` 同源同序注入与分支；运行期闸门只作信息标记不隐藏内容，无法等价处须显式标注偏差。(K-preview-mirrors-pipeline)
- 面向操作端的审计事件须限制 payload 项数与长度并标记截断，只存稳定 key，不存正文或可替代正文的字段。(K-review-event-audit-payload-bounds)
- 启用 AI 审核 authority gate 时须 fail-closed 覆盖审计写失败与同秒多版本 tie-break；当前「采用后直接人工发送」不使用该 gate，生成日志不得阻断草稿或外发。(K-ai-review-authority-loss-and-order)
- `MailComposeTemplateService.renderText()` 是唯一模板变量替换点，修改须覆盖 resolveBlocks 内部调用与全部 5 个 variables 注入入口。(K-renderText-all-callers)
- `IntroductionMailComposer.compose()` 仅有 InitialOutreachService 与 ManualInitialOutreachService 两个调用方，改 variables map 时两路径自动继承。(K-introduction-compose-callers)
- Flyway 对 `qa_rule` 的 keywords/reply_body UPDATE 会覆盖运营运行时改动；关键词/正文迁移须上线前基线核对，CONCAT 带 NOT LIKE、INSERT 带 NOT EXISTS。(K-qa-rule-runtime-vs-migration-writes)
- FREE_FORM LLM 关闭/失败须有独立非空确定性兜底，禁止空 `qaRuleIds` 复用 QA_MATCHED fallback；发送审计 `qaRuleIds` 仍须为空。(K-free-form-fallback-nonempty)
- LLM 调用须由专用 HTTP client 执行 connect/read timeout，并以稳定分类区分超时/限流/网络/服务/空响应；最终 fallback 必须显式标为不可采用的内部参考，禁止静默伪装成草稿。(K-llm-timeout-fallback)
- LLM 多层超时/取消改造须显式区分单次预算、总预算、重试上限和取消提交边界，详见 `docs/knowledge/llm/K-llm-attempt-total-budget-cancel.md`。
- 人工富文本外发的 raw text/HTML 必须在每次发送时先校验占位符，再用最终 sender/contact 渲染，之后才允许 SMTP、mail_record 与审计；前端 adoption 标记不能决定此安全边界。(K-manual-rich-render-before-send)
- 改变 QA `replyBody` 出站形态须覆盖 QaMatchService/QaReplyComposer、PendingMailOperationService、LlmStitchService、MailComposeTemplateService.resolveBlocks 全集。(K-qa-replybody-outbound-sites)
- 「这封邮件用哪个发件账号」全仓有 7 个决策点，分属分发型选号（InitialOutreach / ManualInitialOutreach 首封轮与材料提醒轮）、全局选号（ManualExpertMailService / MeetingScheduleService）、线程归属（PendingMailOperationService / AutoMailReplyService 用收信账号）三类；改账号归属须逐点核对，线程归属那两处永远不跟着改。(K-sender-account-selection-sites)
- 人工发送刻意脱离每日配额与自动暂停：`selectAccountForManualSending` 走不含额度判定的 `isManualSendable`，且发送后不自增 `todaySentCount`；改配额语义须同时覆盖"选号入口"与"发送后自增"两侧，只改其一必然不一致。(K-operator-send-quota-paths)
- 面向运营的业务异常必须继承 `IllegalArgumentException` / `IllegalStateException`，否则 `GlobalExceptionHandler` 一律映射为 500 `INTERNAL_ERROR`；因 `error(...)` 也抛 `IllegalStateException`，自定义子类的 catch 分支须排在通用 `catch (e: Exception)` 之前。(K-custom-exception-http-status-mapping)

---

# 项目元信息（供 multi-ai-kit 使用）

test_command: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

build_command: JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

frontend_paths:
  - src/main/resources/static

exclude_paths:
  - target
  - .idea
  - .antigravitycli
  - docs/reviews
  - docs/plans
  - docs/multi-ai-runs

frontend_enabled: true

## Development Workflow (Superpowers)

This project uses [Superpowers](https://github.com/obra/superpowers) for agentic development. Standard workflow:

1. **Brainstorm** → Write spec via `superpowers:brainstorming`
2. **Plan** → Write plan via `superpowers:writing-plans`, save to `docs/superpowers/plans/`
3. **Execute** → Implement via `superpowers:subagent-driven-development`
4. **Verify** → Run `fix-v` skill (global skill, auto-discovered)
5. **Finish** → Merge via `superpowers:finishing-a-development-branch`

### Verification Rules

When verifying or fixing changes, always use the fix-v skill. Key rules:
- Max 3 fix rounds — after that, stop and report root cause
- Fixes limited to erroring file + direct dependencies only
- No new classes, states, or interfaces during fixes
- Every fix must be checked against the plan's design constraints before applying
- Never modify applied database migrations

### Decision Log Protocol

Fix plans accumulate over multiple verification rounds. To prevent later rounds from re-raising issues that were already decided, follow this protocol:

**Before generating a fix plan**, the verifier MUST:
1. Read the original plan.
2. Read ALL existing fix plans under `docs/plans/fix/` that reference the same original plan (match by filename or `复验对象` header).
3. Build a **decision log** from the fix plans: any section titled "不适用", "已有决策", "不做", "降级", or crossed-out (`~~`) items are **closed decisions**. These override the corresponding items in the original plan.
4. Do NOT report closed decisions as open issues. Do NOT propose tasks that contradict closed decisions.

**When outputting a fix plan**:
- If a fix plan amends an original plan requirement (e.g., drops H2, changes migration version, changes API behavior), the verifier MUST also append a `## 修正记录` section to the **original plan** file, listing each amendment with a one-line rationale and a reference to the fix plan that decided it. This ensures future verification rounds see the amendment at the source.
- Distinguish severity accurately: "original plan says X but code doesn't do X and no decision overrides it" is P1. "Original plan says X, a prior fix plan closed it, code doesn't do X" is not a finding.
- Do NOT escalate test quality preferences (recursive scanning depth, DOM sandbox fidelity, config file style) to P1 when there is no proven production defect. These are P2 at most.

**Scope discipline**:
- A fix plan for feature X must not include tasks for unrelated code hygiene (test config structure, unrelated formatting, general refactoring). Note them as observations if relevant, but do not create tasks.
- If the verifier believes a closed decision was wrong, it should note the concern as an **observation** (not a task) and flag it for human review, not unilaterally reopen it.
