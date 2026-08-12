# Fast-P Ledger — master: docs/plans/2026-08-12/unsubscribe-link-and-page-master.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-08-12/unsubscribe-link-and-page-master.md (sha256 29f401c80efaba9649fb720d8b2856d8dedc1b45956c36d5cd76eb7628108594)
- Amendments: A1, A2, A3
- Master base: 0482bcd497eefba9ce4f44f61a5624ae25d0efe1
- Branch: fast/unsubscribe-link-and-page-master
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/unsubscribe-link-and-page-master
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-12 11:31:52 +0800
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| unsubscribe-06-html-anchor-body | docs/plans/2026-08-12/unsubscribe-06-html-anchor-body.md | commit:8941887ee0cb6a8ad37a00e564a557d1c265a1c0 | none | 2 | LIGHT_PASS_WITH_NOTES | 0482bcd497eefba9ce4f44f61a5624ae25d0efe1 | 04f88337da5824389767a3ef504eb92e6de083f4 | 0 | — | 04f88337da5824389767a3ef504eb92e6de083f4 | 5a028eb1ab6febab4ff3e32f3dcd43d2bd52c356 | Epoch 1 paused for A1; RECORD_ONLY O-1: pre-existing out-of-scope body=mail.body at AutoMailReplyService.kt:977 (MEETING_INVITATION, unchanged from base) |
| unsubscribe-07-opaque-token | docs/plans/2026-08-12/unsubscribe-07-opaque-token.md | commit:69c9fa2afae5d7eca9947685aff247925a6ec3ce | none | 2 | LIGHT_PASS | 04f88337da5824389767a3ef504eb92e6de083f4 | d2c5bda11fb7df7052d8f25134b336481d3268dd | 0 | — | d2c5bda11fb7df7052d8f25134b336481d3268dd | 1e8237db6412a91af94eec648b7a60720cbdc27c | Epoch 1 paused for A2; A2 code form translated (windowed any) as indexOfSlice not in Kotlin stdlib — semantically identical, adjudicated PASS by verifier |
| unsubscribe-08-branded-page | docs/plans/2026-08-12/unsubscribe-08-branded-page.md | commit:b893912f380a6a6ed47aa31557551c9d5c43897a | none | 2 | LIGHT_PASS_WITH_NOTES | d2c5bda11fb7df7052d8f25134b336481d3268dd | 44d6aa23ebdb43581af427708f8348423e2c33a7 | 0 | — | 44d6aa23ebdb43581af427708f8348423e2c33a7 | 81446b00713dd1d9a063bad5af0c548f10765d00 | Epoch 1 paused for A3; RECORD_ONLY O-1: I-5 prose lists empty-local/empty-domain maskEmail bounds not directly asserted in T-6 (implementation handles them; plan's own T-6 cases pass) |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-12/unsubscribe-06-html-anchor-body.md | commit:0482bcd497eefba9ce4f44f61a5624ae25d0efe1 | commit:8941887ee0cb6a8ad37a00e564a557d1c265a1c0 | Plan 06 §验证命令 + §验收标准 I-3 | T-6 anchors the MATERIAL_REMINDER URL; existing GateTest:219 asserts raw-URL html prefix; repair uniquely determined (one-line assertion + authorize file, 10th file within ≤10 budget) | HUMAN:Approve A1 2026-08-12 12:25:41 +0800 |
| A2 | docs/plans/2026-08-12/unsubscribe-07-opaque-token.md | commit:0482bcd497eefba9ce4f44f61a5624ae25d0efe1 | commit:69c9fa2afae5d7eca9947685aff247925a6ec3ce | Plan 07 T-5 + §验收标准 I-1 | T-5 mandated '@'-in-decoded-bytes assertion is statistically flaky (P(byte==0x40)=11.8% per run on correct implementation); observed full-gate failure at UnsubscribeTokenServiceTest:118; deterministic email-bytes-subsequence assertion is strictly stronger per I-1 | HUMAN:Approve A2 2026-08-12 12:40:00 +0800 |
| A3 | docs/plans/2026-08-12/unsubscribe-08-branded-page.md | commit:0482bcd497eefba9ce4f44f61a5624ae25d0efe1 | commit:b893912f380a6a6ed47aa31557551c9d5c43897a | Plan 08 T-4 + §验证命令 + §验收标准 回归 | T-4's injected @Service renderer breaks pre-existing @WebMvcTest UnsubscribeControllerIllegalTokenTest (no renderer bean in slice -> 3 context-load errors); repair uniquely determined: @MockBean UnsubscribePageRenderer (same idiom as existing suppressionService mock; illegal-token tests never reach the renderer) | HUMAN:Approve A3 2026-08-12 14:17:43 +0800 |
