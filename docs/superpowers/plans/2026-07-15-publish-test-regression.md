# Publish Test Regression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the release gate by aligning affected tests with the batch-config execution contract introduced in `3fcf41f8`.

**Architecture:** Restore the legacy material-reminder entry point's persisted daily-cap seed. Repair test doubles and assertions so they model the six-argument `TaskExecutionService.runAndRecordWithResult` call, `422` launch validation, and idempotent cron scheduling.

**Tech Stack:** Kotlin, JUnit 5, Mockito, Maven/Surefire, Java 11.

## Global Constraints

- Do not bypass tests or alter `.multi-ai-kit.yaml` build settings.
- Preserve configuration-level daily-cap accounting through `TaskExecutionService.sumSuccessCountTodayByBatchConfigId` and the legacy material-reminder count through `MailRecordRepository.countSentByMailTypeSince`.
- Preserve `422 UNPROCESSABLE_ENTITY` for invalid or unusable templates.
- Preserve idempotent scheduling when a config ID and cron expression are unchanged.

---

### Task 1: Repair auto-reply controller test callback binding

**Files:**
- Modify: `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt:83-105`
- Test: `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailAutomationControllerTest.kt`

**Interfaces:**
- Consumes: `TaskExecutionService.runAndRecordWithResult(taskType, triggerType, request, onStarted, batchConfigId, block)`.
- Produces: A test double that invokes the supplied `block` argument and records the returned auto-reply result.

- [x] **Step 1: Verify red**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=MailAutomationControllerTest test`

Expected: 12 failures; auto-reply service and final progress updates are not invoked.

- [x] **Step 2: Bind the callback from argument 5**

```kotlin
val block = invocation.getArgument<() -> Any?>(5)
val result = try { block() } catch (_: Exception) { null }
```

- [x] **Step 3: Verify green**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=MailAutomationControllerTest test`

Expected: `Tests run: 31, Failures: 0, Errors: 0`.

### Task 2: Align batch-runtime tests with configuration execution semantics

**Files:**
- Modify: `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt:1538-1700,1865-2100`
- Test: `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt`

**Interfaces:**
- Consumes: `MailRecordRepository.countSentByMailTypeSince(mailType, dayStart)` and `BatchSendScheduler` idempotent rescheduling.
- Produces: A legacy daily-cap seed plus tests that assert launch errors and scheduler behavior against the current runtime contract.

- [x] **Step 1: Verify red**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ManualInitialOutreachServiceTest test`

Expected: 8 failures: a missing legacy persisted-cap seed, obsolete `409` assertions, and an obsolete duplicate-schedule assertion.

- [x] **Step 2: Update expectations and stubs**

```kotlin
val dayStart = LocalDate.now().atStartOfDay()
val alreadySentToday = mailRecordRepository
    .countSentByMailTypeSince(BatchSendType.MATERIAL_REMINDER.name, dayStart)
    .toInt()
assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode)
```

Add a separate changed-cron case that expects a third schedule and verifies the prior future is cancelled.

- [x] **Step 3: Verify green**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ManualInitialOutreachServiceTest test`

Expected: `Tests run: 66, Failures: 0, Errors: 0`.

### Task 3: Release-gate verification

**Files:**
- Modify: no additional files.
- Test: entire Maven suite.

**Interfaces:**
- Consumes: corrected test contract from Tasks 1 and 2.
- Produces: a WAR build acceptable to `publish_feature`.

- [x] **Step 1: Run full release build**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`

Expected: `BUILD SUCCESS` and WAR at `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war`.

- [x] **Step 2: Check diff scope**

Run: `git diff --check && git status --short`

Expected: only the two targeted Kotlin test files and this plan file differ; no whitespace errors.
