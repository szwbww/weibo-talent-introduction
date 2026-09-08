# Fast-P Child Brief — 10 收发件箱专家聊天布局

- Master: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5)
- Plan: docs/plans/2026-09-07/10-mailbox-chat-frontend.md — the complete approved contract. Read it first. S-3 CSS/DOM code blocks are the ONLY production CSS source (byte-verbatim); frontend-baseline.md is read-only reference; ui-style-contract.md S-1..S-5.
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials (branch fast/mailbox-materials)
- Base SHA: 020e8392f89ebbf5ed38b95060605055a67167c5 (= child 09 Code head)
- Execution report: docs/plans/fast/mailbox-materials/children/10/execution.md

## Role
Use execute-p (read skill://execute-p first). Implement ONLY child 10. Serialized run: child 11 untouched. You are the sole writer.

## Authorized files (exactly the plan's 10-file list)
1. src/main/resources/static/mailbox-chat.js (new)
2. src/main/resources/static/mailbox-chat.css (new)
3. src/main/resources/static/app.js (modify)
4. src/test/js/mailboxChatBehavior.test.js (new)
5. src/test/js/mailboxChatStyle.test.js (new)
6. src/test/js/mailboxExpertGrouping.test.js (modify)
7. src/test/js/mailboxDateDefault.test.js (modify)
8. src/test/js/taskDrilldown.test.js (modify)
9. src/test/js/unmatchedDetailResolvedAction.test.js (modify)
10. src/test/js/unmatchedQaReplySource.test.js (modify)

No other files. Proof requiring an unlisted file → return PLAN_CONFLICT. index.html registration belongs to child 11 — do NOT touch index.html; do NOT touch trust-reply-workbench.js internals.

## Constraints
- Invariants I-1..I-5 + S-1/S-3/S-4/S-5 of the plan; S-3 CSS/DOM byte-verbatim; no inline styles/undeclared classes/global-rule edits; reuse existing global classes + TrustReplyWorkbench.mount + ExpertMaterials.mount (child 08) + submitManualRichReply + QA audit chain; no new chat DB/IM; no deletion of legacy backend processing APIs; no outbound-only free rich-text bypass.
- I-1: normal expert chat has NO 查看/处理 entry; pending inbound keeps ONLY 标记已处理 (mark-resolved) via the existing pending/unmatched handled API + original operation-log write; handled shows read-only state; original text/cleaned body/translate/labels/technical info expandable in place; outbound shows real send status only; body renders via existing safe text/HTML clean functions from server DTOs; attachment names default collapsed (count), expand shows ≤3 names + 查看全部附件 opens the child-08 component filtered by this mail's source.
- I-2: right column fixed order 往来 → workbench collapsed (default) → manual expanded (default) → logs collapsed; workbench binds summary.latestInbound.processingId (real processing id, NOT the last visible filtered message); expert switch destroys old mount and discards stale responses; no right-side tabs; mounting activates only in the mailbox view with no taskExecutionId (app.loadMailbox guard: legacy table/group code stays for task drill-down and no-script fallback).
- I-3: manual reply reuses submitManualRichReply + server validation/QA audit; in-memory drafts keyed by contactId+targetInboundId+account; NEW INBOUND with an edited draft → prompt the user to choose the target (never silently rewrite subject/body/QA info); draft cleared only after successful send + count refresh; send button disabled while in-flight; failure keeps all inputs; workbench generate → edit/facts → adopt → manual send is the same original path; existing safety-check failures show as before; NO historical readiness/approval gates.
- I-4: received=0/SENT>0 experts show 待专家回复 + 收0·发N and appear under all/followed/waitingReply; failed-only never mixes in; no-inbound → workbench shows cannot-generate (0 generation calls); manual area still expanded but shows an explanatory note + 选择模板发送跟进邮件 button that opens the EXISTING expert mail flow (ManualMailOptionType is COMPOSE_TEMPLATE-only — verified; command has no free subject/body; do NOT fabricate a sending fake rich-text editor and do NOT invent a processingId).
- I-5: mailbox list = experts only, paged 20; summary from child-07 conversations API (waiting/pending filters compose with followed); header 材料 button opens ExpertMaterials drawer via child-08 mount (same contactId store); follow uses child-07 PUT/DELETE (disable while in flight, optimistic rollback on failure); taskExecution drill-down + unmatched keep their entries and parameters (never fake unmatched as an expert); new CSS = S-3 verbatim only.
- Left 320px expert rail (280 at ≤1100px), no client-side grouping of the current page; existing filter readers passed to the new summary API.
- Tests: adjust the 4 listed existing test files ONLY where the new activated branch changes behavior, KEEPING old compatibility-branch assertions; never delete send-validation to make tests pass. New behavior tests: (source,id) rendering keys, mark-resolved flow (status/count/badge update without editor refresh or downloads), drafts restore across expert switch + new-inbound target prompt, adopt→manual send with QA payload intact, waiting-reply vs failed-only separation, follow optimistic rollback, drawer/material store parity, task/unmatched entries intact. Style test: byte-compare shipped mailbox-chat.css vs the plan S-3 block.
- Environment: node v25; JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home (mvn test). Fast-p evidence excluded from your commit.

## Required commands (run all; exact output + exit codes in execution.md)
1. node --check src/main/resources/static/mailbox-chat.js && node --check src/main/resources/static/app.js
2. node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js
3. node --test src/test/js/*.test.js (full JS suite green — existing contract tests included)
4. JAVA_HOME=... mvn test (full suite incl. exec-plugin Node phase)

## Commit
Commit the implementation locally as: `feat(fast-p): implement 10` — 10 authorized files only. No push/merge/amend/rebase.

## Acceptance for return
Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Write full result to execution.md (files changed, per-command evidence, I-1..I-5 + S-3 byte-compare evidence, deviations).
