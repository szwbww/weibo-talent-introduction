# Repair Plan: apollo-verification-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: `docs/plans/2026-10-18/apollo-verification-master.md`
Verification report: aggregate/master verification, finding V-1
Implementation boundary: `3efc192faf043badc1ef27bef124a71d3120c4c5..e473ffc714b840a2f42c9ca826da7c37e36ab9d7`

## Objective

An ORCID-only existing contact created after registration must block the pending automatic commit and preserve all contact/admission state.

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-1 | P1 | Master I-5; child04 I-4 and implementation step 2 | Commit-time contact lookup reads `profile.orcidId`, but persisted RAW deliberately sets it to `docId`; the real bound ORCID survives only in `externalIds.orcid`, so the required re-read misses a contact made after registration. |

## Findings Excluded

| Finding | Reason |
|---|---|
| O-01, O-02-01, O-03-E2-01/02, O-04-E2-01/02, O-05-E2-01/02/03, O06-01/02, O07-01 | RECORD_ONLY baseline debt/optional coverage; not repair authority. |

## Unchanged Contract

- Keep the RAW `orcidId=docId` identity convention and fact-fingerprint/CAS checks intact.
- Preserve email and alias contact protection, SBIR/APPLICATION/manual/legacy protections, and all provider boundaries.
- No contact/mail writes, new endpoint, provider request, schema change, or test-only bypass.

## Authorized Files

| File | Purpose |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationCommitService.kt` | Resolve the real bound ORCID from persisted `externalIds` when evaluating commit-time contact protection. |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationFlowIT.kt` | Add isolated-MySQL regression for a contact inserted after registration by the persisted external ORCID only. |

## Repair Tasks

### R-1: Preserve late ORCID contact protection

- Resolves: V-1.
- Root cause: `prepare()` rebuilds a RAW profile whose `orcidId` is the document identifier; `contacted()` therefore queries that identifier rather than the frozen real ORCID in `externalIds`.
- Files: exactly the two authorized files above.
- Change: make the commit-time read use the validated/normalized real bound ORCID from `externalIds.orcid`, while retaining the existing e-mail and alias checks; malformed/absent external IDs must not be treated as a matching identity.
- Regression test: use the existing real MySQL + mock HTTP FlowIT fixture to register and queue a qualifying item, insert an `expert_contact` keyed only by its persisted external ORCID before commit, then prove `PROTECTED`/`CONTACT_PROTECTED`, no automatic admission, and no candidate projection.
- Existing verification: run `DiscoveryVerificationFlowIT` and `DiscoveryVerificationCommitServiceTest` with JDK11/Testcontainers.
- Must not change: RAW serialization convention, qualification rules, admission CAS, provider clients, or contact tables.
- Prohibited: backfilling contacts, writing aliases, querying live providers, or widening scope outside these files.

## Verification Commands

1. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationFlowIT,DiscoveryVerificationCommitServiceTest`
2. `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock mvn test -Dapi.version=1.44 -DskipNodeTests=true -Dtest=DiscoveryVerificationRepositoryIT,DiscoveryVerificationContractTest,DiscoveryVerificationRulesTest,DiscoveryVerificationAdmissionGateTest,DiscoveryVerificationWorkerTest,DiscoveryVerificationCommitServiceTest,DiscoveryVerificationFlowIT,DiscoveryVerificationIntegrationTest,DiscoveryVerificationQueryServiceTest,DiscoveryVerificationControllerTest,DiscoveryVerificationReportingIT`
3. `node --test src/test/js/*.test.js`

## Completion Criteria

- A late contact identified solely by persisted `externalIds.orcid` prevents automatic admission/projection and leaves the item protected.
- E-mail/alias contact behavior and the persisted RAW fingerprint remain unchanged.
- Only the two authorized files change; focused backend verification passes. The known full-Node RECORD_ONLY failures remain explicitly reported, not passed.

## Human Approval

Execution is prohibited until the human explicitly approves this plan. After approval, run `execute-p` with this file.

## Review-Fast-P Execution Handoff

An explicit human-originated `$execute-p docs/plans/fix/apollo-verification-master/repair.md` invocation authorizes:

1. Only the Authorized Files and required verification commands in this plan.
2. After all repair tasks and required commands pass, exactly one local product commit before emitting `READY_FOR_VERIFICATION`, staging only Authorized Files, with subject `fix(apollo): preserve late ORCID contact protection`.
3. Appending `docs/plans/review/apollo-verification-master/repair-execution.md` with the exact approval source, repair identity, pre/post code SHAs, changed files, commands, deviations, executor identity when exposed, and clean-state evidence.
4. Exactly one docs-only evidence commit containing only that execution handoff, with subject `docs(review-fast-p): record repair execution`.
5. Returning to the already authorized `review-fast-p` aggregate re-review in the same task when the user's invocation requests it.

This authorizes no extra files, amend, history rewrite, push, merge, deployment, or product repair beyond this plan.
