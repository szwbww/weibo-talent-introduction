# Manual Acceptance — trust-reply-manual-authority

## Epoch 1 — 2026-08-24 15:28 CST

- Reviewed code boundary: `99cef49a37f79b409504e89cd5cd942370966c39..d9406ce`
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | 线上样本摘抄恢复 | LIVE_INBOUND:124 only has one real question; five signature fields absent; refresh has no bootstrap 422; old snapshot is STALE. | PENDING |  |  |  |
| A-2 | Yes | 七种 handling 与 mismatch 事实原文 | Seven handling options; selected mismatch fact stays, warning appears, generated text equals answerBody verbatim. | PENDING |  |  |  |
| A-3 | Yes | 跨摘要重复事实 | Same fact can bind to two requests and persists; no duplicate error; canonical ID occurs once. | PENDING |  |  |  |
| A-4 | Yes | LIVE 发送与数据库事实恒等 | Successful mail has `mail_record_qa_rule` IDs in canonical order; no extra/missing IDs; edited body changes no association. | PENDING |  |  |  |
| A-5 | Yes | 篡改、版本与安全硬门禁 | Tampered IDs/version/locked item fail before SMTP; suppressed contact remains blocked; failed cases create no SENT record. | PENDING |  |  |  |
| A-6 | Yes | 最终诊断留痕 | LIVE and training final actions include bounded `trust-reply-diagnostics-v1`; no body/fact text; temporary operations add none. | PENDING |  |  |  |
| A-7 | Yes | 自动、legacy 与纯人工发送回归 | Auto/legacy strict matching and degraded warning remain; pure manual rich text retains action type and has no diagnostics. | PENDING |  |  |  |
| A-8 | Yes | 原子发布证明 | R2 artifact contains both 02/03 commits; rollback returns both together; no deployable 02-only version. | PENDING |  |  |  |

## Human Sign-off

- Decision: PENDING
- Boundary: d9406ce
- Reporter: user
- Timestamp:
- Note:
