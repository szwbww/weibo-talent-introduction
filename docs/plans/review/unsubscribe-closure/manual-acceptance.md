# Manual Acceptance — unsubscribe-closure

## Epoch 1 — 2026-08-11T23:28:27+0800

- Reviewed code boundary: `8e8ddfcd6c02c754de3e50b3c02004a2900e5be5..cfe8936c2dcf049672ebaca036430aeabcc1cc7d`
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 01-A-1 | Yes | INTRODUCTION preview and delivered email | Real configured-baseUrl token link, no literal placeholder/example URL; prior body text unchanged. | PENDING | | | |
| 01-A-2 | Yes | MATERIAL_REMINDER preview and delivery | Same valid unsubscribe link after the three-line signature. | PENDING | | | |
| 01-A-3 | Yes | MEETING_INVITATION and MEETING_CONFIRMATION | No separator/unsubscribe line or literal placeholder; meeting body remains unchanged. | PENDING | | | |
| 01-A-4 | Yes | Pre-V87 operator-edited INTRODUCTION body | Edit survives migration; only one unsubscribe line is appended. | PENDING | | | |
| 01-A-5 | Yes | Re-run V87 statement body on pre-production after V87 | Affected rows = 0; one `unsubscribe here:` occurrence. | PENDING | | | |
| 01-A-6 | Yes | Production-like pre-production WAR startup | V87 succeeds; no FlywayException or unresolved-placeholder error. | PENDING | | | |
| 01-A-7 | Yes | Real INTRODUCTION unsubscribe link | Confirmation GET then unsubscribe POST succeeds; suppression source ONE_CLICK. Record any context-path 404. | PENDING | | | |
| 02-A-1 | Yes | Suppressed recipient meeting confirmation | HTTP 400 readable error; no email; no OUTBOUND mail record. | PENDING | | | |
| 02-A-2 | Yes | Manual single send then API `allowSuppressed=true` retry | Default blocks; override returns 200/messageId and delivers. | PENDING | | | |
| 02-A-3 | Yes | Pending-workbench reply to suppressed recipient then retry after removal | HTTP 400, no 409/DELIVERY_UNKNOWN attempt; removal permits normal send. | PENDING | | | |
| 02-A-4 | Yes | Batch including suppressed recipient | Recipient is skipped as SUPPRESSED, not failed; no EMAIL_INVALID or throttling. | PENDING | | | |
| 02-A-5 | Yes | Suppressed-recipient QA auto reply | MANUAL_HANDOFF / RECIPIENT_UNSUBSCRIBED; no automatic reply or delivery-layer error text. | PENDING | | | |
| 02-A-6 | Yes | Suppressed-recipient meeting-invitation automatic path | MANUAL_HANDOFF / RECIPIENT_UNSUBSCRIBED; no meeting mail or delivery-layer exception. | PENDING | | | |
| 02b-A-1 | Yes | Empty-body message with exact `unsubscribe` subject | Suppression row source MAILTO, reason `mailto unsubscribe`, and no auto reply. | PENDING | | | |
| 02b-A-2 | Yes | Client mailto unsubscribe flow | Client subject is `unsubscribe`; suppression source MAILTO. If no UI control, inspect raw mailto header then run A-1 equivalent. | PENDING | | | |
| 02b-A-3 | Yes | Subject merely mentioning unsubscribe | No suppression; normal intent flow; recipient can still receive mail. | PENDING | | | |
| 02b-A-4 | Yes | Normal subject plus unsubscribe phrase in body | Suppression source INBOUND_REPLY, not MAILTO. | PENDING | | | |
| 02b-A-5 | Yes | Empty-body Chinese subject `退订` | Suppression source MAILTO. | PENDING | | | |

## Human Sign-off

- Decision: PENDING
- Boundary: `cfe8936c2dcf049672ebaca036430aeabcc1cc7d`
- Reporter: user
- Timestamp: N/A
- Note: N/A
