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
- 新增 enrichment ES 字段必须只在 `ExpertDiscoveryService.updateExpertAcademicFields()` 的 doc map 显式写入，并依赖该方法对 RAW/CANDIDATE/APPLICATION 三层的按需 `_update`；晋升路径保持 `_source` 全量透传。(K-enrichment-write-three-layers)
- 人工回复 frame（问候、致谢、结束语）存在外发、确定性润色 fallback、前端预览三个消费者；修改时必须同源同序，且不得波及自动回复使用的 `QaReplyComposer.compose`。(K-manual-frame-three-consumers)
- AI 草稿生成只有训练模拟与收发件箱两个调用方，跨入口 prompt/约束/模型能力应收口在 `AiReplyDraftService.generate()`；QA_MATCHED verbatim 与 deterministic fallback 仍是独立边界。(K-ai-generate-single-freeform-seam)
- AI 训练知识进入 prompt 前必须按当前 inbound 定向筛选；QA_GROUNDED/FREE_FORM 可消费，QA_MATCHED verbatim 路径禁止注入。(K-training-knowledge-injection-points)
- AI prompt 配置表只存自定义覆盖；默认生效值必须由后端 `AiPromptConfigService` 单源提供，前端不得另写默认 prompt。(K-prompt-config-effective-default)
- `CompositionSuggestResult.gapItems` 只服务建议展示与 AI 请求矩阵；自动回复可共享 tokenizer/count，但不得消费 gapItems 展示结构或 AI grounding 状态。(K-gap-items-compose-only)
- 需保留段落的外发邮件必须同时提供 plain text 与 HTML multipart；HTML 由纯文本转换或安全渲染，审计仍持久化 plain text。(K-plaintext-reply-client-reflow)

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
