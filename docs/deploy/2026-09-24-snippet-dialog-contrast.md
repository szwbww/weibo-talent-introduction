# Reply snippet dialog contrast hotfix

User authorized the style repair and immediate production deployment.

Root cause: `.modal-panel` inherited the shared `--panel-bg: rgba(255,255,255,0.55)` and `.panel` blur. The modal scrim showed through the editor and sticky footer. The existing muted label color was #94a3b8.

Change: scoped `#replySnippetModal .modal-panel` overrides set panel backgrounds to opaque white, muted labels to #64748b, and remove panel backdrop blur. No JS, template content, variant selection, or database changes. All 11 existing versioned resource URLs use `20260924-snippet-dialog-contrast`.

Verification:
- Reproduced against extracted production-matching modal DOM/CSS in a local browser. Before: rgba(255,255,255,0.55); after: rgb(255,255,255), including footer.
- Related JS tests: 52 passed.
- Complete frontend JS suite: 1152 passed, 0 failed.
- Production browser reload loaded the new cache key and confirmed panel/footer white and labels rgb(100,116,139). The verification browser was unauthenticated; visual comparison used the actual modal markup in the local fixture.

Deployment: static hotfix to `/opt/apache-tomcat-9.0.71/webapps/talent/WEB-INF/classes/static/{styles.css,index.html}` on 150.158.92.103. Checked old hashes before mutation; backed up both files. No service restart, WAR replacement, or application build deployment. Source files contain the fix for the next normal WAR build; redeploying an older WAR would revert this hotfix.

Backup: `/root/talent-static-backups/snippet-dialog-contrast-20260924-110839`.

Deployed SHA-256:
- styles.css: ccbc678a17330f0a6cbde72af48f8564a262e3b2e3581edfbd64c9831ce0f651
- index.html: 9ce2fa2cfcaa225428de104dbc424e7f894f74990df488ec61c23c3caa1120ce

Rollback: restore both backed-up files only after checking no later release has replaced them. Existing docs/releases.json changes belong to another task and were not modified by this hotfix.
