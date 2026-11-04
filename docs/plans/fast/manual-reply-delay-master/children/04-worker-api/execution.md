## Epoch 1 — PLAN_CONFLICT (bounded prerequisite check)

Executor: `Impl04WorkerApi`.

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md`
- Plan SHA-256: `b68fedd43c52e61c4a2502808d3514986baf22c932b24f5029945dcba261ae4d`
- Execution ID: the canonical plan path above plus `@b68fedd43c52e61c4a2502808d3514986baf22c932b24f5029945dcba261ae4d`.
- Epoch: NEW, bound to the user-approved plan at `05342b4be63bbc7d9b30416ae73ada2d44b562df`; read-only `git diff --quiet` confirmed those plan bytes match.
- Master approval supplied: `0bfe5b07c85e76f85c43a31d30fde8b37f30b9b9`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master`.
- Branch: `fast/manual-reply-delay-master`.
- Worktree Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-manual-reply-delay-master`.
- Common Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git`.
- Pre-execution product SHA: `9bbb3b050f533a0010d48d545b4756abc8aa7e82`.
- Pre-execution/checkpoint HEAD: `82ba0687a37d6cf26e908053763c2227cebe2476`.
- Post-execution product SHA: unchanged `9bbb3b050f533a0010d48d545b4756abc8aa7e82`; no child04 product commit.
- Implementation range: empty. No production or test file edited; only this execution report appended.
- Existing controller modification to `docs/plans/fast/manual-reply-delay-master/ledger.md` was preserved.

### Confirmed scope conflict

04 T3 requires restore to return original input (`manual-reply-delay-04-worker-api.md:77,81`). The downstream contract explicitly requires complete LEGACY input and QA/assembly/RAG/adopt context, recovered from server snapshots after refresh (`manual-reply-delay-06-frontend.md:12,113,145,208-210`). The controller explicitly authorized this bounded downstream prerequisite inspection, then instructed no product edits until the missing production-file authority is resolved.

The current LEGACY restore model is lossy for that contract:

| Original input / missing persisted identity | Source evidence | Consumer / consequence |
|---|---|---|
| Original `TrustReplyAssembleRequest`: top-level expected source/evidence versions, requested fact IDs, per-request fact selections, frame selection/version, final-paragraph request structure and operator facts are not captured as the original request. | `TrustReplyWorkbenchService.kt:357-370` declares the request. `DelayedManualReplyModels.kt:183-188` retains only source, verified itemVersions, paragraph-by-request mapping and diagnostics. Its factory `:598-605,712-723` persists that reduced result, not the request. Per-item source/evidence versions do exist at `TrustReplyWorkbenchService.kt:298-314`; they are not the original top-level aggregate request or frame/selection data. | Existing legacy editor `app.js:17699-17721` builds `expectedSourceVersion`, `expectedEvidenceSetVersion`, `requestFactSelections`, `frameSnapshot` and locked items; `:18497-18506` resubmits `adopt.trustReplyAssembly`. 06 T1 requires restoration of precisely this legacy context. The current model cannot faithfully recover a non-null original frame snapshot or the original selection/request structure. |
| Original `MeetingInput`, notably `zoneId`, `startLocal`, `endLocal`, `generatedAt`, and original `zoomUrl` spelling. Legacy input also contains ignored compatibility fields (`templateId`, `templateBody`, `expertSalutation`, `senderSignature`), which are not retained as raw input. | `MeetingConfirmationModels.kt:40-58` declares the input; `PendingMailOperationService.kt:189-190` accepts it. `DelayedLegacyRestore` at Models `:191-203` retains ICS and `DelayedMeetingEventSnapshot` only; the latter at `:108-112` stores UTC milliseconds and meeting link. Factory `:721-723,731-737` copies only those final artifacts. `PreparedManualReply` at Pending `:2025-2030` retains raw subject/html/text, but no original meeting or assembly request. | 04 restore's original-input contract includes accepted LEGACY request data. Meeting editor serialization explicitly retains zone/local-time/generatedAt at `mailbox-chat.js:149-153`, and send consumes `meeting.input` at `:9455-9456`; this current editor evidence is not a claim that the existing app.js legacy editor already exposes meeting controls. UTC/ICS must not be substituted for original input in the new restore API. |

Meeting inference is not a safe substitute: `MeetingConfirmationService.kt:173-183` normalizes the URL/zone/local times; `:202-225` generates a semantic digest and ICS. Its `:460,473-474` renders a country label rather than preserving the original IANA zone identifier in the human meeting text, and `:538-542` writes UTC DTSTART/DTEND. A digest is not reversible original input. Re-parsing or guessing a zone would violate the explicit instruction not to fabricate original payload from UTC/ICS.

