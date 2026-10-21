# Fast-P Ledger — master: docs/plans/2026-10-21/apollo-repair-retention-master.md

- Status: PAUSED_FOR_HUMAN
- Master plan: docs/plans/2026-10-21/apollo-repair-retention-master.md (sha256 7b0113d6782083d3c86b910f74197bd39e3875f2018a93e68ef6d0f5554cfbda)
- Amendments: N/A
- Master base: 962fcfe924ad7562a4ea917d174180df82ac3b5c
- Branch: fast/apollo-repair-retention-master
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-10-21T12:20:25.372905
- Current child: 06
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: C-06-01 required constructor caller migration in ExpertDiscoveryServiceTest.kt is outside child06 whitelist; human scope amendment required
- Resume from: 626a9ecbeb3ee8d8fcf3a9406838f9749d6a3729

## Baseline
Original workspace preserved: 32 unstaged and 6 untracked entries; no staged changes. HEAD matches approved baseline. Java 11.0.32.1 and Docker 29.8.2 available. Validator supports --through-child. Production read-only freeze: execution 27254 revision 25790, input_closed=1, ENFORCE; DIRECT_CLEAR 120 PROJECTED, APOLLO_CLEAR 83 PROJECTED, PENDING 579, ERROR 264; no aggregate drift versus plan. Request totals respectively 360/332/4502/1373. WAR SHA256 066edea45b99168ff099a7a8c30b3e414c157437f25edcb955aa78a4a702172f; ApolloPeopleSearchClient.class 4164db077d77912adc36d23653c1a03d12a097704a6f71b039d267e51d9f1517; Worker.class 0f25ed138d61a855b889119f509768e9bfc906c682e317d434ab57ecbc87b466. Full item/generation/request/snapshot/evidence hash output retained in session artifact://9. Production writes/deploy/replay prohibited.

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| 01 | docs/plans/2026-10-21/apollo-repair-01-error-retention.md | sha256:290edb938b50bbb8859cc96db2d64fb1e3bacdcbc075186bd12e57448e32bb0b | none | 1 | LIGHT_PASS | 962fcfe924ad7562a4ea917d174180df82ac3b5c | dc1f01c55164016288c491cb5f9580eeaa5ac968 | 1 | 4c2339e13d90d06735dd00edeaaefeede845ed7c | 4c2339e13d90d06735dd00edeaaefeede845ed7c | 885b82f4e38a25f12d0e7179273223ef627cacec | Writers: ApolloRetention01,ApolloFix01R1; reverifier: ApolloReverify01R1; F-01 closed; 32 tests passed |
| 02 | docs/plans/2026-10-21/apollo-repair-02-credit-recovery.md | sha256:13e24fb1c83a1035ba58a902642833947755c7c79320fc5d474ddb7c819b6b16 | 01 | 1 | LIGHT_PASS | 4c2339e13d90d06735dd00edeaaefeede845ed7c | b2d0666118224e119b95fd02b57d6b25a6fd7bec | 0 | — | b2d0666118224e119b95fd02b57d6b25a6fd7bec | 75f7b3559497f1dce1c901910d5bef967ca8c5b2 | Implementer: ApolloRetention02; verifier: ApolloVerify02; 28 unit/runtime + 16 MySQL tests passed |
| 03 | docs/plans/2026-10-21/apollo-repair-03-search-trace.md | sha256:3cfa9fad532edb7e462148b85a84dfa5ac6f6d85ed8641ff2f14835ed370ec53 | 01,02 | 1 | LIGHT_PASS | b2d0666118224e119b95fd02b57d6b25a6fd7bec | 8024e30cc57c4188d829761f59444af3061edf51 | 0 | — | 8024e30cc57c4188d829761f59444af3061edf51 | d392f1ccae9c93118434b21fb9552b6e196fd032 | Implementer: ApolloRetention03; verifier: ApolloVerify03; 53 unit/runtime + 17 MySQL tests passed |
| 04 | docs/plans/2026-10-21/apollo-repair-04-identity-normalization.md | sha256:1b1fb2670794126f2a7842988319d7b9796a244a4bd74c00e23a6cb111896b2b | 03 | 1 | LIGHT_PASS_WITH_NOTES | 8024e30cc57c4188d829761f59444af3061edf51 | 043c843774fc3c4388d01d2a3eb5114b549bb92d | 1 | 835bfae52e32482313df2f76f99572f578aeb468 | 835bfae52e32482313df2f76f99572f578aeb468 | 474ee493f7cb6f1be4fac2bfd82d91a0e5c731dd | Writers: ApolloRetention04,ApolloFix04R1; reverifier: ApolloReverify04R1; F-04-01 closed; O-04-01 RECORD_ONLY; 70 tests passed |
| 05 | docs/plans/2026-10-21/apollo-repair-05-decision-audit.md | sha256:de1aba525acca6e45b8ef434360f85fd79715944a46af36b18f33c44e42e1809 | 04 | 1 | LIGHT_PASS_WITH_NOTES | 835bfae52e32482313df2f76f99572f578aeb468 | a489c28e93e0357e26f6e5e85ab8b49962ed0b5c | 0 | — | a489c28e93e0357e26f6e5e85ab8b49962ed0b5c | 5d2d7ed21c64114562cad0f376ba33a72b2e976f | Implementer: ApolloRetention05; verifier: ApolloVerify05; 53 tests passed; inherited O-04-01 RECORD_ONLY |
| 06 | docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md | sha256:908af3147608b328ab9d90b755b6b97e4cad369fd324cbb3be6d0b089779e48b | 05 | 1 | PAUSED_FOR_HUMAN | a489c28e93e0357e26f6e5e85ab8b49962ed0b5c | — | 0 | — | a489c28e93e0357e26f6e5e85ab8b49962ed0b5c | 76a539da4a8705daeacaddd4dad8c2c4bb60ad97 | Implementer: ApolloRetention06; PLAN_CONFLICT C-06-01 before edits; no product commit or tests |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
