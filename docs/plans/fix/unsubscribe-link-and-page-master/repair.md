# Repair Plan: unsubscribe-link-and-page-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-08-12/unsubscribe-link-and-page-master.md (SHA-256 29f401c80efaba9649fb720d8b2856d8dedc1b45956c36d5cd76eb7628108594)
Approved amendments: A1 docs/plans/2026-08-12/unsubscribe-06-html-anchor-body.md (SHA-256 05d4181f8d740b33fff2729bb7e17d360dda7ad12b3998f2c7597cb3ccc4203e); A2 docs/plans/2026-08-12/unsubscribe-07-opaque-token.md (SHA-256 cd595abea7660328a43cd29291e32886ef96abe32413ef6e865b69dcbf9205be); A3 docs/plans/2026-08-12/unsubscribe-08-branded-page.md (SHA-256 0b54bf790b316b63903e33f24198b7c411174aeeb62fb74fab98bbbd07a79da0)
Verification report: aggregate/master review-p report, epoch 1, 2026-08-12 (controller destination: docs/plans/review/unsubscribe-link-and-page-master/machine-verification.md)
Implementation boundary: 0482bcd497eefba9ce4f44f61a5624ae25d0efe1..44d6aa23ebdb43581af427708f8348423e2c33a7

## Objective

Add the two omitted, required `maskEmail` boundary assertions without changing page behavior or production code.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P2 (mandatory acceptance) | Plan 08 I-5 requires independent coverage for no `@`, multiple `@`, empty local, and empty domain. | `UnsubscribePageRendererTest#mask email handles boundary shapes` covers normal, single-character local, no `@`, and multiple `@`, but omits `@b.com` and `a@`. |

## Findings Excluded

| Finding | Reason |
|---|---|
| Plan 06 RECORD_ONLY `AutoMailReplyService.kt:977` | Unchanged from `0482bcd`; MEETING_INVITATION is explicitly out of scope and remains plain text. |
| All other aggregate matrix rows | Fresh focused tests, full `mvn test`, `mvn clean package`, source inspection, and diff checks passed. |

## Unchanged Contract

- `UnsubscribePageRenderer.maskEmail` behavior remains exactly as implemented: `@b.com` renders `•••@b.com`; `a@` renders `•••`.
- No production code, migration, configuration, controller behavior, CSS, token behavior, or manual acceptance item changes.
- The existing Plan 08 tests and all product/test scope outside the Authorized Files remain unchanged.

## Authorized Files

| File | Purpose |
|---|---|
| src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribePageRendererTest.kt | Add discriminating assertions for the empty-local and empty-domain I-5 bounds. |

## Repair Tasks

### R-1: Cover the two mandatory email-mask bounds

- Resolves: V-1.
- Root cause: lines 90-92 test only `a@b.com`, `noatsign`, and `a@b@c.com`; neither required empty-local nor empty-domain input is exercised.
- Files: `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribePageRendererTest.kt` only.
- Change: extend `mask email handles boundary shapes` (or an equivalently focused existing test) with assertions that `confirmPage("t", "@b.com")` contains `•••@b.com` and that `confirmPage("t", "a@")` contains `<p class="qf-pill">•••</p>`.
- Regression test: the two assertions must prove the renderer's output, not private implementation details.
- Existing verification: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribePageRendererTest`; `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`; `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`; `git diff --check`.
- Must not change: every pre-existing assertion and all rendered HTML/CSS behavior.
- Prohibited: any edit outside the Authorized Files; changing production code; weakening or removing existing assertions; adding migrations, configuration, or documentation changes.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribePageRendererTest`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package`
4. `git diff --check`

## Completion Criteria

- The focused renderer test passes and directly asserts both `@b.com → •••@b.com` and `a@ → •••`.
- Full test and package commands pass.
- The product/test diff from `44d6aa23ebdb43581af427708f8348423e2c33a7` changes only the Authorized File.
- No product code was changed.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/unsubscribe-link-and-page-master/repair.md` invocation authorizes:

1. Only `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribePageRendererTest.kt` and the required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only the Authorized File, with product commit subject `test(fast-p): cover unsubscribe email-mask empty bounds`.
3. Appending `docs/plans/review/unsubscribe-link-and-page-master/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed file, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with evidence commit subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it: `$review-fast-p docs/plans/fast/unsubscribe-link-and-page-master/human-review-handoff.md` using the committed `docs/plans/review/unsubscribe-link-and-page-master/repair-execution.md`.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
