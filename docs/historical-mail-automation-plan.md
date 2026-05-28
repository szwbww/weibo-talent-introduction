# Historical Mail Automation Improvement Plan

## Background

The folder `~/Downloads/聊天记录5.27` contains historical conversations between manual operators and experts. Most files are screenshot-based PDFs, so they were analyzed through OCR. The records show that the real business flow is not a single-round FAQ interaction. It is a staged application process involving interest confirmation, meetings, company matching, material collection, video or commitment requests, submission, and result follow-up.

This document records the implementation plan for improving the current automated mail workflow based on those historical records.

## Observed Conversation Stages

1. Initial outreach
   - Introduce the talent program or enterprise cooperation opportunity.
   - Ask whether the expert is interested.
   - Ask for CV or basic availability.

2. Interest confirmation
   - Expert replies with interest, availability, or request for more details.
   - Common phrases include `interested`, `available`, `consider`, `open to`, `would like to know more`.

3. Meeting scheduling
   - Experts provide time slots and time zones.
   - Meeting tools include Zoom, Teams, Webex and Google Meet.
   - Manual operators often send meeting links and confirm China-time conversions.

4. Material collection
   - Common materials: CV, passport, PhD/Master/Bachelor degree certificates, employment proof, publication list, patents, awards, contracts, appointment letters, PPT, video and signed commitment.
   - Experts often send partial materials and ask what is still missing.

5. Company and project matching
   - Operators introduce the matched enterprise, its technical field, and why the expert matches.
   - Experts ask for company details, research direction, project topic and whether the enterprise really fits their expertise.

6. Expert concerns and FAQ
   - Funding source and amount.
   - Whether money transfer or fees are required.
   - Whether the expert must relocate to China.
   - Whether remote part-time consulting is possible.
   - Confidentiality and employer-conflict concerns.
   - Application timeline and result announcement.

7. Application preparation
   - Chinese application form is prepared by the team.
   - Experts may need to review or provide missing details.
   - Some cases require a short self-statement video or VCR, sometimes with passport verification.
   - Some cases require a commitment statement that materials are submitted through one channel only.

8. Submission and follow-up
   - Operators inform experts after submission.
   - Results may be delayed.
   - If not selected, the operator may continue to next-round follow-up.

## Current System Gaps

1. QA matching uses the entire inbound body.
   - Historical emails contain long reply chains with old `From`, `Sent`, `To`, `Subject` blocks.
   - Matching the full body can trigger answers based on old quoted content instead of the expert's latest message.

2. There is no intent layer before QA.
   - Current flow goes from inbound email directly to QA matching.
   - Historical replies frequently represent actions, not questions: attaching CV, providing availability, asking to schedule a meeting, sending passport, accepting a project, or asking for missing materials.

3. Attachments are not stored.
   - Current IMAP service extracts text body only.
   - Historical records depend heavily on attachments and documents.

4. Conversation states are too coarse.
   - Current states can represent initial send, QA reply, meeting invitation and manual review, but cannot represent material collection, company matching, video request, commitment request, submission, result follow-up, or next round.

5. No structured material checklist.
   - Missing materials are currently implicit in email bodies.
   - Operators need a visible checklist and next action.

6. No structured meeting scheduling.
   - Available time, time zone and meeting tool preferences are not extracted or stored.

7. Expert identity matching is too strict.
   - Current matching relies on exact `from` email.
   - Historical conversations show experts may use ResearchGate, personal Gmail, university email, or company email.

## Implementation Plan

### Phase 1: Inbound Mail Cleaning

Goal: Make QA and intent detection operate only on the expert's latest message.

Backend changes:

1. Add `MailBodyCleaner`.
2. Remove historical quoted blocks after markers such as:
   - `From:`
   - `Sent:`
   - `To:`
   - `Subject:`
   - `On ... wrote:`
   - `-----Original Message-----`
   - repeated `>` quoted lines.
3. Strip signatures and disclaimers when possible.
4. Return both:
   - raw body for audit display.
   - cleaned body for matching and classification.

Database changes:

1. Add `cleaned_body LONGTEXT` to `mail_record`.
2. Store raw `body` unchanged.

Tests:

1. Latest expert reply with long quoted history should keep only latest content.
2. `RE:` chains should not trigger QA matches from old messages.
3. Plain short replies like `hi`, `Thanks`, `I am interested` should remain intact.

### Phase 2: Intent Classification Before QA

Goal: Decide whether an inbound email is an action, a question, or a manual-review case before sending any automatic reply.

Add `InboundIntentClassifier` with rule-based first version.

Initial intents:

- `INTERESTED`
- `ASK_MORE_INFO`
- `MEETING_TIME_PROVIDED`
- `MEETING_REQUESTED`
- `CV_ATTACHED`
- `DOCS_ATTACHED`
- `ASK_MATERIAL_LIST`
- `ASK_PROCESS`
- `ASK_FUNDING`
- `ASK_CONFIDENTIALITY`
- `ASK_REMOTE_PART_TIME`
- `ASK_COMPANY_INFO`
- `ASK_VIDEO_REQUIREMENT`
- `PASSPORT_UPDATED`
- `NOT_INTERESTED`
- `UNKNOWN`

