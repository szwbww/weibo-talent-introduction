# Verify mail_send_attempt Simplification

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify that the mail_send_attempt simplification compiles, passes tests, and adheres to design constraints.

**Architecture:** mail_send_attempt is now a simple audit log with 3 states (PREPARED/SENT/FAILED). Anti-duplicate sending relies on checking MailRecord for existing SENT records, not on attempt state machine. Retry uses upsert (findByOrcidIdAndMailType) to avoid UNIQUE constraint violations.

**Tech Stack:** Kotlin + Spring Boot 2.7 (Java 11/zulu-11), Spring Data JDBC, Maven

---

## Design Constraints (MANDATORY - violations are bugs)

1. `MailSendAttemptStatus` has exactly 3 constants: `PREPARED`, `SENT`, `FAILED` — no other states allowed
2. No CAS operations, no `SELECT FOR UPDATE`, no optimistic locking on `mail_send_attempt`
3. Anti-duplicate sending is via `hasSentIntroduction()` checking `MailRecord`, NOT via attempt status
4. Retry uses `findByOrcidIdAndMailType` to upsert existing attempt — never INSERT a duplicate
5. No new classes or interfaces may be added
6. V23/V24 SQL migrations must NOT be modified
7. `OutreachReconcileService`, `ManualOutreachRecovery`, and all reconcile-related code must not exist
8. `ConversationStateService.transition()` is the only way to change contact status

## Fix Rules (MANDATORY)

- **Max 3 fix rounds.** If compilation or tests still fail after 3 fix attempts, STOP and report root cause analysis. Do NOT generate a 4th fix.
- **Scope limit:** Each fix may only modify the file that errored and its direct dependencies. No new files.
- **Constraint check:** Before every fix, list what you plan to change and verify it does not violate any Design Constraint above. If it would, STOP and report the conflict.

---

### Task 1: Verify Compilation

**Files:**
- Read: `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/MailSendAttemptStatus.kt`
- Read: `src/main/kotlin/com/weibo/talentintroduction/campaign/repository/MailSendAttemptRepository.kt`
- Read: `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`
- Read: `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt`
- Read: `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationController.kt`

- [ ] **Step 1: Run Maven compile**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn compile
```

Expected: BUILD SUCCESS

- [ ] **Step 2: If compilation fails, apply fix (max 3 rounds)**

Read the error, identify the file and line. Before fixing, verify the fix does not violate Design Constraints. Fix only the erroring file. Re-run compile. If still failing after 3 rounds, STOP and report.

- [ ] **Step 3: Verify no dangling references**

```bash
grep -r "OutreachReconcileService\|ManualOutreachRecovery\|DELIVERY_UNKNOWN\|RECONCILING\|SMTP_SENT_DB_PENDING\|QUOTA_PENDING\|RECONCILED_SENT\|RECONCILED_NOT_SENT" src/main/kotlin/ src/test/kotlin/ --include="*.kt"
```

Expected: No matches (V23 migration SQL comments are OK, they are in `src/main/resources/`)

---

### Task 2: Verify Tests Pass

- [ ] **Step 1: Run all tests**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 2: If tests fail, apply fix (max 3 rounds)**

Read the failure. Before fixing, verify the fix does not violate Design Constraints. Fix only the failing test or the source it tests. Re-run. If still failing after 3 rounds, STOP and report.

---

### Task 3: Verify Design Constraint Compliance

- [ ] **Step 1: Verify MailSendAttemptStatus has exactly 3 states**

Read `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/MailSendAttemptStatus.kt`

Verify it contains only: `PREPARED`, `SENT`, `FAILED` as string constants. No enum, no grouping sets, no other states.

- [ ] **Step 2: Verify upsert pattern in ManualInitialOutreachService**

Read `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt`

Search for `mailSendAttemptRepository.save`. Verify that before every save, there is a `findByOrcidIdAndMailType` call that checks for existing attempts. There must be NO bare `save(MailSendAttempt(...))` that always creates a new row.

- [ ] **Step 3: Verify anti-duplicate uses MailRecord, not attempt status**

In the same file, find `hasSentIntroduction`. Verify it checks `mailRecordRepository` for OUTBOUND INTRODUCTION with sendStatus=SENT. It must NOT check attempt status.

- [ ] **Step 4: Verify ManualOutreachTxHelper simplicity**

Read `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualOutreachTxHelper.kt`

Verify:
- Only 2 public methods: `recordSuccess` and `recordFailure`
- `recordSuccess` calls `conversationStateService.transition()` (not direct status mutation)
- No `SELECT FOR UPDATE`, no CAS, no `upsertMailRecord`

- [ ] **Step 5: Verify no reconcile code exists**

```bash
grep -r "reconcile\|Reconcile\|RECONCILE" src/main/kotlin/ src/test/kotlin/ src/main/resources/static/ --include="*.kt" --include="*.js" --include="*.html" -l
```

Expected: No matches (task-modal-runtime.js `terminalExecutionReconciled` is OK — it's UI state, not outreach reconcile)

---

### Task 4: Verify Frontend

- [ ] **Step 1: Check JS syntax**

```bash
node --check src/main/resources/static/app.js
node --check src/main/resources/static/task-modal-runtime.js
```

Expected: No output (success)

- [ ] **Step 2: Verify no reconcile UI in index.html**

```bash
grep -c "reconcileModal\|outreachReconcileLink\|formatAttemptStatusLabel" src/main/resources/static/index.html src/main/resources/static/app.js
```

Expected: All counts = 0

- [ ] **Step 3: Verify outreach progress panel has no 'unknown' counter**

Read `src/main/resources/static/app.js`, find `updateOutreachProgressPanel`. Verify there is no `unknown` field or display logic.
