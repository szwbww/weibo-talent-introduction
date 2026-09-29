# Child 01 — Pause Evidence (Epoch 1)

- Paused at: 2026-09-29 (controller, fast-p run `2026-09-29-discovery-repair-00-master`)
- Child state: `PAUSED_FOR_HUMAN`
- Child base: `b9ec45b008f5c4f965679b99575db0ea8fe50731`
- Product code head (epoch 1): `b944ccf0f4c1b395706a6add6d869ff59eed1a75` — `feat(fast-p): implement 01`, changing exactly the 7 authorized files
- Implementer: agent `Child01Implementer` (fresh task subagent), verdict `PLAN_CONFLICT`
- Implementer report: `children/01/execution.md`

## Conflict

Child plan T-2 requires `DiscoveryIdentity.EXTRACTION_VERSION` to be raised above the current `20261002` (`docs/plans/2026-09-29/discovery-repair-01-contact-ownership.md`, 现状审计 P-2: "仅增加 EXTRACTION_VERSION（当前 20261002，实施取更大值）").

The child's own required command names `DiscoveryIdentityTest`, and that test pins the literal value:

```
src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt:54
        assertEquals(20261002, DiscoveryIdentity.EXTRACTION_VERSION)
```

That file is **not** in child 01's authorized-file list (the plan lists 7 files; the pin-owning test is not among them). Updating the pin to `20261003` is therefore outside the child's contract and needs a human-approved plan amendment.

## Command evidence at `b944ccf`

- `mvn -Dtest=SourceAuthorEmailResolverTest,ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryIdentityTest test` → exit 1, 251 tests, 1 failure, 0 errors, 0 skipped.
  - `SourceAuthorEmailResolverTest` 24/0/0/0, `ExpertDiscoveryServiceTest` 173/0/0/0, `DiscoveryPipelineServiceTest` 47/0/0/0, `DiscoveryIdentityTest` 7/1/0/0.
  - Sole failure: `expected: <20261002> but was: <20261003>` (the pin).
- Same command scoped to the other three classes after the commit → exit 0, 244 tests, 0 failures, BUILD SUCCESS.
- `git diff --check` exit 0; ZIP copy to `src/test/resources/discovery/ownership-20260929.zip` byte-identical (sha256 `d250220a9bd94519e3b15e36cfd48305b976dedeb29f1a57d6fc807ee9aa19e7`); test-side diff has 0 deleted lines.

## Precedent for the pin update

`65dd09ef2dda58f18d4df977d88a30580643f3fe` (`fix(discovery): reject ambiguous PDF email ownership`) bumped the same pair in one commit:

```
DiscoveryIdentity.kt:        - 20261001  + 20261002
DiscoveryIdentityTest.kt:    - 20261001  + 20261002
```

So the pin is an established mechanical companion of the constant, but it still requires an authorized-file amendment in this run.

## Requested amendment (requires explicit human approval)

- Plan: `docs/plans/2026-09-29/discovery-repair-01-contact-ownership.md`
- Change: add an 8th authorized file `src/test/kotlin/com/weibo/talentintroduction/expert/domain/DiscoveryIdentityTest.kt` with action 同步 EXTRACTION_VERSION pin（20261002 → 20261003）in 变更文件清单.
- Master rule: `M-1 / 01-T-2`
- Reason: T-2 mandates the constant bump and the required command already names this test, but the file was omitted from the authorized list; only the literal pin line changes.

## Resume plan (after approval)

1. Commit the amended child plan on its own; append amendment row `A1` (Before/After commit identities, master rule, reason, `HUMAN:` approval).
2. Resume child 01 in execution epoch 2 with `fix_round=0`: implementer updates only the pin line, reruns the required command, commits `feat(fast-p): implement 01 epoch 2`.
3. Dispatch a fresh lightweight verifier for epoch 2 over `b9ec45b..<epoch-2 code head>`.

## Not done

No verification was performed for epoch 1; no fix rounds consumed; children 02–04 never started. Product behaviour beyond the version constant and the pin is implemented and green in the three other required classes.
