# Fast-P Ledger — master: docs/plans/2026-08-24/00-expert-rnd-classification-master.md

- Status: RUNNING
- Master plan: docs/plans/2026-08-24/00-expert-rnd-classification-master.md (commit 3a4162c9c458f899470f59ac6e1a07b9ba748b3a)
- Amendments: A1
- Master base: c004a18d675b86040597f17f5911aa52f718d156
- Branch: fast/expert-rnd-classification
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-rnd-classification
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-08-24T15:43:44+0800
- Current child: 01
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

Approved execution start: master base `c004a18d675b86040597f17f5911aa52f718d156` (branch `main`). Plans seeded on the fast branch in commit `3a4162c9c458f899470f59ac6e1a07b9ba748b3a` (docs-only, expert-rnd-classification plan files under `docs/plans/2026-08-24/`).

Baseline command results, run in the retained fast worktree at the seed commit (docs-only):

- `git diff --check` -> exit 0 (clean)
- `node --check src/main/resources/static/app.js` -> exit 0 (APPJS_OK)
- `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` -> exit 0, `Tests run: 2739, Failures: 0, Errors: 0, Skipped: 4`, `BUILD SUCCESS` (total time 02:19 min). Baseline fully green (no pre-existing failures to compare against). Node suite within mvn: pass.

JDK 11 (zulu-11) verified: `openjdk version "11.0.15"`. Node `v25.7.0`.

Plan family facts (from master plan):
- Master invariants M-1..M-6 bind all children; each child carries its own I<child>-<n> invariants.
- Ordered execution 01 -> 02 -> 03 -> 04; production release is one combined release after all four; 03 fails closed, so INTRODUCTION targets are 0 before backfill.
- Global constraints: JDK 11 mandatory (`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` for every mvn command); full regression gate `mvn test` must end BUILD SUCCESS; shared files across children must be modified serially by one writer at a time; no push/merge/rebase/amend; fast-p evidence excluded from implementation commits.

## Children

| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 01 | docs/plans/2026-08-24/01-expert-rnd-classification-core.md | commit:10ec2d4f64806f07979e858022dae7a2569c7894 | none | 2 | PENDING | c004a18d675b86040597f17f5911aa52f718d156 | a8cf1723df1403682a04babbf213f3c17a8ccc1b | 0 | — | — | — | epoch 1 PLAN_CONFLICT resolved via A1; epoch 2 resumes with guard pin sync; base = master base |
| 02 | docs/plans/2026-08-24/02-expert-rnd-classification-backfill.md | commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a | 01 | 1 | PENDING | — | — | 0 | — | — | — | base = 01 code head |
| 03 | docs/plans/2026-08-24/03-expert-rnd-send-gate.md | commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a | 01 | 1 | PENDING | — | — | 0 | — | — | — | plan 前置: 01; master order bases through 02 code head; production send gate needs 02 CANDIDATE backfill (M-6) |
| 04 | docs/plans/2026-08-24/04-expert-rnd-incremental-classification.md | commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a | 01,02,03 | 1 | PENDING | — | — | 0 | — | — | — | 前置: 01~03 完成 + 02 CANDIDATE 回填/抽样通过 |

## Amendments

| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-08-24/01-expert-rnd-classification-core.md | commit:3a4162c9c458f899470f59ac6e1a07b9ba748b3a | commit:10ec2d4f64806f07979e858022dae7a2569c7894 | 计划 01 验收标准「回归：运行 mvn test」vs 变更文件清单（9 文件） | T3 强制三份 mapping 同构新增 expertClassification：RAW 声明字段 32→33 使 ExpertIndexServiceTest per-field PUT 计数 pin 必然过期；ExpertSearchService 新增解析/logger 导入使 seam guard pin 431→445 必然过期，任何合规实现都无法保绿；按 guard 自带规程（:130）与 expert-reachability A1-A3 先例仅同步计数 32→33 与行号 431→445，context 不变 | HUMAN:ask 选项「Approve amendment A1」2026-08-24 |
