# Manual Acceptance — meeting-country-timezone-master

## Epoch 1 — 2026-10-04

- Reviewed code boundary: e28e53fd898edd62905a0d45a6bf90396b18b1bf..eaf4fedbfa0ab09ae1d9f6bbb65764870461fe2d
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-M1 | Yes | In a local/test environment with a contact and controlled test inbox, execute 02 A-1–A-10 and 01 A-4/A-5 send plus historic-download flow. | Brazil 09:00 is Beijing 20:00; choices/email have no city or `East Time`; new attachment name follows the rule; historic attachment keeps archived name and bytes. | PENDING | — | — | — |
| A-M2 | Yes | View world clock, expert location settings, and independent meeting calendar; then execute 02 A-10. | World clock still searches city/IANA ID; expert-location directory is not deduplicated; original calendar UTC boundaries are not rewritten. | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: eaf4fedbfa0ab09ae1d9f6bbb65764870461fe2d
- Reporter: —
- Timestamp: —
- Note: —
