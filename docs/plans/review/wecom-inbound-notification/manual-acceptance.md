# Manual Acceptance — docs/plans/2026-10-06/wecom-inbound-notification.md

## Epoch 1 — 2026-10-08T06:48:58Z

- Reviewed code boundary: 235681497c226066fa0174a2d79bc82863a1e91a..b5452b4ff487766dd69c0ef4b3996dd2b484f1ef
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-M1 | Yes | Complete experience: deploy both steps to a test environment with an authorized test-group Webhook and mobile WeCom group notifications; enable group messages; send one new test-expert email and run “检查回复”; then disable group messages and send another email. | First email is stored and creates one group notification; second is stored with no new notification; auto-reply value remains unchanged; re-enabling does not backfill the second email. | PENDING | N/A | N/A | N/A |
| A-M2 | Yes | Preview review: open the supplied clickable preview and click the rightmost “群消息” twice. | Closed is gray; open is blue; saving feedback appears; other buttons do not change; no real group message is sent. | PENDING | N/A | N/A | N/A |

## Human Sign-off

- Decision: PENDING
- Boundary: b5452b4ff487766dd69c0ef4b3996dd2b484f1ef
- Reporter: N/A
- Timestamp: N/A
- Note: N/A
