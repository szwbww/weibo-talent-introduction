# Candidate Auto Reply Monitoring Plan

## Background

The system is preparing to enable automatic outreach and reply handling for experts in the candidate Elasticsearch layer.

Before enabling the workflow at scale, operators need a fast way to answer:

1. How many introduction emails were sent today?
2. Which experts replied today?
3. Which inbound emails received an automatic reply?
4. Which reply was sent to each expert?
5. Which inbound emails were routed to manual review instead of receiving an automatic reply?
6. Are any sender accounts, scheduled jobs or reply flows failing?

The current system stores most individual mail records, but it does not provide an operational monitoring page or daily aggregate APIs. The existing task execution page only shows scheduled-task execution records. It is not sufficient for daily mail operations.

This document defines the monitoring plan required before candidate-layer automatic reply is enabled in production.

## Scope

This plan covers:

1. Daily outreach and reply metrics.
2. Searchable mail activity details.
3. Expert-level reply visibility.
4. Automatic reply and manual-review visibility.
5. Sender-account health visibility.
6. Basic alerts and rollout safeguards.

This plan does not replace:

- the expert contact page improvement plan.
- the candidate-to-application index promotion plan.
- the later full operator task-generation phase.

## Existing Data That Can Be Reused

### Mail Records

The existing `mail_record` table already stores:

| Field | Monitoring use |
| --- | --- |
| `expert_contact_id` | Join mail activity to the expert |
| `direction` | Distinguish inbound and outbound messages |
| `mail_type` | Distinguish introduction, QA reply and meeting invitation |
| `message_id` | Identify the sent or received email |
| `in_reply_to` | Link reply threads where available |
| `subject` | Display mail subject |
| `body` | Display sent or received content |
| `cleaned_body` | Display the latest inbound expert content |
| `matched_qa_rule_id` | Show which QA rule generated an automatic reply |
| `send_status` | Show outbound delivery result |
| `received_at` | Daily inbound statistics |
| `sent_at` | Daily outbound statistics |
| `created_at` | Audit fallback timestamp |

### Contact Records

The existing `expert_contact` table provides:

| Field | Monitoring use |
| --- | --- |
| `id` | Mail-record relation |
| `campaign_id` | Filter activity by campaign |
| `orcid_id` | Expert identity |
| `expert_email` | Recipient identity |
| `expert_name` | Operator-readable display |
| `current_status` | Current conversation stage |
| `manual_handoff_required` | Manual-review status |
| `last_mail_at` | Last outbound activity |
| `last_reply_at` | Last inbound activity |

### Sender Accounts

The existing `mail_sender_account` table provides:

| Field | Monitoring use |
| --- | --- |
| `account_code` | Sender account identifier |
| `sender_email` | Display sender mailbox |
| `today_sent_count` | Current account-pool usage snapshot |
| `daily_send_limit` | Daily safety threshold |
| `last_sent_at` | Last successful send |
| `enabled` | Account availability |

### Task Executions

The existing `task_execution` table provides scheduled polling and queue-consumption evidence:

- start time.
- finish time.
- status.
- success count.
- failure count.
- error message.

Keep this as low-level execution evidence. Add a separate business monitoring page for mail activity.

## Monitoring Goals

### Goal 1: Daily Summary in Under 10 Seconds

The first screen should answer:

```text
Today introduction emails sent: 120
Today inbound replies received: 18
Today experts who replied: 15
Today automatic replies sent: 11
Today meeting invitations sent: 4
Today manual-review messages: 3
Today failed outbound emails: 0
```

Operators should not need to inspect task execution JSON or individual contacts to obtain this summary.

### Goal 2: Trace Every Automatic Reply

For every automatic reply, operators must be able to see:

1. Expert name, ORCID and email.
2. Inbound subject and cleaned inbound content.
3. Classified intent.
4. Matched QA rule where applicable.
5. Outbound mail type, subject and body.
6. Sender account.
7. Send status and send time.
8. Current contact stage.
9. Whether the expert was promoted from candidate layer to application layer.

### Goal 3: Find Messages That Need Attention

Operators need a short exception list:

1. Unmatched inbound emails.
2. Manual-review messages.
3. Paused-contact inbound messages.
4. Closed-contact inbound messages.
5. Outbound send failures.
6. Elasticsearch application-index promotion failures.
7. Scheduled polling failures.

## Daily Metrics

### Required Cards

Add a new page named `邮件监控` with daily summary cards:

