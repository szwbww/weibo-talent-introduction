# Repair Plan: monitoring-window-and-cohort-reply-rate

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: docs/plans/2026-09-02/monitoring-window-and-cohort-reply-rate.md
Verification report: review-p verification, 2026-09-02
Implementation boundary: current working-tree diff for the baseline plan's 11 authorized files, reviewed against HEAD `20868f5`

## Objective

Count a cohort member as replied when it has any INBOUND mail at or after its INTRODUCTION, even if it also has an earlier INBOUND mail.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | I-2: same-cohort member with an INBOUND at or after INTRODUCTION is replied; first reply time is the minimum such INBOUND. | Both cohort SQL queries take `MIN(received_at)` before joining to `s.first_sent_at`; an earlier inbound makes the join fail and hides a later qualifying inbound. |

## Findings Excluded

| Finding | Reason |
|---|---|
| SQL integration-suite execution | Docker daemon unavailable; evidence is blocked, not a repairable product defect. |

## Unchanged Contract

- Keep I-1 cohort deduplication, I-3 `mail_record`-only reply authority, I-4 maturity denominator and 7-day cutoff, and I-6 no `send_status` predicate.
- Keep the two-query repository interface and all other baseline-plan file scope unchanged.
- Do not use `expert_contact.first_reply_at`, change frontend behavior, or alter the baseline plan.

## Authorized Files

| File | Purpose |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt | Make each query derive the earliest qualifying inbound after its cohort member's `first_sent_at`. |
| src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt | Add a MySQL regression case for an inbound before and another after the INTRODUCTION. |

## Repair Tasks

### R-1: Correlate first inbound with the cohort member's introduction

- Resolves: V-1
- Root cause: `MIN(received_at)` is calculated per expert before the `received_at >= s.first_sent_at` predicate; therefore the minimum can be a pre-introduction mail.
- Files: `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt`; `src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt`
- Change: In both country and domain cohort queries, choose `MIN(received_at)` only from INBOUND records joined with `received_at >= s.first_sent_at`, then calculate reply and mature-reply counts from that qualifying timestamp while preserving one row per cohort expert before country/domain aggregation.
- Regression test: Seed one expert with INBOUND before INTRODUCTION and another INBOUND after it; assert both country and domain cohort results count that expert as replied, and assert the mature count uses the qualifying post-introduction first reply.
- Existing verification: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailRecordRepositoryMonitoringIT -DmigrationIt=true`; `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`.
- Must not change: I-1/I-3/I-4/I-6 and the response DTO/service/frontend contracts.
- Prohibited: New SQL methods, `expert_contact.first_reply_at`, `send_status` filtering, production changes outside the two authorized files.

## Verification Commands

1. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailRecordRepositoryMonitoringIT -DmigrationIt=true`
2. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailMonitoringServiceTest`
3. `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`
4. `node --test src/test/js/monitoringDateDefault.test.js`
5. `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js`
6. `node --check src/main/resources/static/app.js`
7. `git diff --check`

## Completion Criteria

- A pre-introduction INBOUND no longer suppresses a later qualifying INBOUND in either cohort aggregation.
- The regression proves reply attribution uses the earliest qualifying INBOUND and preserves the 7-day mature cutoff.
- Changed files remain inside the authorized list.

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.
