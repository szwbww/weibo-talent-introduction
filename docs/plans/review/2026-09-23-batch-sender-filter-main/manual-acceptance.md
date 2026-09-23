# Manual Acceptance — batch sender filter

## Epoch 1 — 2026-09-23T14:07:13Z

- Reviewed code boundary: 9237d6f573335d1624217cbc5501f68a6f52b97b..75cc1714611ac085341cf372d28c057bb332796d
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | Configure two sender accounts A/B; create a UI task selecting A; save, reopen, execute, inspect snapshot and mail records. | UI chip, config API, and snapshot are `["A"]`; all new successful mail records use A; B adds 0. | PENDING | N/A | N/A | N/A |
| A-2 | Yes | Run INTRODUCTION and MATERIAL_REMINDER with bound X/B and unbound Y, selecting A. | X sends 0 and remains bound B; eligible Y sends only with A and binds A; all-bound material reminder previews/sends 0. | PENDING | N/A | N/A | N/A |
| A-3 | Yes | Use two logical sender accounts sharing one IMAP mailbox; disable B; execute B-only then A-only configuration. | Picker keeps A/B distinct; disabled B selection is visible; B-only sends 0; A-only sends only with A. | PENDING | N/A | N/A | N/A |
| A-4 | Yes | Run legacy `[]` task; manually inherit A-only task, change to B, inspect diff/preview/execute. | Legacy task retains full-pool selection; manual snapshot is `["B"]`; original scheduled config remains `["A"]`. | PENDING | N/A | N/A | N/A |

## Human Sign-off

- Decision: PENDING
- Boundary: 75cc1714611ac085341cf372d28c057bb332796d
- Reporter: N/A
- Timestamp: N/A
- Note: N/A