| Card | Definition |
| --- | --- |
| `介绍邮件` | Count outbound `mail_record` rows where `mail_type = INTRODUCTION` and `sent_at` is in the selected day |
| `收到回复` | Count inbound `mail_record` rows where `direction = INBOUND` and `received_at` is in the selected day |
| `回复专家数` | Distinct `expert_contact_id` among inbound messages received in the selected day |
| `自动 QA 回复` | Count outbound rows where `mail_type = QA_REPLY` |
| `会议邀约` | Count outbound rows where `mail_type = MEETING_INVITATION` |
| `人工回复` | Count outbound rows where `mail_type = MANUAL_QA_REPLY` or a manual-send source is recorded |
| `转人工消息` | Count inbound processing records routed to manual review |
| `未匹配来信` | Count inbound processing records with no matched contact |
| `发送失败` | Count outbound rows where `send_status` is not successful |
| `有效层新增` | Count successful candidate-to-application promotions |

### Conversion Metrics

Display lightweight conversion metrics:

| Metric | Formula |
| --- | --- |
| `回复率` | Distinct replied experts / introduction recipients |
| `自动回复覆盖率` | inbound messages automatically replied / inbound messages eligible for automatic handling |
| `人工介入率` | manual-review inbound messages / inbound messages |
| `有效层晋级率` | newly promoted application experts / distinct replied experts |

Avoid presenting same-day reply rate as a complete campaign result. Introductions sent today may receive replies on later days. The page should support:

1. Daily activity metrics by event time.
2. Cohort metrics by introduction-send date.

## Monitoring Dimensions

All summary and detail APIs should support:

1. Date range.
2. Campaign.
3. Sender account.
4. Mail type.
5. Direction.
6. Send status.
7. Processing result:
   - automatically replied.
   - manual review.
   - unmatched.
   - paused.
   - closed.
8. Contact conversation stage.
9. Expert keyword or country where practical.

Default range:

```text
Today 00:00:00 to current time in Asia/Shanghai
```

## Detailed Activity Tables

### Introduction Mail Table

Show every introduction email sent in the selected date range:

| Column | Description |
| --- | --- |
| Sent time | `sent_at` |
| Expert | Name, ORCID and email |
| Campaign | Campaign ID or display name |
| Sender account | Account code and sender email |
| Subject | Outbound subject |
| Send status | Sent or failed |
| Current stage | Current contact status |
| Reply state | No reply, replied, promoted to application layer |
| Actions | Open expert contact detail |

### Inbound Reply Table

Show every expert reply:

| Column | Description |
| --- | --- |
| Received time | `received_at` |
| Expert | Name, ORCID and email |
| Sender account | Mailbox that received the message |
| Subject | Inbound subject |
| Cleaned content | Latest expert content, expandable |
| Classified intent | `inbound_intent.intent_code` |
| Confidence | `inbound_intent.confidence` |
| Processing result | Automatic reply, manual review, unmatched, paused or closed |
| Application promotion | Promoted, already effective, skipped or failed |
| Actions | Open thread, open expert detail |

### Outbound Reply Table

Show automatic and manual replies sent to experts:

| Column | Description |
| --- | --- |
| Sent time | `sent_at` |
| Expert | Name, ORCID and email |
| Reply mode | Automatic or manual |
| Mail type | QA reply, meeting invitation, manual QA reply |
| Sender account | Account code and sender email |
| Subject | Outbound subject |
| Body | Expandable reply content |
| Matched QA rule | Rule ID and category where applicable |
| Send status | Sent or failed |
| Source inbound mail | Link to the inbound mail that triggered the reply |
| Actions | Open expert contact detail |

### Exceptions Table

Show records requiring operator attention:

| Exception type | Examples |
| --- | --- |
| Unmatched | Sender email cannot be linked to a contact |
| Manual review | Risky question, documents, meeting confirmation |
| Paused | Contact is under manual handling |
| Closed | Closed expert sent another message |
| Send failure | SMTP send did not succeed |
| Promotion failure | Candidate-to-application ES upsert failed |
| Scheduler failure | Polling task failed |

The table must allow operators to open the corresponding expert, inbound message or task execution.

## Data Model Improvements

### Add Sender Account to Mail Records

Current mail records do not store the sender-account code directly. Add:

```text
mail_record.sender_account_code VARCHAR(64)
```

Use it for:

1. Introduction-mail account monitoring.
2. Inbound mailbox monitoring.
3. Automatic-reply account monitoring.
4. Sender account error investigation.

Populate it for:

- initial outreach.
- inbound mail persistence.
- QA automatic replies.
- meeting invitations.
- manual emails.
- meeting confirmations.

