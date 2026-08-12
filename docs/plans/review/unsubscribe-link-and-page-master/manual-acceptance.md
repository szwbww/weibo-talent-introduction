# Manual Acceptance — unsubscribe-link-and-page-master

## Epoch 2 — 2026-08-12 16:04:56 +0800

- Reviewed code boundary: `0482bcd497eefba9ce4f44f61a5624ae25d0efe1..0a8723a14f6e1035f9e56e9cfb75427b4c0774b8`
- Machine report epoch: 2
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 06-A1 | yes | Gmail HTML anchor | INTRODUCTION shows `Unsubscribe` anchor, not visible raw URL | PENDING | — | — | — |
| 06-A2 | yes | MIME plain text | text/plain retains full URL; HTML is multipart alternative | PENDING | — | — | — |
| 06-A3 | yes | Archive body | backend mail record remains plain text | PENDING | — | — | — |
| 06-A4 | yes | MATERIAL_REMINDER | anchor works; reply threading remains intact | PENDING | — | — | — |
| 06-A5 | yes | No URL configuration | no empty unsubscribe link is rendered | PENDING | — | — | — |
| 06-A6 | yes | End-to-end unsubscribe | issued link completes unsubscribe | PENDING | — | — | — |
| 06-A7 | yes | V88 migration/operator edits | wording applied without overwriting edited templates | PENDING | — | — | — |
| 07-A1 | yes | New token | 43-char base64url opaque token; no email disclosure | PENDING | — | — | — |
| 07-A2 | yes | Token idempotence | same recipient reuses URL; one database row | PENDING | — | — | — |
| 07-A3 | yes | New token unsubscribe | valid new token unsubscribes successfully | PENDING | — | — | — |
| 07-A4 | yes | Legacy token compatibility | existing HMAC token still unsubscribes | PENDING | — | — | — |
| 07-A5 | yes | Forgery rejection | invalid token is rejected | PENDING | — | — | — |
| 07-A6 | yes | Unconfigured environment | normal sending behavior remains when URL config absent | PENDING | — | — | — |
| 08-A1 | yes | Confirm page visual parity | desktop design matches stated site contract | PENDING | — | — | — |
| 08-A2 | yes | Email masking | rendered/source HTML contains no full email | PENDING | — | — | — |
| 08-A3 | yes | Keep subscribed | navigates to site without suppression side effect | PENDING | — | — | — |
| 08-A4 | yes | Success page | expected success content and same shell | PENDING | — | — | — |
| 08-A5 | yes | Mobile page | no overflow; stacked responsive controls | PENDING | — | — | — |
| 08-A6 | yes | Invalid link | HTTP 400 plain `invalid link` response | PENDING | — | — | — |
| 08-A7 | yes | One-click channel | POST remains `unsubscribed`, ONE_CLICK suppression | PENDING | — | — | — |
| 08-A8 | yes | Context-path form | confirmation POST succeeds under context path | PENDING | — | — | — |
| 08-A9 | yes | No-logo fallback | wordmark appears and no empty image tag | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: `0a8723a14f6e1035f9e56e9cfb75427b4c0774b8`
- Reporter: —
- Timestamp: —
- Note: —