Routing rules:

1. `INTERESTED`
   - If no meeting invitation has been sent, send `MEETING_INVITATION`.
   - Move contact to `MEETING_SCHEDULING`.

2. `MEETING_TIME_PROVIDED`
   - Record candidate time.
   - Move to `MEETING_SCHEDULING`.
   - Create manual task to confirm meeting link and time zone.

3. `CV_ATTACHED` or `DOCS_ATTACHED`
   - Save attachments.
   - Update material checklist.
   - Move to `MATERIALS_PARTIAL` or `MATERIALS_RECEIVED`.
   - Manual review required for document validation.

4. `ASK_PROCESS`, `ASK_FUNDING`, `ASK_REMOTE_PART_TIME`, `ASK_CONFIDENTIALITY`, `ASK_COMPANY_INFO`
   - Try QA answer only if confidence is high.
   - Otherwise manual review.

5. `ASK_VIDEO_REQUIREMENT`, `PASSPORT_UPDATED`, `ASK_MATERIAL_LIST`
   - Manual review or template-assisted reply, not fully automatic in first version.

6. `NOT_INTERESTED`
   - Send polite closure only if configured.
   - Move to `CLOSED`.

Database changes:

1. Add `inbound_intent` table:
   - `id`
   - `mail_record_id`
   - `expert_contact_id`
   - `intent_code`
   - `confidence`
   - `matched_keywords`
   - `auto_action`
   - `created_at`

Tests:

1. "I am interested" should not be treated as generic QA.
2. "I will be available at 9AM China time" should create meeting scheduling work.
3. "Attached is my CV" should create document processing work.

### Phase 3: Attachment and Document Management

Goal: Persist inbound attachments and expose them to operators.

Database changes:

1. Add `mail_attachment`:
   - `id`
   - `mail_record_id`
   - `file_name`
   - `content_type`
   - `file_size`
   - `storage_path`
   - `created_at`

2. Add `expert_document`:
   - `id`
   - `expert_contact_id`
   - `mail_attachment_id`
   - `document_type`
   - `document_status`
   - `review_note`
   - `created_at`
   - `updated_at`

Document types:

- `CV`
- `PASSPORT`
- `PHD_DEGREE`
- `MASTER_DEGREE`
- `BACHELOR_DEGREE`
- `EMPLOYMENT_PROOF`
- `PATENT_PROOF`
- `AWARD_PROOF`
- `PUBLICATION_LIST`
- `PPT`
- `VIDEO`
- `COMMITMENT`
- `OTHER`

Backend changes:

1. Extend `ImapMailReceiveService` to extract attachments from multipart email.
2. Save attachments to a configured storage directory.
3. Infer document type from filename and message intent.
4. Do not mark document as valid automatically; set `PENDING_REVIEW`.

Frontend changes:

1. Expert detail page shows document checklist.
2. Each document has status: missing, received, accepted, rejected.
3. Operators can review, reclassify and add notes.
4. Add one-click "missing material reminder" draft.

### Phase 4: Expanded Conversation State Machine

Goal: Represent the actual application lifecycle.

Extend `ConversationStatus`:

- `INTEREST_CONFIRMED`
- `MEETING_SCHEDULING`
- `MEETING_SCHEDULED`
- `MEETING_DONE`
- `MATERIALS_REQUESTED`
- `MATERIALS_PARTIAL`
- `MATERIALS_RECEIVED`
- `COMPANY_MATCHED`
- `APPLICATION_PREPARING`
- `VIDEO_REQUESTED`
- `VIDEO_RECEIVED`
- `COMMITMENT_REQUESTED`
- `COMMITMENT_RECEIVED`
- `SUBMITTED`
- `RESULT_PENDING`
- `REJECTED_THIS_ROUND`
- `NEXT_ROUND_FOLLOW_UP`

Backend changes:

1. Add `ConversationStateService`.
2. Centralize state transitions.
3. Validate allowed transitions.
4. Record state history.

Database changes:

1. Add `expert_contact_status_history`:
   - `id`
   - `expert_contact_id`
   - `from_status`
   - `to_status`
   - `reason`
   - `source`
   - `created_at`

Frontend changes:

1. Display current state in Chinese.
2. Display state timeline.
3. Show recommended next action based on current state.

### Phase 5: Structured Meeting Scheduling

Goal: Convert expert availability into actionable meeting records.

Database changes:

1. Add `meeting_schedule`:
   - `id`
   - `expert_contact_id`
   - `source_mail_record_id`
   - `expert_available_text`
   - `expert_timezone`
   - `china_time`
   - `meeting_tool`
   - `meeting_link`
   - `meeting_status`
   - `note`
   - `created_at`
   - `updated_at`

Backend changes:

1. Extract meeting tool keywords: Zoom, Teams, Webex, Google Meet.
2. Extract availability phrases where possible.
3. Store raw availability text even if time parsing is uncertain.
4. Create manual task to finalize meeting link.

Frontend changes:

1. Expert detail page shows meeting scheduling block.
2. Operators can confirm time and paste meeting link.
3. System can send meeting confirmation email from selected template.

### Phase 6: Expert Email Alias Support

Goal: Avoid losing replies when experts switch mail addresses.

Database changes:

1. Add `expert_email_alias`:
   - `id`
   - `expert_contact_id`
   - `email`
   - `source`
   - `verified`
   - `created_at`

Backend changes:

1. Match inbound email by primary expert email first.
2. Then match by alias.
3. If no match, create manual review record with possible candidate contacts by subject, name, and previous thread headers.

Frontend changes:

1. Show unmatched inbound mail queue.
2. Allow operator to bind an unmatched email to an expert.
3. Add alias after binding.

### Phase 7: QA Rule Expansion

Goal: Cover recurring expert questions from historical records.

New QA categories:

- `MATERIAL_LIST`
- `COMPANY_MATCH`
- `FUNDING_SOURCE`
- `NO_FEE`
- `REMOTE_PART_TIME`
- `CONFIDENTIALITY`
- `IP_AND_EMPLOYER_CONFLICT`
- `APPLICATION_TIMELINE`
- `RESULT_ANNOUNCEMENT`
- `VIDEO_REQUIREMENT`
- `PASSPORT_EXPIRED`
- `MEETING_TOOL`
- `NEXT_ROUND`

Important rule behavior:

1. Some answers can be automatic:
   - high-level process.
   - general remote part-time explanation.
   - no-fee statement.
   - basic material list.

2. Some answers should require manual review:
   - specific company details.
   - funding transfer and banking questions.
   - confidentiality or employer conflict.
   - video/passport/commitment wording.
   - final submission and result communication.

Implementation:

1. Add seed SQL for new categories and rules.
2. Add `handoff_required = 1` for risky categories.
3. Add frontend labels and filters.

### Phase 8: Task Generation

Goal: Turn important inbound events into operator work items.

Task types:

- `CONFIRM_MEETING`
- `REVIEW_DOCUMENT`
- `REQUEST_MISSING_DOCUMENT`
- `CONFIRM_COMPANY_MATCH`
- `PREPARE_APPLICATION_FORM`
- `REQUEST_VIDEO`
- `REQUEST_COMMITMENT`
- `FOLLOW_UP_RESULT`
- `HANDLE_RISKY_QUESTION`

Backend changes:

1. Add `TaskGenerationService`.
2. Generate tasks from classified intent and state transition.
3. Avoid duplicate open tasks for the same contact and task type.

Frontend changes:

1. Task record page should show generated tasks.
2. Task type must be readable Chinese text.
3. Operators can mark task done and add note.

## Recommended Delivery Order

### Sprint 1

1. Implement `MailBodyCleaner`.
2. Store `cleaned_body`.
3. Update `QaMatchService` to use cleaned body.
4. Add unit tests for quoted history removal.

### Sprint 2

1. Implement `InboundIntentClassifier`.
2. Add `inbound_intent` table.
3. Update `AutoMailReplyService` routing:
   - intent first.
   - QA second.
   - manual review for risky intents.
4. Add unit tests for common historical intents.

### Sprint 3

1. Add attachment extraction.
2. Add `mail_attachment` and `expert_document`.
3. Add document checklist to expert detail page.
4. Add tests for multipart emails with attachments.

### Sprint 4

1. Expand conversation statuses.
2. Add status history.
3. Add recommended next action.
4. Update frontend status labels.

### Sprint 5

1. Add meeting schedule table and UI.
2. Extract meeting availability text.
3. Support meeting confirmation email.

### Sprint 6

1. Add email alias matching.
2. Add unmatched inbound mail queue.
3. Add manual bind-to-contact action.

### Sprint 7

1. Add expanded QA categories and rules.
2. Mark risky categories as manual-review.
3. Add regression tests using historical sample phrases.

## Immediate First Implementation Target

The first code change should be Sprint 1 plus a minimal part of Sprint 2:

1. `MailBodyCleaner`.
2. `cleaned_body` persistence.
3. `InboundIntentClassifier` with these first intents:
   - `INTERESTED`
   - `MEETING_TIME_PROVIDED`
   - `CV_ATTACHED`
   - `DOCS_ATTACHED`
   - `ASK_PROCESS`
   - `ASK_FUNDING`
   - `ASK_CONFIDENTIALITY`
   - `UNKNOWN`
4. Route attachment/material/meeting intents to manual review for the first version.

This gives the system a safer foundation before adding more automatic responses.
