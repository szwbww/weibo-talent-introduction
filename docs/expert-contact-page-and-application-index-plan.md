# Expert Contact Page and Application Index Improvement Plan

## Background

The expert contact page currently mixes three concepts:

1. Elasticsearch expert index level:
   - raw expert data.
   - initially filtered candidates.
   - effective experts who have replied and entered active follow-up.
2. Conversation stage:
   - waiting for reply.
   - meeting scheduling.
   - materials review.
   - application preparation.
   - closed.
3. Reply handling mode:
   - automatic reply allowed.
   - automatic reply paused because an operator has taken over.

The current implementation exposes part of these concepts, but the boundaries are incomplete. The page shows manual handoff state, while the inbound mail processor does not treat manual handoff or closed state as a hard gate. The application-level Elasticsearch index is configured but has not been created or written.

This document defines the next implementation plan. It focuses on the expert contact page and the candidate-to-application promotion flow. Later historical-mail roadmap phases such as expanded QA rules, full document management and generated operator tasks remain deferred unless explicitly pulled into this scope.

## Current Automatic Reply Flow

Automatic reply processing can be triggered in three ways:

1. Scheduled polling through `MailAutomationScheduler`.
2. Manual HTTP calls:
   - `POST /api/mail/auto-reply`
   - `POST /api/mail/auto-reply/all`
3. RabbitMQ dispatch when the mail queue is enabled.

For each unread inbound message, the current flow is:

```text
Fetch unread messages from IMAP
-> deduplicate by sender account and IMAP UID
-> match contact by primary email or alias
-> verify that an introduction email was sent
-> clean quoted history from the inbound body
-> persist inbound mail and attachments
-> classify inbound intent
-> route to automatic reply, manual review, meeting invitation or close
```

Current routing behavior:

| Inbound condition | Current result |
| --- | --- |
| Contact cannot be matched | Add to unmatched inbound mail queue |
| Contact has no introduction mail record | Manual review |
| Expert expresses interest | Send meeting invitation and move to `MEETING_SCHEDULING` |
| Expert supplies meeting time or requests a meeting | Create meeting schedule record and require manual review |
| Expert sends documents | Save attachments and require manual review |
| Expert asks a risky question | Require manual review |
| Expert asks a normal question with a safe QA rule match | Send QA reply automatically |
| Expert explicitly declines | Move contact to `CLOSED` |
| No safe QA rule matches | Require manual review |

## Confirmed Problems

### Problem 1: Manual Handoff Does Not Really Pause Automatic Reply

The page provides:

- `转人工`
- `完成人工`

Creating a manual handoff updates:

- `current_status = MANUAL_HANDOFF`
- `manual_handoff_required = true`

Completing a manual handoff updates:

- `manual_handoff_required = false`
- `current_status = WAITING_REPLY`

However, `AutoMailReplyService.receiveAndAutoReply` does not check:

- `manual_handoff_required`
- `current_status == CLOSED`
- a dedicated contact-level automatic reply flag

This means the page semantics and runtime behavior are inconsistent:

- A contact marked as manual handoff can still receive an automatic reply.
- A closed contact can still be processed again when a new email arrives.
- There is no explicit action named `恢复自动回复`.

### Problem 2: Completing Manual Handoff Always Moves to `WAITING_REPLY`

The page currently submits:

```json
{
  "nextStatus": "WAITING_REPLY",
  "note": "Completed from console"
}
```

This is too coarse. `WAITING_REPLY` is suitable when an operator has replied and is waiting for another expert email, but it is not the correct next stage for every case.

Examples:

| Manual work completed | Suggested next stage |
| --- | --- |
| Operator answered a question | `WAITING_REPLY` |
| Operator sent a follow-up mail | `WAITING_REPLY` |
| Meeting details finalized | `MEETING_SCHEDULED` |
| Meeting completed | `MEETING_DONE` |
| Partial documents reviewed | `MATERIALS_PARTIAL` |
| All required documents accepted | `MATERIALS_RECEIVED` |
| Expert confirmed enterprise match | `COMPANY_MATCHED` |
| Expert explicitly declined | `CLOSED` |

The page should require the operator to select the next stage when completing manual work.

### Problem 3: Closing a Contact Fails to Deserialize the JSON Request

The page sends the correct payload:

```json
{
  "reason": "Closed from console"
}
```

The backend receives a Kotlin request object:

```kotlin
data class ExpertContactCloseRequest(
    val reason: String
)
```

The project currently does not declare `jackson-module-kotlin`. Jackson therefore cannot reliably construct Kotlin request data classes from JSON object bodies. The close endpoint is the confirmed failure point, but the same risk applies to other JSON request DTOs.