### Add Outbound Source

Add:

```text
mail_record.send_source VARCHAR(32)
```

Suggested values:

- `INITIAL_OUTREACH`
- `AUTO_QA`
- `AUTO_MEETING_INVITATION`
- `MANUAL_SEND`
- `MEETING_CONFIRMATION`

This avoids inferring automation only from `mail_type`.

### Link Automatic Reply to Source Inbound Mail

Current outbound replies use `in_reply_to`, but monitoring should have a direct relational link:

```text
mail_record.source_mail_record_id BIGINT NULL
```

Use it for automatic QA replies, meeting invitations and operator replies triggered from an inbound message.

### Add Processing Result

The existing `inbound_mail_processing` table should record a normalized processing outcome:

```text
processing_result VARCHAR(64)
```

Suggested values:

- `AUTO_QA_REPLIED`
- `AUTO_MEETING_INVITATION_SENT`
- `MANUAL_REVIEW`
- `UNMATCHED_CONTACT`
- `AUTO_REPLY_PAUSED`
- `CONTACT_CLOSED`
- `EXPLICITLY_DECLINED`
- `PROCESSING_FAILED`

Keep the existing process reason for detailed explanation.

### Add Application Promotion Audit

Add a durable MySQL audit table:

```text
expert_application_promotion
```

Suggested fields:

| Field | Purpose |
| --- | --- |
| `id` | Primary key |
| `expert_contact_id` | Contact relation |
| `orcid_id` | Stable expert identifier |
| `source_mail_record_id` | Inbound message that triggered promotion |
| `promotion_source` | Automatic reply, manual binding or manual confirmation |
| `promotion_status` | Pending, success, failed, removed |
| `error_message` | ES failure details |
| `operator_name` | Manual action audit |
| `created_at`, `updated_at` | Audit timestamps |

Use this table for:

1. `有效层新增` daily metrics.
2. Failed-promotion retry.
3. Operator visibility.

## Backend API Plan

### Daily Summary

```text
GET /api/mail-monitoring/summary?date=2026-06-02&campaignId=&accountCode=
```

Response:

```json
{
  "date": "2026-06-02",
  "introductionSent": 120,
  "inboundReplies": 18,
  "repliedExperts": 15,
  "autoQaReplies": 11,
  "meetingInvitations": 4,
  "manualReplies": 2,
  "manualReviewMessages": 3,
  "unmatchedMessages": 1,
  "failedOutboundMessages": 0,
  "applicationPromotions": 14
}
```

### Introduction Activity

```text
GET /api/mail-monitoring/introductions?from=&to=&campaignId=&accountCode=&page=&size=
```

### Inbound Activity

```text
GET /api/mail-monitoring/inbound?from=&to=&campaignId=&accountCode=&processingResult=&page=&size=
```

### Outbound Reply Activity

```text
GET /api/mail-monitoring/outbound-replies?from=&to=&campaignId=&accountCode=&sendSource=&sendStatus=&page=&size=
```

### Exceptions

```text
GET /api/mail-monitoring/exceptions?from=&to=&type=&page=&size=
```

### Sender Account Health

```text
GET /api/mail-monitoring/sender-accounts?date=2026-06-02
```

Response should include:

- daily sent count.
- daily limit.
- introduction count.
- automatic reply count.
- failed send count.
- last send time.
- last receive time.
- enabled state.

## Frontend Plan

### Add `邮件监控` Navigation Entry

Add a page next to:

- 邮箱账号.
- QA 规则.
- 专家联系.
- 未匹配来信.
- 任务记录.

### Page Layout

Use four sections:

1. Date and filter toolbar.
2. Daily summary cards.
3. Activity tabs:
   - `介绍邮件`
   - `收到回复`
   - `已发回复`
   - `异常待处理`
4. Sender-account health table.

Default behavior:

1. Open with today's date.
2. Refresh automatically every 60 seconds.
3. Provide a visible manual refresh button.
4. Show the last refreshed time.
5. Preserve filter state while switching activity tabs.

### Fast Navigation

Every activity row should link to:

1. Expert contact detail.
2. Source inbound email where applicable.
3. Outbound reply body.
4. Manual-review or unmatched-mail handling page where applicable.

## Rollout Safety Plan

### Phase 1: Visibility Before Automation

Implement and deploy:

1. Monitoring fields.
2. Daily summary API.
3. Introduction, inbound, outbound and exception tables.
4. Sender-account health.

