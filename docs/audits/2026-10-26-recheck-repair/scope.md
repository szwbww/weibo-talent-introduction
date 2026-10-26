# Approved implementation scope (2026-10-26)

Authorization: user approved the conversation's six-part repair, explicitly requested direct changes on main, no application deployment, and permitted index/data updates.

- All pending-review entry: server-owned NEEDS_REVIEW scope, independent of list success.
- Filtered list: bounded asynchronous paginated snapshot, no per-page full scan or 10k window; explicit freshness/failure/retry, no admission writes.
- Roster: persist input profiles/baselines during a fast scroll, close scroll before paper lookup; bounded indexed lookup batches and visible stages/progress.
- Single active recheck per operator, idempotent same-spec reuse, explicit conflict for changed spec; no automatic cancellation or loss of old history.
- Restore server-owned run/READY confirmation on re-entry, handle stale frontend replies.
- Preserve all archived bodies, verification/admission rules, provider settings; failure counts reflect saved records.

Validation: JDK11 Maven compile/package plus focused review/recheck/archive tests, real local MySQL migrations/query-plan tests, JS lifecycle/list tests, git diff check. Production changes limited to additive compatible archive lookup indexes/generated columns; no application upload/restart and no expert/admission/task mutations.