The storage issue cannot be solved solely inside the authorized controller/worker/Pending changes: `DelayedManualReplySnapshotCodec` serializes the declared model and rejects unknown JSON properties (`DelayedManualReplyModels.kt:274-305`). Packing restoration input into unrelated evidence/audit text or diagnostics would violate the explicit whitelist and conflate user input with trusted internal evidence.

### Smallest authority needed

Authorize `src/main/kotlin/com/weibo/talentintroduction/mail/service/DelayedManualReplyModels.kt` in child04, in addition to already-authorized Pending and child04 tests:

- Capture original legacy restore inputs at preparation in `PendingMailOperationService.kt`, keeping these distinct from trusted delivery evidence.
- Extend the explicit restore model/factory/validation in `DelayedManualReplyModels.kt` to persist the missing original assembly and meeting input through a safe, bounded whitelist. Decide exact snapshot compatibility/version handling in the amendment; do not silently invalidate previously valid snapshots.
- Exercise round-trip restoration in the already-authorized child04 test files. No additional existing-test authorization is requested without concrete compatibility evidence.
- Keep worker SMTP payload frozen and unchanged; original input is for restoration only and must not become new send authority.

This is a prerequisite/scope finding, not a repair plan or permission to modify Models. No applied migration or later-child production change is needed to establish this finding. No existing plan was changed.

### Task status

| Requirement | Status | Evidence |
|---|---|---|
| T1 runtime reconstruction | BLOCKED before implementation | Full child execution paused at prerequisite scope gate. |
| T2 bounded worker | BLOCKED before implementation | No worker file created. |
| T3 original-input restore | CONFLICT | Missing declared original-input fields require prohibited Models edit. |
| T4 fixed public mapping verification | BLOCKED before implementation | Existing lifecycle mapping read only; no child04 test/verification claim. |

### Command evidence

All commands ran in the target worktree, never the main worktree.

| Command | Observed result |
|---|---|
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/plan_identity.py docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md` | Exit 0, canonical path and SHA-256 above; repeated before report, unchanged. |
| `python3 /Users/lukai/.agents/skills/execute-p/scripts/worktree_identity.py docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md --worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master` | Exit 1: unrelated stale publish-worktree registry path `/private/var/folders/r_/p27w33t543l9r08_h0sxjmf40000gn/T/multi-ai-kit-publish-weibo-talent-introduction-2ccc4a976cde-lt1m864b` is absent. Used explicitly allowed native read-only fallback; did not prune. |
| `git rev-parse --show-toplevel && git symbolic-ref --short HEAD && git rev-parse HEAD --git-dir --git-common-dir` | Exit 0; root, branch, HEAD and Git directories shown above. |
| `git merge-base --is-ancestor 9bbb3b050f533a0010d48d545b4756abc8aa7e82 HEAD` | Exit 0; supplied product base is reachable. |
| `git diff --quiet 05342b4be63bbc7d9b30416ae73ada2d44b562df -- docs/plans/2026-11-04/manual-reply-delay-04-worker-api.md` | Exit 0; exact approved child bytes match. |
| `git status --short` (before this report) | Only controller-owned `M docs/plans/fast/manual-reply-delay-master/ledger.md`; no product changes. |

Targeted grep receipts:

- Pattern `data class MeetingInput|data class CalendarAttachmentSnapshot`, main Kotlin source: hits `MeetingConfirmationModels.kt:40,134`; those declaration ranges were read.
- Pattern `data class TrustReplyAssembleRequest|function buildTrustReplyAssemblySnapshot|function buildManualReplySendPayload|trustReplyAssembly`, scoped to `TrustReplyWorkbenchService.kt` and `app.js`: request declaration `:357`, serializer `app.js:17699`, adoption `:17751`, actual send assignment `:18505-18506`; full relevant ranges were read.
- Pattern `startLocal|generatedAt|previewAttachmentSha256`, `mailbox-chat.js`: input whitelist `:150`, request attachment digest `:9456`; these are existing consumers, not newly implemented restore behavior.
- Pattern `restore|原输入|恢复|meeting`, child06 plan: original-input obligations at `:12,113,145,208-210`.

### Verification / runtime evidence

No build, compilation, test, formatter, SMTP, database or runtime smoke command ran. Required Maven commands and runtime scenarios are NOT SATISFIED. No passing test counts, skips or smoke results are claimed. A field-declaration scope conflict does not require running product commands to reproduce missing persisted data; controller explicitly prohibited doing so.

### Freshness and next action

- Plan identity rechecked: YES, unchanged.
- Native worktree identity rechecked: YES.
- Product changes/commit: NONE.
- Required implementation commands run: NO.
- Historical test evidence used as current proof: NO.
- Remaining blocker: explicit production scope/snapshot compatibility amendment for original LEGACY input persistence.
- Next action: controller persists PLAN_CONFLICT pause and obtains exact authority. Resume execution against newly approved immutable plan bytes; do not mark this epoch READY_FOR_VERIFICATION.