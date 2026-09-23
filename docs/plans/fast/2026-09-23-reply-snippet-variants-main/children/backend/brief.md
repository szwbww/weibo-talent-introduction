# Child backend — approved implementation brief

- Exact plan: `docs/plans/2026-09-23/01-reply-snippet-variants-backend.md`
- Plan identity: `commit:73bc40d5b5623d9a71b0c9ff8e5a5990f3e3ae18` (content SHA-256 `313cc10082baaa5767e650e53d8e85523684b5fb583cdd25f37e2d02ad355416`)
- Read that complete plan before coding; it is the full acceptance contract.
- Product base: `24e8439480581fa6b6e5a81b5579e7b8ce393206`.
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-reply-snippet-variants-main`; branch `fast/2026-09-23-reply-snippet-variants-main`.
- Repository rules: Kotlin/Spring Boot 2.7, Java 11 at `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`; MySQL persistence is Spring Data JDBC; add a new Flyway migration, never edit applied migrations. `docs/design.md` is mandatory for mail/conversation logic if touched.

## Authorized files (exactly these nine)

1. `src/main/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantService.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/template/domain/MailComposeTemplate.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/template/controller/MailComposeTemplateController.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`
5. `src/main/resources/db/migration/V136__add_compose_subject_snippet_id.sql`
6. `src/test/kotlin/com/weibo/talentintroduction/variant/service/ContentVariantServiceTest.kt`
7. `src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt`
8. `src/test/kotlin/com/weibo/talentintroduction/template/repository/MailComposeTemplateBlockRepositoryIT.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`

Do not modify any other product/test file. If V136 is already occupied, or satisfying a requirement needs an unlisted file or plan decision, stop with PLAN_CONFLICT; do not amend the plan.

## Required invariants and downstream contract

- I-1: one public candidate-pool/selection seam in `ContentVariantService`; candidates = original plus enabled variants in repository order; uniform independent selection for every template snippet occurrence and every new generation; duplicates allowed; no variants returns original. `QA_RULE`, other legacy resolve behavior and QA deterministic algorithm stay unchanged. Keep rendering signatures compatible; formal `variantSeed` no longer fixes snippet selection. `previewDraft.variantIndex` stays only as an explicit legacy preview selection input and goes through the public selector.
- I-2: nullable `subjectSnippetId: Long?` / `subject_snippet_id BIGINT NULL` is the authoritative subject reference. Null means custom `subject`; reference writes store source text as display snapshot only, read latest snippet content for render, and never fall back to stale snapshot. Carry ID through Request→Command→create/update→Detail→previewDraft. Old requests remain custom. `subjectVariants` stays ignored/cleared.
- I-3: ID must reference an existing enabled snippet. Every candidate and final rendered subject is nonempty, <=255 chars, no CR/LF. Reject rather than truncate, normalize, or retry. Preserve current placeholder gate for custom subjects.
- I-4: select each subject/body raw once before rendering/checking; returned raw text, text/HTML and preview descriptions must derive from same selections. Candidate-based required key union/intersection follows plan; send gate checks selected raw texts exactly. Listing/gate enumeration must not sample.
- I-5: selection occurs in render, never again in SMTP/retry/write for the same `ComposedMail`. A fresh batch compose can choose a fresh sample. Preview is a separate sample and remains read-only.
- I-6: retain `render/renderByCode` seed parameters, QA determinism, legacy explicit preview index, old API behavior and maximum-pool-size semantics. No AI/RAG framework changes.
- Downstream interface required by child `frontend`: nullable `subjectSnippetId` accepted/returned by template APIs and accepted by both preview-draft paths; custom subject sends explicit null; when preview index is absent the backend samples through public selector.

## Required commands (fresh after final implementation)

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DskipNodeTests=true -Dtest=ContentVariantServiceTest,MailComposeTemplateServiceTest,TemplateVariantContextTest,ComposeTemplateGateControllerTest test
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -DskipNodeTests=true -Dapi.version=1.40 -Dtest=MailComposeTemplateBlockRepositoryIT,FlywayMigrationIntegrationTest test
```

The second command uses this workstation's OrbStack Docker socket/API settings: default discovery was proven to send API 1.32 while the server requires 1.40. It exercises the same plan-required Maven test target. If this configured command remains unavailable, report the infrastructure failure exactly; never substitute mocks for migration evidence. Preserve documented P-1..P-4 and all T-1..T-4 and acceptance criteria in the complete approved plan.

Baseline exposed two existing issues in the authorized `MailComposeTemplateBlockRepositoryIT`: without the explicit `spring.flyway.placeholder-replacement=false` override, Flyway treats `${senderEmail}` in V2 as a placeholder; with that override, the full V135 schema migrates but `createSchema()` fails dropping `mail_compose_template` while `batch_send_task_config` still references it by FK (`fk_batch_send_task_config_template`). Other MySQL repository ITs explicitly set the placeholder property. Keep production files out of scope. Add the property to this authorized test class and drop the referencing table before its parent as needed for the manual schema fixture; do not disable FK checks or suppress/mock migrations. This fixture maintenance is necessary to execute the plan-required JDBC/migration verification.
