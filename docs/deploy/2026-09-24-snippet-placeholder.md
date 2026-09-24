# Reply snippet placeholder validation hotfix

User authorized implementation and immediate production deployment.

## Behavior

- Reply snippet originals and variants accept known bare placeholders, including `${primaryResearchField}`.
- Frontend shows a non-blocking missing-default notice; insertion uses a bare token in these editors.
- Unknown keys, broken tokens, blank explicit defaults, empty variants and duplicate variants remain invalid.
- QA validation remains strict. Existing template required-key derivation and selected-variant send gate are unchanged.
- No database migration or configuration change.

## Validation

- All frontend tests: 1,155 passed (`node --test src/test/js/*.test.js`).
- Backend: 148 passed across ReplySnippetServiceTest, ContentVariantServiceTest, PersonalizationGateServiceTest, MailComposeTemplateServiceTest and QaMatchServiceTest, under JDK 11.
- Added create/update persistence checks for bare original/variant placeholders, invalid-token rejection before writes, QA isolation, non-blocking editor notices and hidden-variant validation.
- `git diff --check` passed.

## Deployment

Applied 2026-09-24 14:11:41 Asia/Shanghai to `150.158.92.103`.

Patched the existing production WAR with the three compiled service class families and `app.js`, `index.html`, `styles.css` (17 entries). Unrelated WAR entries retained. Included the earlier snippet dialog contrast hotfix so redeployment preserves it.

Backup and receipt: `/root/talent-deploy/snippet-placeholder-20260924/`.

- `original.war`: original WAR before this deployment.
- `rollback.war`: rollback package including the previously deployed static contrast fix.
- `patched.war`: new deployment package.
- `receipt.json`: before/after file hashes and deployment timestamp.

Deployed WAR SHA-256: `76df18f93f63346292b0380bf552f0659b6e53f5b6679918a52a17ff7163da7d`.

No active task executions were present at switch time. Tomcat redeployed the application; startup completed at 14:12:01. All 17 exploded files match the release manifest. Homepage returned HTTP 200 and `/api/auth/me` returned the expected unauthenticated JSON. Frontend cache version: `20260924-snippet-placeholder`.

No production email sent or expert/snippet records created during verification. Functional save/gate behavior was covered by local regression tests, with production checked for startup, deployed bytecode/resource hashes and HTTP availability.
