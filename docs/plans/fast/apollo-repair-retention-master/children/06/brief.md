# Child 06 approved execution brief

Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-repair-retention-master
Branch: fast/apollo-repair-retention-master
Exact child plan: docs/plans/2026-10-21/apollo-repair-06-promotion-reporting.md
Plan identity: sha256:908af3147608b328ab9d90b755b6b97e4cad369fd324cbb3be6d0b089779e48b
Read the exact child plan completely, including Authorized Files table, invariants, commands and acceptance scenarios. Its normative evidence is docs/plans/2026-10-21/apollo-repair-retention-evidence.md (read complete E-6/E-7); master constraints: docs/plans/2026-10-21/apollo-repair-retention-master.md; receipts: docs/plans/2026-10-21/apollo-repair-retention-code-receipts.md.
Only the child plan's source/test file whitelist is authorized. No approved amendments. Prior children interfaces are in this shared worktree; do not inspect or implement later plans.
Set JAVA_HOME=/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home and prepend its bin to PATH; DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock. Run exact plan commands after changes, no mid-flight build/test/formatter. Exercise changed runtime path via throwaway smoke or existing real runtime scenario; report limits honestly. Do not deploy or write production; production baseline already frozen by controller. Human acceptance is deferred; never mark it passed.
Use execute-p. All behavioral/file-scope ambiguities must return PLAN_CONFLICT, never amend plan. Product commit excludes fast-p artifacts. Documentation evidence belongs in execution.md (authorized workflow artifact), not new product docs outside whitelist. Output report includes actual changed files, commit, command/exit evidence, invariant/smoke evidence, downstream interfaces and limitations.

## Epoch 2 — Approved A1 resume

- This section supersedes epoch1's plan identity and “No approved amendments” above; prior text is retained as history.
- Governing plan identity: commit:c337ee2aec8a7fc5f36584afeb0d00464ca5103e.
- Approval: HUMAN:批准 继续; 2026-10-21. A1 authorizes ExpertDiscoveryServiceTest.kt only for constructor caller migration and necessary mock, preserving assertions.
- Product base: a489c28e93e0357e26f6e5e85ab8b49962ed0b5c. Epoch1 stopped before product edits; do not replay completed children01–05.
- Use JAVA_TOOL_OPTIONS=-Dapi.version=1.44 with the JDK/Docker environment above.
- Preserve O-04-01 as RECORD_ONLY; no unrelated IntegrationTest repair.
- Append epoch2 results to execution.md; preserve epoch1 logs. No automatic aggregate review or whole-system suite; human acceptance remains pending.
