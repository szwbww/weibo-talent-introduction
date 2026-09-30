# Manual Acceptance — discovery-pdf-contact-integrity

## Epoch 1 — 2026-09-30T04:23:57Z

- Reviewed code boundary: `a37efe970e4446242b121c5628db02daa631fc92..827b8b0f7df5c51e06db6d06be528d06e6ee2620`
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | Run phase-5 command; inspect `target/discovery-plan-acceptance/pdf-contact-integrity.json`; compare chee.pdf p1 and lyderic.pdf p2 in the ZIP. | Chee and Lydéric target emails exist but all identity fields are empty; no erroneous Contact; `PDF_PARSE`, `httpRequests=1`, `fulltextObtained=true`. | PENDING | — | PENDING | PENDING |
| A-2 | Yes | Inspect the Lun entry and compare lun.pdf p1 with mailto/final emails. | `yue@msn.com` is absent from contacts/resolved; exactly one unowned `lun_yue@msn.com`; `mgaarde1@lsu.edu` remains. | PENDING | — | PENDING | PENDING |
| A-3 | Yes | Inspect synthetic controls, positive controls, and `pdf-contact-consumer.json`. | Unique `Jane Doe1,†,*`, two-email control, and independent-name control bind correctly; positive consumer documents retain supplied identity values. | PENDING | — | PENDING | PENDING |
| A-4 | Yes | Inspect text control table and Surefire summaries for PlainText/Core/JATS/Resolver/Pdf tests. | Two-address and wrapped-email cases remain correct; double-newline case is empty; brace count, HTML/XML, tail-page, and mailto controls regress 0. | PENDING | — | PENDING | PENDING |
| A-5 | Yes | Inspect `pdf-contact-cache.json`, `pdf-contact-consumer.json`, and DiscoveryIdentityTest summary. | Producer is `20261004`; 20261003/20261002/null are FAILED unsupported with 0 downloads/writes; current control succeeds; proof version remains 20260925. | PENDING | — | PENDING | PENDING |
| A-6 | Yes | Inspect `pdf-contact-consumer.json` case inputs, filtering, validation calls, and captured documents. | Unowned targets are not validated or written; `yue@msn.com` is absent before consumption; positive unique control writes. | PENDING | — | PENDING | PENDING |

## Human Sign-off

- Decision: PENDING
- Boundary: `827b8b0f7df5c51e06db6d06be528d06e6ee2620`
- Reporter: PENDING
- Timestamp: PENDING
- Note: PENDING