## Target Model

### Separate Index Level, Conversation Stage and Reply Mode

The expert contact page must display these separately:

| Concept | Example values | Ownership |
| --- | --- | --- |
| Elasticsearch index level | `RAW`, `CANDIDATE`, `APPLICATION` | Elasticsearch |
| Conversation stage | `WAITING_REPLY`, `MEETING_SCHEDULING`, `MATERIALS_PARTIAL`, `CLOSED` | MySQL `expert_contact.current_status` |
| Automatic reply mode | enabled, paused | MySQL contact-level flag |
| Manual work state | pending, assigned, completed | MySQL `manual_handoff` |

Do not infer reply mode only from `current_status`. A contact may be in `MATERIALS_PARTIAL` while automatic replies are paused, or in `WAITING_REPLY` while automatic replies are enabled.

### Contact-Level Automatic Reply Flag

Add a MySQL field:

```text
expert_contact.auto_reply_enabled BOOLEAN NOT NULL DEFAULT TRUE
```

Rules:

1. New outreach contacts start with automatic reply enabled.
2. `转人工` sets `auto_reply_enabled = false`.
3. Automatically generated manual review sets `auto_reply_enabled = false`.
4. `恢复自动回复` sets `auto_reply_enabled = true`.
5. `关闭` sets:
   - `current_status = CLOSED`
   - `auto_reply_enabled = false`
   - `manual_handoff_required = false`
6. Completing manual work does not silently enable automatic reply. The operator explicitly chooses whether to resume it.

### Automatic Reply Hard Gates

Before automatic classification and reply, the inbound processor must evaluate:

```text
if contact.currentStatus == CLOSED:
    persist inbound mail for audit
    do not reply automatically
    record processing reason CONTACT_CLOSED

if !contact.autoReplyEnabled:
    persist inbound mail for audit
    do not reply automatically
    create or reuse manual work record
    record processing reason AUTO_REPLY_PAUSED

otherwise:
    continue normal automatic reply flow
```

## Elasticsearch Layered Index Plan

### Current Index Configuration

| Layer | Default index name | Intended purpose | Current implementation state |
| --- | --- | --- | --- |
| Raw | `orcid_info` | Original collected expert data | Existing source index, searchable |
| Candidate | `orcid_info_candidate` | Initially validated experts eligible for outreach | Searchable, but no raw-to-candidate write job exists in this repository |
| Application | `orcid_info_application` | Effective experts with valid replies and active follow-up | Name is configured only; index creation and writes are missing |

MySQL remains the transactional source of truth for campaign state, email records and operator actions. Elasticsearch remains a layered search and display projection.

### Candidate-to-Application Promotion Rule

Do not promote every inbound email blindly. Promote a candidate after a meaningful matched reply:

```text
Candidate expert
-> introduction mail has been sent
-> inbound mail is received
-> inbound sender matches an expert contact by primary email or alias
-> inbound mail is persisted and classified
-> intent is not NOT_INTERESTED
-> message is not an unmatched or invalid inbound record
-> upsert expert into application index
```

Additional rules:

1. `NOT_INTERESTED` does not create a new application index document.
2. If an already-promoted expert later declines, keep the document and set `applicationStatus = CLOSED`.
3. Manual binding from the unmatched mail queue should allow the operator to promote the expert after confirming the match.
4. Repeated inbound replies must update the existing document instead of creating duplicates.
5. Use `orcidId` as the Elasticsearch document ID.

### Application Index Document

The application index should copy candidate profile fields and add business projection fields:

```json
{
  "orcidId": "0000-0000-0000-0000",
  "email": "expert@example.com",
  "givenNames": "Ada",
  "familyNames": "Lovelace",
  "country": "United States",
  "keyword": "computer science",
  "employment": "University",
  "age": 45,
  "degree": "PhD",
  "nationality": "United States",

  "expertContactId": 123,
  "campaignId": 10,
  "applicationStatus": "ACTIVE",
  "firstReplyAt": "2026-06-02T13:00:00+08:00",
  "lastReplyAt": "2026-06-02T13:00:00+08:00",
  "currentConversationStatus": "MEETING_SCHEDULING",
  "autoReplyEnabled": true,
  "promotionSource": "INBOUND_REPLY",
  "applicationPromotedAt": "2026-06-02T13:00:00+08:00",
  "updatedAt": "2026-06-02T13:00:00+08:00"
}
```

### Application Index Mapping

Create `orcid_info_application` explicitly with stable mappings:

| Field | Elasticsearch type |
| --- | --- |
| `orcidId` | `keyword` |
| `email` | `keyword` |
| `givenNames`, `familyNames`, `employment` | `text` with keyword sub-field where useful |
| `country`, `nationality` | `keyword` |
| `keyword` | `text` |
| `age`, `expertContactId`, `campaignId` | numeric |
| `degree` | `keyword` |
| `applicationStatus`, `currentConversationStatus`, `promotionSource` | `keyword` |
| `autoReplyEnabled` | `boolean` |
| `firstReplyAt`, `lastReplyAt`, `applicationPromotedAt`, `updatedAt` | `date` |

### Elasticsearch Write Service

Add `ExpertIndexWriterService` with:

1. `ensureApplicationIndex()`
2. `upsertApplicationExpert(...)`
3. `syncApplicationStatus(...)`
4. `markApplicationClosed(...)`
5. `removeApplicationExpert(...)`
6. `bulkPromoteApplications(...)`

Use Elasticsearch upsert semantics. A write failure must not roll back inbound email persistence or SMTP processing. Record the failure for retry and operator visibility.

### Candidate-to-Application Integration Points

Integrate writes at:

1. `AutoMailReplyService`
   - Promote after a valid matched inbound reply has been persisted and classified.
   - Skip initial promotion for `NOT_INTERESTED`.
2. `UnmatchedInboundMailService`
   - After manual binding, provide an explicit promotion option.
3. `ConversationStateService`
   - Sync `currentConversationStatus`, `autoReplyEnabled` and `updatedAt` for already-promoted experts.
4. Close action
   - Mark an existing application document as `CLOSED`.

### Raw-to-Candidate Follow-Up Scope

The repository also lacks the raw-to-candidate write process. Keep this as a separate follow-up implementation:

```text
Scan raw index
-> parse ExpertProfile
-> evaluate CandidateEligibilityService
-> bulk upsert eligible profiles into candidate index
-> record candidateValidatedAt
-> preserve rejection reason for audit and tuning
```

The first delivery should prioritize candidate-to-application promotion because it directly affects active expert contact management.

## Expert Contact Page Improvement Plan

### Header Summary

Show these fields prominently:

1. Expert name, email and ORCID.
2. Index level:
   - `筛选层`
   - `有效层`
3. Conversation stage:
   - Chinese label for `current_status`.
4. Automatic reply mode:
   - `自动回复已开启`
   - `自动回复已暂停`
5. Manual work state:
   - no pending manual work.
   - pending.
   - assigned operator.
   - completed.

### Action Buttons

Display actions conditionally:

| Condition | Available actions |
| --- | --- |
| Automatic reply enabled and contact not closed | `转人工` |
| Automatic reply paused and contact not closed | `恢复自动回复` |
| Pending or assigned manual work exists | `完成人工` |
| Contact not closed | `关闭` |
| Contact closed | `重新打开` only if business allows it |

Do not always display both `转人工` and `完成人工`.

### Complete Manual Work Dialog

Replace the current fixed `WAITING_REPLY` submission with a dialog:

1. Completion note.
2. Required next conversation stage.
3. Checkbox:
   - `完成后恢复自动回复`
4. Optional operator name.

Default next stages may depend on the manual handoff reason:

| Manual handoff reason | Suggested default next stage |
| --- | --- |
| `QA_MANUAL_REVIEW` | `WAITING_REPLY` |
| `HANDLE_RISKY_QUESTION` | `WAITING_REPLY` |
| `CONFIRM_MEETING` | `MEETING_SCHEDULED` |
| `REVIEW_DOCUMENT` | `MATERIALS_PARTIAL` |
| Unmatched inbound binding | retain current stage until operator confirms |

### Closed Contact Behavior

When closing a contact:

1. Require a close reason.
2. Set:
   - `current_status = CLOSED`
   - `auto_reply_enabled = false`
   - `manual_handoff_required = false`
3. Keep all mail and status history.
4. Persist future inbound mail for audit without sending automatic replies.
5. Mark an existing application index document as `CLOSED`.
6. Hide normal contact actions and show closed reason.

### Index-Level Display

For contacts in the candidate layer:

- Show `筛选层`.
- After valid inbound reply promotion, refresh the page and show `有效层`.

For manually bound unmatched emails:

- Show whether the contact is already in the effective layer.
- Provide `加入有效层` when operator confirmation is required.

## Backend Work Items

### Phase 1: Fix Existing Contact Page Bugs

1. Add `jackson-module-kotlin`.
2. Add MVC tests for JSON request DTO deserialization:
   - close contact.
   - create manual handoff.
   - complete manual handoff.
