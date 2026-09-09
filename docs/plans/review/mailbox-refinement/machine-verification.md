# Machine Verification — 00-mailbox-refinement-master

## Epoch 1 — 2026-09-09

- Master plan: docs/plans/2026-09-09/00-mailbox-refinement-master.md
- Governing master identity: worktree sha256 ed5a0f14bdf3fc21c877e9eacf0f1370b1943194eeea205935434ecc4f7eddb1; recorded identity commit 351d69a538bcf891514f234a8d717cb5ef64c63c
- Master identity state: CONSISTENT (invoked sha256 identical; amendments N/A)
- Boundary: af25bf54df2bc70dd0fe9e3254b246a49395219c..9b6591c04545769d4ffad97dd285742958b3c8f3
- Reviewer: AggregateReviewerMailboxRefinement (fresh subagent; no fast-p writer/verifier conversation)
- Result: PASS
- Convergence: INITIAL
- Repair artifact/result: N/A (no repair planning; no repair.md created)

### Complete review-p output (persisted verbatim from reviewer)

## Verification Result: PASS

Plan: docs/plans/2026-09-09/00-mailbox-refinement-master.md (governing identity worktree sha256 ed5a0f14bdf3fc21c877e9eacf0f1370b1943194eeea205935434ecc4f7eddb1, recorded identity commit 351d69a538bcf891514f234a8d717cb5ef64c63c, master_identity_state CONSISTENT, amendments N/A) with ordered child plans 01/02/03 in same dir.
Implementation boundary: af25bf54df2bc70dd0fe9e3254b246a49395219c..9b6591c04545769d4ffad97dd285742958b3c8f3 (worktree /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-refinement, branch fast/mailbox-refinement, evidence head ce94a7599b45eac13b7b472578a66c1941506c87; commits 9b6591c..ce94a75 touch only docs/plans/fast/mailbox-refinement/**).
Convergence: INITIAL (epoch 1). Manual acceptance: PENDING (human gate).

Commands (all fresh): (1) mvn test -Dtest=MailSubjectDecoderTest,ImapMailReceiveServiceTest,MailboxConversationControllerTest,MailboxConversationRepositorySqlCompatTest exit 0 BUILD SUCCESS: Decoder 12/0/0, Imap 16/0/0, SqlCompat 2/0/0, ControllerTest class-level mysqlIt skip 1. (2) Container probe: mysql 8.0.36 hostname 5016f88e1b86, container-local talent_introduction; only 3306 listener is mailbox-refinement-mysql. (3) DB_URL=...talent_introduction... mvn test -Pmysql-it -Dtest=MailboxConversationRepositoryIT exit 0: 21/0/0. (4) same + MailboxConversationControllerTest exit 0: 21/0/0. (5) node --check x4 all OK. (6) trio node tests 65 pass/0 fail. (7) node --test src/test/js/*.test.js 765 pass/0 fail. (8) rg cache key: 7 new-key index refs, old key 0 hits, new key only in 7 whitelisted test files (13 literals, 20 matches/8 files, exit 0). (9) mvn test package exit 0 BUILD SUCCESS 03:52: surefire 3234 run/0 fail/0 err/9 skipped (pre-existing opt-in gates); WAR built; exec node gates green.

Contract matrix verdicts (all PASS, evidence file:line): M-1 I-1 server sort repo:230-244 shared orderByClause page:171/explain:212, count:122 no ORDER BY, IT:496/:400/:313/:355 real MySQL; M-2 I-1 tabs mcb:195-199/:985-987/:976-996, 3 chips :46-53, 0 .sort, beh:1190/:1219; M-3 mark requery+empty-page fallback mcb:1925-1972, beh:1610/:1650; M-4 I-2 union repo:453-497 OUTBOUND-only mail_record + processing, IT:85; M-5 tags real processing id Service:205-246, OUTBOUND empty, ControllerTest:502/:523/:564/:575; M-6 follow session user repo:590, ControllerTest:175/:225/:249/:264; M-7 CSS byte-identical to S-6 target (cmp 17894/17894) + style test; M-8 no mock fetch/global rule, scope diff 4 files; M-9 no mark-on-browse (mcb:1541 explicit only, beh:2178); M-10 bounded cache 50/500/10 mcb:39-41, Map user|scope|contact :218-280, 0-valid anchors :247-262, epoch guards, beh:2031/:2063/:2100/:2135/:2196; M-11 translate click-only POST /api/translate escapeText :1612, reopen zero requests beh:1546/:1573; M-12 business writes reused (follow/mark/tags/status-level/manual-rich-reply/translate endpoints), partial failure surfaced beh:1761, full suite 0 fail; M-13 0 migration/ES-write (searchByOrcidIds read-only terms :631-660); M-14 3 tabs only, popover hidden default; M-15 MailSubjectDecoder pure unfold+decodeText single pass, applied Imap:339 + Service:163/:228, no DB UPDATE; tests 12/16; M-16 expertTags page-scoped ≤3 batch (Service:275-317), [] vs null, ControllerTest:649/:686/:724/:753/:784/:807, personTagNames mcb:999-1003, renderPerson :1015-1035 no badge; M-17 S-7 single-line style asserts; M-18 cross-chain recipientEmail/keyword/tags/expertTags decode, 0 /thread; M-19 cache-key chain exact 7+7, old key 0, child-03 diff +20/-20 literals only; M-20..M-25 M1..M6 preserved (nav/check/bulk/auto buttons, follow isolation, manual reply QA+drafts beh:1918/:1945, materials drawer only, task drill-down legacy gate app.js:14349, manage overlay portal + shared tag seams + adapter cleared app.js:15117-15355, old tag hosts adapter-less branch); M-26..M-29 child-01 P1..P4 + 02 P6 (cursor/scope, waitingReply compat ControllerTest:335, attachments metadata, validation <=255/start<=end 400); M-30 authorized scope exactly 23-file union, 0 out-of-scope, 0 styles.css/workbench/expert-materials/CLAUDE.md/migration changes; M-31 machine evidence for human-gated items exists (style source-text id assertions, 43 behavior cases); M-32 no skip-as-pass (mysql-it only vs probed container).

RECORD_ONLY: O-1 translation cache keyed source:id with bodyText equality at response-write (mcb:1496-1503/:2063-2068) - informational, mapped to master I-4 PASS, no promotion. O-2 browser screenshots deferred to human per master A-1/02 A-10-11 - no promotion. O-3 0/1/10-tag rows and narrow-pane hover human A-11; code paths machine-tested (beh:1150/:1176) - no promotion.

Findings: P1 none. P2 none. Observations: OBS-1 unused local pendingCount mailbox-chat.js:1008 (cosmetic leftover); OBS-2 surefire full-run totals 3234 vs child-recorded 3255 differ by disabled mysql-gated container aggregation only, 0 failures in every run.

Evidence boundaries: none blocking. Real-browser/hover/pixel checks, 03 A-1 network-panel, migrationIt/Testcontainers (env-blocked, not required) remain human/out-of-scope.

Next action: machine PASS -> proceed to master-plan human acceptance gate. Repair planning: N/A (no repair.md). BLOCKED: none.

No product code was modified.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| O-1 translation cache keyed by message source:id, bodyText equality at response-write | Master I-4 (UI操作不丢上下文) | No promotion — informational; observable contracts hold and are tested | mailbox-chat.js:1496-1503/:2063-2068; mailboxChatBehavior.test.js 43 cases |
| O-2 real-browser screenshot acceptance deferred to human acceptance | Master A-1 / child-02 A-10/A-11 | No promotion — correctly gated to human; machine proofs = CSS byte-compare vs S-6 + DOM source-text assertions | mailboxChatStyle.test.js; cmp 17894/17894 vs S-6 target |
| O-3 0/1/10-tag expert rows and narrow-pane line-height/hover are human A-11 | Master R6/I-8 / child-02 A-11 | No promotion — code paths machine-tested, pixel/hover behavior stays human | mailboxChatBehavior.test.js:1150/:1176; plan T5/A-11 |
