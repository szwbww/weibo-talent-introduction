# Manual Acceptance — docs/plans/2026-09-29/bounce-repair-master.md

## Epoch 1 — pending human results

- Reviewed code boundary: `ca55f0e37ca2d61cdcf362d4d64c5658e4dc34b3..46d7e17ddb4b71093a1faca156300335a442ddc4`
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | Complete 01 A-1–A-4, then 02 A-1–A-4, then 03 A-1–A-4 using isolated test accounts/contacts only. | Account pool verifies 9/160 (5.63%); policy rejection remains a failure without `EMAIL_INVALID`; only address invalidity marks invalid; failure creates no new resend; manual override, enable/disable controls, and bindings remain unchanged. | PENDING | N/A | N/A | N/A |
| A-2 | Yes | Preserve a test contact with only historical 5.7.1 while `EMAIL_INVALID`, and a contact with a manual `CHANGE_OPERATOR_STATUS`; upgrade the test application, run read-only reconcile, inspect DB/ES/details. | Neither stored DB/ES status changes because of upgrade/reconcile; the historical 5.7.1 case may report a discrepancy; manual case is `HUMAN_OVERRIDE`; no automatic mail is sent. | PENDING | N/A | N/A | N/A |

## Human Sign-off

- Decision: PENDING
- Boundary: `46d7e17ddb4b71093a1faca156300335a442ddc4`
- Reporter: N/A
- Timestamp: N/A
- Note: N/A