3. Add `auto_reply_enabled` to `expert_contact`.
4. Add explicit pause and resume endpoints.
5. Apply automatic reply hard gates for paused and closed contacts.
6. Add tests proving paused and closed contacts do not receive automatic replies.

### Phase 2: Improve Manual Completion Semantics

1. Extend manual handoff completion request:

```json
{
  "nextStatus": "WAITING_REPLY",
  "resumeAutoReply": true,
  "note": "Operator answered the question"
}
```

2. Require an explicit next status.
3. Add suggested stage defaults in the UI.
4. Display reply mode independently from stage.
5. Conditionally display action buttons.

### Phase 3: Build the Application Index

1. Add application index mapping.
2. Add application index creation command or startup-safe initializer.
3. Implement `ExpertIndexWriterService`.
4. Add candidate-to-application promotion on valid matched inbound reply.
5. Add status synchronization after contact state changes.
6. Add close synchronization.
7. Add retry visibility for Elasticsearch write failures.

### Phase 4: Add Manual Promotion Support

1. Extend unmatched inbound binding flow with optional application promotion.
2. Add `加入有效层` action on the contact page where appropriate.
3. Add audit information:
   - promotion source.
   - operator.
   - promotion time.
4. Add tests for idempotent repeated promotion.

### Phase 5: Plan Raw-to-Candidate Batch Filtering

1. Add candidate index mapping if not already managed externally.
2. Add a batch scan and bulk upsert service.
3. Persist or expose rejection reasons.
4. Add an admin-triggered endpoint or scheduled task.
5. Add page-level visibility for candidate filtering metrics.

## API Plan

### Contact Reply Mode

```text
POST /api/expert-contacts/{contactId}/auto-reply/pause
POST /api/expert-contacts/{contactId}/auto-reply/resume
```

Pause request:

```json
{
  "reason": "Needs operator review",
  "assignedTo": "operator-name",
  "note": "Optional note"
}
```

Resume request:

```json
{
  "nextStatus": "WAITING_REPLY",
  "note": "Operator completed manual handling"
}
```

### Complete Manual Work

```text
POST /api/expert-contacts/{contactId}/manual-handoff/complete
```

```json
{
  "nextStatus": "WAITING_REPLY",
  "resumeAutoReply": true,
  "note": "Operator completed manual handling"
}
```

### Application Index Promotion

```text
POST /api/expert-contacts/{contactId}/application-index/promote
POST /api/expert-contacts/{contactId}/application-index/remove
```

Promotion request:

```json
{
  "source": "MANUAL_CONFIRMATION",
  "operator": "operator-name",
  "note": "Confirmed after unmatched inbound binding"
}
```

## Test Plan

### Backend

1. JSON deserialization:
   - close request succeeds.
   - manual handoff create request succeeds.
   - manual handoff complete request succeeds.
2. Reply mode:
   - manual handoff pauses automatic reply.
   - paused contacts persist inbound mail without replying.
   - resume action restores automatic reply eligibility.
   - closed contacts persist inbound mail without replying.
3. Manual completion:
   - selected next stage is persisted.
   - resume checkbox controls `auto_reply_enabled`.
4. Application index:
   - valid reply promotes candidate by `orcidId`.
   - repeated reply performs idempotent upsert.
   - explicit rejection skips initial promotion.
   - close marks existing application document as `CLOSED`.
   - ES failure does not lose inbound mail.
5. Manual promotion:
   - unmatched binding can promote after confirmation.
   - repeated manual promotion remains idempotent.

### Frontend

1. `node --check src/main/resources/static/app.js`
2. Contact page visibly separates:
   - index level.
   - conversation stage.
   - automatic reply mode.
   - manual work state.
3. Action buttons appear only when valid for the current state.
4. Complete-manual dialog requires next-stage selection.
5. Close action succeeds and refreshes the detail page correctly.
6. Candidate contact visibly becomes effective after application-index promotion.

### Full Regression

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --check src/main/resources/static/app.js
```

## Acceptance Criteria

The scope is complete when:

1. The page clearly distinguishes index level, conversation stage and automatic reply mode.
2. `转人工` reliably pauses automatic replies for that expert.
3. `恢复自动回复` explicitly restores automatic reply eligibility.
4. Completing manual work requires the operator to select the next stage.
5. Closing a contact succeeds without JSON parsing errors.
6. Closed contacts never receive automatic replies.
7. `orcid_info_application` exists with explicit mappings.
8. A valid matched inbound reply promotes the candidate into the application index.
9. Repeated replies update the existing application document by `orcidId`.
10. Application index failures are visible and retryable without losing inbound email records.
11. Existing backend tests and frontend syntax checks pass.

