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
Conversation status (`common/domain/ConversationStatus`) flows `NEW → INTRO_SENT → WAITING_REPLY → {QA_AUTO_REPLIED | MEETING_INVITATION_SENT → WAITING_MEETING_CONFIRMATION → MANUAL_HANDOFF} | MANUAL_REVIEW | CLOSED`. **Always transition state through `ConversationStateService.transition(...)`** — it persists the contact and appends an `ExpertContactStatusHistory` row in one place. Don't mutate `currentStatus` directly.

### Async / scheduling (both opt-in)
- **RabbitMQ** (`mail/queue`, `config/RabbitMailQueueConfig`): gated by `talent-introduction.mail-queue.enabled=true`. When enabled, automation publishes jobs (`RabbitMailQueuePublisher`) consumed by `MailQueueConsumer`; when disabled the publisher bean is absent and callers fall back to synchronous execution via `ObjectProvider.getIfAvailable()`. Preserve this fallback pattern.
- **Scheduling** (`task/service/MailAutomationScheduler`): gated by `talent-introduction.scheduling.enabled=true` with cron expressions for auto-reply-all and initial outreach. All scheduled work is wrapped by `TaskExecutionService.runAndRecord(...)` which persists a `TaskExecution` audit row.

### Configuration
Runtime config is `src/main/resources/application.yml`, all overridable via env vars. Typed config classes live in `config/` (`@ConfigurationProperties`): ES connection, candidate eligibility filters, mail-queue topology, scheduling, attachment storage. Eligibility filters (doctoral degree, age, nationality, valid email) are intentionally in config now with the plan to later move to DB-backed admin settings.

### Database migrations
Flyway runs on startup from `src/main/resources/db/migration` (`V1`..`V10`). **Schema changes must be new `V<n>__*.sql` migrations** — never edit an applied migration. Seed data (templates, QA rules) also ships as migrations.

### Frontend
A static admin UI (`src/main/resources/static/` — `index.html`, `app.js`, `styles.css`) is served by `common/controller/FrontendController` and talks to the REST controllers under `/api/*` (`/api/mail`, `/api/mail/sender-accounts`, `/api/expert-contacts`, `/api/experts`, `/api/qa`, `/api/task-executions`).

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
