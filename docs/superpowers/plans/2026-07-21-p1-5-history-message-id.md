# P1-5 History Message ID Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make message-ID normalization repository-wide single-source and prove both AI entry points exclude PENDING outbound mail from final draft history.

**Architecture:** Add a module-internal mail policy containing the only normalization algorithm. `AiReplyContextBuilder` and `BounceDetector` consume it; controller integration tests capture the final `mailHistory` passed to `AiReplyDraftService`.

**Tech Stack:** Kotlin, JUnit 5, Mockito, Spring MVC tests, Maven, Node.

## Global Constraints

- Repair only P1-5.
- Normalize exactly `trim -> removeSurrounding("<", ">") -> trim`; null/blank becomes `""`.
- Preserve history eligibility, ordering, formatting, limits, continuity prompt, and fallback behavior.
- Preserve all pre-existing dirty-worktree changes.
- Use JDK 11 for Maven.

---

### Task 1: Shared message-ID policy

**Files:**
- Create: `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailMessageIdNormalizer.kt`
- Create: `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailMessageIdNormalizerTest.kt`
- Modify: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextBuilder.kt:45-49,111-114`
- Modify: `src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceDetector.kt:137-139,182-191,226-227`
- Modify: `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyContextServiceTest.kt:539-555`

**Interfaces:**
- Produces: `internal object MailMessageIdNormalizer`; `fun normalize(raw: String?): String`.
- Consumes: nullable RFC message-ID strings from history and bounce paths.

- [ ] **Step 1: Write the failing test**

```kotlin
class MailMessageIdNormalizerTest {
    @Test
    fun `normalizes brackets and whitespace`() {
        assertEquals("id@example.com", MailMessageIdNormalizer.normalize("  < id@example.com >  "))
    }

    @Test
    fun `normalizes null and blank to empty`() {
        assertEquals("", MailMessageIdNormalizer.normalize(null))
        assertEquals("", MailMessageIdNormalizer.normalize("  <>  "))
    }
}
```

- [ ] **Step 2: Run RED**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=MailMessageIdNormalizerTest test
```

Expected: test compilation fails because `MailMessageIdNormalizer` does not exist.

- [ ] **Step 3: Add minimal implementation**

```kotlin
package com.weibo.talentintroduction.mail.service

internal object MailMessageIdNormalizer {
    fun normalize(raw: String?): String =
        raw?.trim()?.removeSurrounding("<", ">")?.trim().orEmpty()
}
```

- [ ] **Step 4: Replace local algorithms**

`AiReplyContextBuilder` imports the policy and calls `MailMessageIdNormalizer.normalize` for current and record IDs; delete its companion helper. `BounceDetector` calls the same policy at all five sites; delete its helper. Existing context normalization assertions move to the policy.

- [ ] **Step 5: Run GREEN and uniqueness audit**

Run targeted policy/context/bounce tests, then:

```bash
rg -n "fun normalizeMessageId|normalizeMessageId\\(" src/main src/test
```

Expected: tests PASS; no legacy implementation/call remains.

---

### Task 2: Final-history PENDING counterexamples

**Files:**
- Modify: `src/test/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundAiReplyTurnKnowledgeTest.kt:755-828`
- Modify: `src/test/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingSimulateTest.kt:870-936`

**Interfaces:**
- Consumes: controller-generated `mailHistory`, argument 5 of `AiReplyDraftService.generate`.
- Produces: proof of old inbound/SENT retention and FAILED/PENDING/current exclusion.

- [ ] **Step 1: Extend inbox characterization fixture**

Add this record before the current inbound record:

```kotlin
record(
    4L,
    "OUTBOUND",
    "pending@example.com",
    "PENDING_OUTBOUND_EXCLUDED",
    "PENDING",
    now.minusHours(12)
)
```

Change the current inbound ID to `5L`, then add:

```kotlin
assertTrue(!capturedHistory!!.contains("PENDING_OUTBOUND_EXCLUDED"))
```

- [ ] **Step 2: Extend training characterization fixture**

Add:

```kotlin
val pendingOutbound = sentOutbound.copy(
    id = 90L,
    messageId = "train-pending@example.com",
    body = "TRAIN_PENDING_EXCLUDED",
    cleanedBody = "TRAIN_PENDING_EXCLUDED",
    sendStatus = "PENDING"
)
```

Include `pendingOutbound` in `records`, then add:

```kotlin
assertTrue(!capturedHistory!!.contains("TRAIN_PENDING_EXCLUDED"))
```

- [ ] **Step 3: Run both controller tests**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=UnmatchedInboundAiReplyTurnKnowledgeTest,AiTrainingSimulateTest test
```

Expected: PASS. Filtering already exists; these tests close missing integration evidence.

---

### Task 3: Verification

**Files:**
- Verify only; no further production changes expected.

**Interfaces:**
- Consumes: Tasks 1-2.
- Produces: P1-5 machine evidence.

- [ ] **Step 1: Run targeted suite**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn -Dtest=MailMessageIdNormalizerTest,AiReplyContextServiceTest,BounceDetectorTest,UnmatchedInboundAiReplyTurnKnowledgeTest,AiTrainingSimulateTest,AiReplyDraftServiceTest test
```

Expected: PASS, including continuity/fallback A/B.

- [ ] **Step 2: Run full JVM/embedded-JS suite**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn clean test
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Run standalone JS and static checks**

```bash
node --test src/test/js/*.test.js
rg -n "fun normalizeMessageId|normalizeMessageId\\(" src/main src/test
git diff --check
```

Expected: JS zero failures; no legacy normalizer; no whitespace errors.

- [ ] **Step 4: Review scope**

Confirm the diff contains only the design/plan documents plus listed P1-5 production/test files. Do not stage or commit unrelated pre-existing changes.
