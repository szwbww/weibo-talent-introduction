# Fast-P Child Brief — 01

- Child: 01
- Plan: docs/plans/2026-08-24/01-expert-rnd-classification-core.md
- Plan identity: commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a
- Depends on: none
- Base: c004a18d675b86040597f17f5911aa52f718d156
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification

## Global constraints (binding, from master plan docs/plans/2026-08-24/00-expert-rnd-classification-master.md)

1. JDK 11 mandatory. Use `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command; bare mvn fails the build on newer JDKs.
2. Master invariants M-2 and M-3 bind this child: classification normalization/evidence/scores/priority/type/sendable live ONLY in `ExpertClassificationService`; each of the three ES indexes gets exactly ONE new top-level object `expertClassification` (no root-level `sendable`/`expertType` second source of truth).
3. No backfill, no management API, no send filtering, no incremental scheduling, no frontend in this child. Application startup must NOT write classifications. `orcid_info_*` mappings get only the backward-compatible `expertClassification` addition.
4. Full regression gate: `JAVA_HOME=... mvn test` must end `BUILD SUCCESS` with `Tests run: N, Failures: 0, Errors: 0` (baseline at master base: green — see ledger).
5. Git: commit locally only, exactly one implementation commit with message `feat(fast-p): implement 01`. Never push, merge, rebase, amend, or rewrite history. Exclude fast-p report/log files (docs/plans/fast/) from the implementation commit; the controller commits evidence separately.
6. Do not review or implement later children (02/03/04). Do not repair unrelated behavior. Skip formatters/linters and project-wide suites beyond the required commands.

## Authorized files (11; modify nothing else)

1. src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertClassification.kt (NEW, T1)
2. src/main/kotlin/com/weibo/talentintroduction/expert/domain/ExpertProfile.kt (T1)
3. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationService.kt (NEW, T2)
4. src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchService.kt (T3)
5. src/main/resources/es/orcid_info_raw.json (T3)
6. src/main/resources/es/orcid_info_candidate.json (T3)
7. src/main/resources/es/orcid_info_application.json (T3)
8. src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertClassificationServiceTest.kt (NEW, T2)
9. src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertSearchServiceTest.kt (T3)
10. src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexServiceTest.kt (A1: pin 32→33, per-field PUT count)
11. src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt (A1: EXCLUDED_NOISE_SITES ExpertSearchService.kt line 431→445, context unchanged)

## Required commands (run all; from plan 验收标准 + master plan)

- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertClassificationServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertSearchServiceTest
- JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test   (full regression; must contain Tests run: N, Failures: 0, Errors: 0; exit 0)
- git diff --check   (clean)

## Downstream interfaces (contracts children 02/03/04 consume)

- `ExpertType` enum: exactly `PRODUCTION_RND / ACADEMIC_RND / HYBRID_RND / SERVICE_ONLY / OUT_OF_SCOPE / UNKNOWN`. `sendable=true` iff type in SENDABLE_TYPES (first three); `ExpertClassification` constructor must NOT accept a caller-supplied sendable — JSON serialization still emits `sendable`.
- `ExpertClassification`: `type(keyword)`, `sendable(boolean, derived)`, `productionScore(integer)`, `researchScore(integer)`, `positiveEvidence(keyword[])`, `negativeEvidence(keyword[])`, `version(keyword)`, `sourceFingerprint(keyword)`, `classifiedAt(date)`; `version == "rnd-v1-2026"`; classifiedAt comes from an injectable `Clock`.
- `ExpertClassificationService.classify(profile: ExpertProfile): ExpertClassification` — deterministic pure function (policy rnd-v1-2026, recent-paper cutoff year 2021, production threshold 50, research threshold 50); sourceFingerprint = SHA-256 over normalized six text fields + three numeric fields.
- `ExpertProfile` gains tail field `expertClassification: ExpertClassification? = null` (nullable default — existing positional/named constructions must keep compiling).
- Three ES mappings declare identical `expertClassification.properties` (no `enabled:false`); `classifiedAt` format `yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis` matching existing date fields.
- `ExpertSearchService.sourceFields()` includes `expertClassification`; `toExpertProfile()` parses the object explicitly — missing/null fields → null; unknown `type` → log warn AND treat whole classification as null (fail closed, never silently map to UNKNOWN).
- Classifier must NOT read email/nationality/gender; degree is not a clinical criterion. Exact clinical/medical-domain/white-list word lists and scoring in plan Task 2 are normative.

## Plan text (exact approved content; authoritative)

Read the committed plan file: docs/plans/2026-08-24/01-expert-rnd-classification-core.md
Follow its 需求描述 / 关键不变量 I1-1..I1-5 / 现状审计 / 实现方案 T1-T3 / 变更文件清单 / 验收标准 exactly. Do not re-derive the classification rules; plan Task 2 items 1-10 are normative (incl. exact word lists, scoring, priority order, evidence codes).

## Amendment A1 (approved 2026-08-24, epoch 2)

Authorized the two guard-pin refreshes above (files 10-11): `ExpertIndexServiceTest.kt` RAW per-field PUT count 32→33; `OperatorStatusWriteSeamGuardTest.kt` EXCLUDED_NOISE_SITES `ExpertSearchService.kt` line 431→445 (context `operatorStatus = source.nullableText("operatorStatus")` unchanged). Zero assertion-semantics change. Epoch 1 implementation commit a8cf1723 stays; the remaining work is exactly these two refreshes plus a green full regression.
