# Manual Acceptance — expert-reachability

## Epoch 1 — 2026-08-17

- Reviewed code boundary: edda3e4e67e8b4511f3c7ca76b09926c56e4f69a..59f33864c0cd91f6699f83eabf5fa88e7c1d7839
- Machine report epoch: 1 (PASS, INITIAL; P3 cosmetic finding RE-04-O3)
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 02-A1 | Yes | Mapping pushed to existing indices after restart | GET /orcid_info_candidate/_mapping/field/reachability and /orcid_info_application/... return keyword mapping; no mapping-conflict WARN for reachability | — | — | — | — |
| 02-A2 | Yes | RAW layer unchanged | GET /orcid_info/_mapping/field/reachability returns empty {} (expected, not defect) | — | — | — | — |
| 02-A3 | Yes | List/detail regression | list rows/fields, filters (近N年/H指数), level switch identical to pre-change; no console errors | — | — | — | — |
| 03-A1 | Yes | Full backfill first run | POST /api/experts/sync-reachability returns BulkSyncResult; task EXPERT_REACHABILITY_SYNC SUCCESS/PARTIAL_SUCCESS; failure 0; skipped significantly < total | — | — | — | — |
| 03-A2 | Yes | Duplicate trigger returns 409 | second POST while running -> HTTP 409 "任务正在执行中"; one RUNNING record | — | — | — | — |
| 03-A3 | Yes | Never-contacted experts assigned | expert without expert_contact row gets reachability HIGH/LOW (or field absent only when no emailSource); never 'has emailSource but field absent' | — | — | — | — |
| 03-A4 | Yes | Unsubscribe takes effect immediately | suppress email -> _source.reachability becomes BLOCKED_UNSUBSCRIBED without full sync; email_suppression row added | — | — | — | — |
| 03-A5 | Yes | Unsubscribe succeeds when ES down | suppress returns success; email_suppression row added; WARN logged; no 5xx | — | — | — | — |
| 03-A6 | Yes | operatorStatus + updatedAt sort unaffected | updatedAt sort order stable across backfill; backfill-operator-status unaffected | — | — | — | — |
| 04-A1 | Yes | Four-tier badge rendering | HIGH green 可达 高 / LOW amber 可达 低 / UNKNOWN gray 可达 未知 / BLOCKED red 已退订·停发; badge left of h-index; 5px dot | — | — | — | — |
| 04-A2 | Yes | Hover tooltip readable | native tooltip per tier; HIGH/LOW show source+domain; UNKNOWN fixed text; BLOCKED per-sub-tier text | — | — | — | — |
| 04-A3 | Yes | BLOCKED row still shown, checkbox disabled | row visible, no dim/strike; checkbox disabled; select-all excludes it | — | — | — | — |
| 04-A4 | Yes | Existing badges/filters regression | h-index/已补充/tags/account text identical; region/discipline/近N年 filters unchanged; level switch unchanged | — | — | — | — |
| 04-A5 | Yes | Status-filter path renders 可达 未知 without errors | MySQL path rows show gray 可达 未知; no undefined console errors | — | — | — | — |
| 05-A1 | Yes | Five filter options each effective | 全部 max; 仅高可达 all green; 高+低 no red/gray; 仅未知 all gray; 仅已失效 all red; sum = 全部 | — | — | — | — |
| 05-A2 | Yes | Clear-filters resets the control | 清除筛选 -> dropdown back to 全部; count back; activated hint gone | — | — | — | — |
| 05-A3 | Yes | Batch ES and retry paths agree | RecipientScope EXCLUDE_BLOCKED: both paths exclude all BLOCKED; diff only from other dimensions | — | — | — | — |
| 05-A4 | Yes | Existing filter combinations unchanged | region/discipline/domain/status/近N年/H-index counts identical to pre-change when reachability unset | — | — | — | — |
| 05-A5 | Yes | Material-reminder target unchanged | recipient estimate identical when no reachability config | — | — | — | — |
| 06-A1 | Yes | Config save + refill | set 排除已失效, save, reopen -> refilled; list pill 可达性 · 排除已失效 shown | — | — | — | — |
| 06-A2 | Yes | Old typed API update preserves new column | PUT INTRODUCTION config changing only cron -> reachabilityFilter still 排除已失效; gate + statuses also preserved | — | — | — | — |
| 06-A3 | Yes | Filter truly affects targets | 不过滤 N1; 排除已失效 N2 = N1 - B (B = BLOCKED count, error 0); 仅高可达 N3 < N2 | — | — | — | — |
| 06-A4 | Yes | Illegal value rejected | PUT reachabilityFilter=MEDIUM -> HTTP 400 with message; config unchanged | — | — | — | — |
| 06-A5 | Yes | Upgrade does not change existing scope | after V100 + restart, untouched task estimate identical; shows 不过滤 | — | — | — | — |
| 06-A6 | Yes | Gate-filter behavior unchanged | gate pill/switch/changelog identical; two pills coexist without overlap | — | — | — | — |

## Human Sign-off
- Decision: PENDING | ACCEPT | REJECT
- Boundary: 59f33864c0cd91f6699f83eabf5fa88e7c1d7839
- Reporter: <human identity or user>
- Timestamp: <value>
- Note: <value>