Run the system with automatic reply disabled and confirm that monitoring reflects existing manual and test traffic accurately.

### Phase 2: Limited Candidate-Layer Enablement

Enable automatic reply for a small controlled set:

1. Limit daily introductions by campaign.
2. Limit sender-account daily volume.
3. Start with one or two mailboxes.
4. Keep risky intents routed to manual review.
5. Refresh the monitoring page during the rollout window.

Suggested initial safety limits:

```text
Daily introduction limit: 20 to 50 experts
Automatic reply polling interval: 5 to 10 minutes
Sender accounts enabled: 1 to 2
```

Increase only after confirming:

- no unexpected automatic replies.
- no SMTP failures.
- no unmatched-mail spike.
- no manual-review backlog spike.
- application-index promotions are correct.

### Phase 3: Alerts

Add configurable alerts:

| Alert | Suggested threshold |
| --- | --- |
| Outbound send failure | Any failure |
| Scheduler failure | Any failed polling run |
| Sender account near limit | Above 80% daily limit |
| Unmatched inbound spike | More than configured threshold per hour |
| Manual review backlog | More than configured threshold |
| Promotion failure | Any failed application-index upsert |
| No polling heartbeat | No successful poll within expected interval |

First version can display alerts on the monitoring page. External notifications can be added later.

## Implementation Phases

### Phase 1: Monitoring Data Completeness

1. Add `sender_account_code` to `mail_record`.
2. Add `send_source` to `mail_record`.
3. Add `source_mail_record_id` to `mail_record`.
4. Add normalized inbound `processing_result`.
5. Populate fields in every send and receive path.
6. Add database indexes for daily queries.

Suggested indexes:

```text
mail_record(direction, sent_at)
mail_record(direction, received_at)
mail_record(mail_type, sent_at)
mail_record(sender_account_code, sent_at)
mail_record(expert_contact_id, created_at)
inbound_mail_processing(process_status, received_at)
inbound_mail_processing(processing_result, received_at)
```

### Phase 2: Monitoring APIs

1. Add `MailMonitoringService`.
2. Add summary queries.
3. Add paginated introduction activity.
4. Add paginated inbound activity.
5. Add paginated outbound reply activity.
6. Add exceptions query.
7. Add sender-account health query.

### Phase 3: Monitoring Page

1. Add `邮件监控` navigation entry.
2. Add daily summary cards.
3. Add activity tabs and filters.
4. Add expandable mail body display.
5. Add direct navigation to expert contact details.
6. Add 60-second auto-refresh.

### Phase 4: Application Promotion Visibility

1. Add `expert_application_promotion`.
2. Display promotion result beside inbound replies.
3. Add failed-promotion exception records.
4. Add retry action for failed promotions.

### Phase 5: Alerts and Controlled Rollout

1. Add threshold configuration.
2. Show page-level warning banner.
3. Start with a limited campaign.
4. Review metrics daily before increasing volume.

## Test Plan

### Backend

1. Summary counts only selected-day records.
2. Date boundaries use `Asia/Shanghai`.
3. Introduction table returns outbound `INTRODUCTION` mails only.
4. Inbound table returns classified intent and processing result.
5. Outbound reply table links replies to experts and source inbound mails.
6. Sender-account filtering works.
7. Campaign filtering works.
8. Failed outbound messages appear in exceptions.
9. Paused and closed inbound messages appear in exceptions.
10. Promotion failures appear in exceptions and can be retried.

### Frontend

1. Monitoring page opens with today's date.
2. Summary cards display correct values.
3. Activity tabs preserve filters.
4. Mail bodies expand without leaving the page.
5. Expert links open the correct contact detail.
6. Exceptions link to the correct handling flow.
7. Auto-refresh does not reset selected filters.
8. `node --check src/main/resources/static/app.js`

### Full Regression

```text
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --check src/main/resources/static/app.js
```

## Acceptance Criteria

The monitoring scope is complete when:

1. Operators can see today's introduction-mail count immediately.
2. Operators can list every expert who replied today.
3. Operators can list every automatic and manual reply sent today.
4. Each outbound reply shows its expert, sender account, subject, body and send status.
5. Automatic replies link back to their triggering inbound email.
6. Manual-review, unmatched, paused and closed-contact messages are visible in one exceptions list.
7. Sender-account usage and failures are visible by mailbox.
8. Application-layer promotions and failures are visible.
9. The page refreshes automatically and supports manual refresh.
10. Daily metrics use `Asia/Shanghai` date boundaries.
11. Existing backend tests and frontend syntax checks pass.

