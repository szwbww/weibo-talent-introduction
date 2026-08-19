# Fast-P Human Review Handoff

- Outcome: READY_FOR_HUMAN_REVIEW
- Master base: af1723f37021328f8ffa61261504727e514fbb4b
- Current/final code head: 8c2ec53f4e97d06acb89b81bfb5a388a9d49a566
- Branch/worktree: fast/grounded-coverage / /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-grounded-coverage

## Child Status
| Child | Status | Code boundary | Fix rounds | Evidence commit |
|---|---|---|---:|---|
| 01-fact-and-catalog | LIGHT_PASS_WITH_NOTES | af1723f37021328f8ffa61261504727e514fbb4b..f5c09382744c0da8a537610af6145974ee1fcaf4 | 0 | ac278783f2969b8d357df95bd35c356a31cc0a02 |
| 02-unrecognized-request-detection | LIGHT_PASS_WITH_NOTES | f5c09382744c0da8a537610af6145974ee1fcaf4..533a02fce781ff09693d630c0d029f0d93c7d58a | 0 | 90c95af3d763c91b5b0bb31205c6667acbc12063 |
| 03-fact-order-drag | LIGHT_PASS | 533a02fce781ff09693d630c0d029f0d93c7d58a..8c2ec53f4e97d06acb89b81bfb5a388a9d49a566 | 0 | 2f538463a3049d0d961390d89effe2e157e13cb0 |

## RECORD_ONLY Index
| Observation | Child | Evidence | Source report |
|---|---|---|---|
| O-1: A-4 catalog-side exemption constant additionally lists `application.timeline` (O4-class orphan, runtime `def.copy` only); plan's literal exemption set would fail its own mandated guard | 01 | AiReplyIntentCatalogTest guard + AiReplyIntentCatalog.kt:342 | verify-log 01 |
| O-2: **Production gap**: Funding support rule (id=8) keywords (`salary,subsidy,funding,compensation` + V81 appends) contain NO substring of the orthopaedic letter; plan mandates 5/5 SUPPORTED on that letter with Funding support bound — only achievable via test-fixture keyword `remuneration`. Production letter will NOT show 5/5 via this rule as written | 01 | QaFactSelectionServiceTest C-2 fixture; V3/V81 keywords vs letter text | verify-log 01 |
| O-3: Plan acceptance "grep -c 'programme' V105 = 0" unsatisfiable against plan's own mandated reply_subject/body text; operative I-2 keyword invariant (0× `programme` in keyword lines) holds, enforced by parity test | 01 | V105 + parity test | verify-log 01 |
| O-4: C-2 promptPool read as 2 new facts + id=6 + Funding support + Pre-contract IP boundary (5 rules) — matches plan's own factRuleIds enumeration; plan wording "三条新规则" internally inconsistent | 01 | QaFactSelectionServiceTest | verify-log 01 |
| O-5: Plan `<正文见下>`/`<同 reply_body>` placeholders filled with plan's verbatim bodies (byte-identical) | 01 | V105 S1/S2 bodies | verify-log 01 |
| O-1: `canonicalizeWithMap` per-char lowercase vs `canonicalize` whole-string — speculative Unicode context-sensitive divergence (Greek sigma/non-BMP); wrapper equivalence verified on all P1 fixtures, no fixture impact | 02 | AiReplyIntentCatalog.kt:477-527 | verify-log 02 |
| O-2: `select()` emits `[ASK_ENUM]` for all callers (pending-mail suggest/preflight, training/unmatched) beyond plan's two decision points; **RATIFIED by A1 item 4** (behavior-neutral noise, no action) | 02 | QaFactSelectionService.select() | verify-log 02 |
| O-3: asks starting outside any request region excluded from unrecognized counts (region attribution) vs plan B-2 flat `filterNot`; behavior-neutral for acceptance letters | 02 | QaFactSelectionService.kt:414-417 | verify-log 02 |

## Pause/Resume
- Reason: N/A (child 02 paused then resumed; see ledger Amendments A1)
- Resume from: N/A

No whole-system verification was performed.
