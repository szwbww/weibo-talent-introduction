# Manual Acceptance — global-world-clock-master

## Epoch 1 — 2026-09-17T04:27:36Z

- Reviewed code boundary: `24f5c8205a304d3682e09e02458960bc2caa0463..474445a3f9b84921decbf7f28c1a2b995fa6b897`
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | yes | 01 component acceptance | Complete `global-world-clock-01-component.md` A-1 behavior check. | PENDING | N/A | N/A | N/A |
| A-2 | yes | 01 responsive header acceptance | Complete 01 A-2 real-browser width, one-line, icon fallback check. | PENDING | N/A | N/A | N/A |
| A-3 | yes | 01 selected-time acceptance | Complete 01 A-3 current-time versus selected-time check. | PENDING | N/A | N/A | N/A |
| A-4 | yes | 01 time-zone correctness | Complete 01 A-4 DST, cross-day, and minute-offset check. | PENDING | N/A | N/A | N/A |
| A-5 | yes | 01 catalogue/search acceptance | Complete 01 A-5 real catalogue and search check. | PENDING | N/A | N/A | N/A |
| A-6 | yes | 01 lifecycle/auth acceptance | Complete 01 A-6 close, auth-hide, and late-response check. | PENDING | N/A | N/A | N/A |
| A-7 | yes | 01 keyboard/theme acceptance | Complete 01 A-7 focus, keyboard, and dark-mode check. | PENDING | N/A | N/A | N/A |
| A-8 | yes | 01 preservation acceptance | Complete 01 A-8 business-view preservation check. | PENDING | N/A | N/A | N/A |
| A-9 | yes | Exit-adjacent entry and log removal | Clock is between user and logout; no log button; task log and authentication remain functional. | PENDING | N/A | N/A | N/A |
| A-10 | yes | Cache upgrade and resource order | All 11 versioned assets use the new key; `world-clock.js` follows `app.js`; context path and catalogue search work. | PENDING | N/A | N/A | N/A |
| A-11 | yes | Meeting UI isolation | Clock does not disrupt meeting confirmation in light or dark theme. | PENDING | N/A | N/A | N/A |
| A-M1 | yes | Aggregate delivery | A-1 through A-11 completed in a logged-in test environment; record browser, widths, theme, and every unexecuted item. | PENDING | N/A | N/A | N/A |

## Human Sign-off

- Decision: PENDING
- Boundary: `474445a3f9b84921decbf7f28c1a2b995fa6b897`
- Reporter: N/A
- Timestamp: N/A
- Note: N/A
