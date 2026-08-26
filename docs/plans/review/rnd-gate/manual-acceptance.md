# Manual Acceptance — rnd-gate

## Epoch 1 — 2026-08-26T04:51:44Z

- Reviewed code boundary: f2935072c819a9167e75220a6a959b0769462fde..ee152d2b21030f6b86da16769f638b29d4be094b
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | Deploy 01→04→02→03 in a test environment without changing any environment variable or admin configuration; request INTRODUCTION recipient estimate after each deployment; run quick promotion and deep discovery after each. | Four estimates match; promotion count remains in the same range as before deployment; deep-discovery enabled platform list is unchanged. | PENDING | N/A | N/A | N/A |
| A-2 | Yes | With a CANDIDATE whose classification is `PRODUCTION_RND` but uses a non-`rnd-v2-2026` version and has a valid email, select production R&D in batch configuration; request estimate; execute one-size INTRODUCTION batch. | Estimate excludes the expert; no OUTBOUND/INTRODUCTION/SENT record is added. | PENDING | N/A | N/A | N/A |
| A-3 | Yes | With an APPLICATION contact tagged 「承诺回复材料」 and no `expertClassification`, request MATERIAL_REMINDER estimate and execute one reminder. | Contact remains in estimate and reminder sends successfully. | PENDING | N/A | N/A | N/A |

## Human Sign-off

- Decision: PENDING
- Boundary: ee152d2b21030f6b86da16769f638b29d4be094b
- Reporter: N/A
- Timestamp: N/A
- Note: N/A
