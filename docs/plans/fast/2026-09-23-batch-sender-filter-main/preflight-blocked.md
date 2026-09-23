# Fast-P Preflight Record — BLOCKED_PREFLIGHT

- Outcome: BLOCKED_PREFLIGHT
- Invocation: explicit `$fast-p docs/plans/2026-09-23/00-batch-sender-filter-main.md` (2026-09-23)。按 fast-p 契约，该调用授权本 master plan 的一次本地 worktree、本地 branch 与本地 commit；不授权 push/merge/rebase。
- Master plan: `docs/plans/2026-09-23/00-batch-sender-filter-main.md` (sha256 b535d9388636dd7aa9a30180717770b5d62a46e1986a087d10ee2cc53adf4a16)
- Child 01: `docs/plans/2026-09-23/01-batch-sender-filter-backend.md` (sha256 03cff279985163dcb2aaf780277d9f55ea58428d54503a00f934390eb1e863dc)
- Child 02: `docs/plans/2026-09-23/02-batch-sender-filter-frontend.md` (sha256 9d1ce050e13180d4df4e4e4b522d04cc3774bc1992a269a874cc4e1806dc9cc8)
- Candidate master base (NOT used): 9237d6f573335d1624217cbc5501f68a6f52b97b (`main` HEAD)
- Worktree / branch created: none
- Product changes / commits: none
- Blocking gate: MAIN `G-0`（外部基线门槛）未满足，即 `M-5`（共享收件箱与迁移顺序不得冲突）

## 阻塞事实（命令与输出）

| # | 事实 | 命令 | 观测结果 |
|---|---|---|---|
| 1 | 仓库及全部 worktree 中不存在 V134 | `ls src/main/resources/db/migration \| sort -V \| tail -4`；`git log --all --diff-filter=A -- 'src/main/resources/db/migration/V134*'`；`find /Users/lukai/IdeaProjects -maxdepth 6 -name "V134__*" -path "*db/migration*"` | 最高版本 `V133__create_discovery_paper_queue.sql`；`V134` 无任何提交、无任何磁盘文件 |
| 2 | 共享收件箱 01 未实施、未验证 | `docs/plans/fast/2026-09-23-shared-inbox-master/ledger.md`（worktree `...-fast-2026-09-23-shared-inbox-master`） | `Status: RUNNING`，`Current child: c1`，c1 `State: PENDING`；branch `fast/2026-09-23-shared-inbox-master` 停在 `daabfdc`（仅计划播种提交，无产品代码） |
| 3 | 共享收件箱 01 正是 V134 的拥有者 | `01-shared-inbox-configuration.md:81,99` | 该计划文件清单第 1 项为 `src/main/resources/db/migration/V134__shared_inbox_owner.sql` |
| 4 | 本组子计划禁止在 V134 之前创建 V135 | `01-batch-sender-filter-backend.md:3` | 「共享收件箱计划预留 V134；本计划的 V135 仅在 V134 已进入同一 Flyway 序列后执行。实施前重新核对最高版本与工作树，若版本冲突先修订文件名和本文，不能抢号」 |
| 5 | MAIN G-0 明确要求 01 先实施并验证 | `00-batch-sender-filter-main.md` G-0 / M-5 | 「先确认共享收件箱计划 01 已实施并验证，V134 已在代码仓库与测试环境的 Flyway 序列……若 01 尚未实施，本组维持计划状态；要改执行顺序，先重写本组两个子计划的迁移与共享文件约束」 |
| 6 | 无既有本 master plan 的 fast-p run 可续跑 | `git worktree list` + 各 worktree `docs/plans/fast/*batch-sender*`；`git branch --list '*batch-sender*'` | 无匹配；本计划文件在 `main` 中仍是 untracked（`?? docs/plans/2026-09-23/`） |

## 未受影响的前置检查（在 `main @ 9237d6f` 上成立）

| 检查 | 观测 |
|---|---|
| `index.html` 资源键审计 | `grep -c "20260922-discovery-continuous" src/main/resources/static/index.html` = 11，与前端子计划一致 |
| 固定键测试 | `grep -rl <key> src/test` = 0 命中，与前端子计划一致 |
| 新字段尚未存在 | `sender_account_codes_json` / `senderAccountCodes` 在 `src/**`（kt/sql/js/html）零命中 |
| 子代理能力 | `task` 工具可用（scout/reviewer/security-reviewer/task/sonic），Required Agent Gate 具备 |
| 授权 | 显式 `$fast-p` 调用即本 run 的授权依据（与 `...-shared-inbox-master` 的既有先例一致） |

## 结论

以当前基线执行本组 01 会创建 `V135__add_batch_sender_account_codes.sql`，而同一 Flyway 序列中 `V134` 尚不存在——直接违反 MAIN `G-0`/`M-5` 与后端子计划第 3 行的「不能抢号」约束，并会使 `FlywayMigrationIntegrationTest` 的「最新版本」断言建立在错误序列上。此外前端 02 要求以共享收件箱 01 的前端最终基线（`index.html`/`app.js` owner 配置）为起点，该基线同样尚不存在。fast-p 不允许在缺少可靠 Git 基线时开跑，故在 Set Up Once 之前返回 `BLOCKED_PREFLIGHT`：未创建 worktree/branch，未做任何产品改动，未产生任何 commit。

## 解除条件（任一，须人工决定）

1. 先完成共享收件箱 run 的 c1（V134 进入 Flyway 序列并被验证），再重新调用 `$fast-p docs/plans/2026-09-23/00-batch-sender-filter-main.md`。届时 `MASTER_BASE_SHA` 必须取包含 V134 的基线（共享收件箱 branch 的产品 head，或其合入 `main` 后的 HEAD），且不得并行在 `main @ 9237d6f` 上实现前端 02。
2. 由人工批准改写 MAIN `G-0`/`M-5` 与两份子计划的迁移号/共享文件约束（例如放弃共享收件箱串行基线、把本组迁移改为当前最大版本之后的 V134），改后重新调用 `$fast-p`。计划改写本身属于合同变更，需要显式人工批准并记录（fast-p `## Amendments` 行的 `HUMAN:` 批准）。

## 复跑说明

本记录是唯一产物（untracked，位于 `main` 工作树，路径前缀 `docs/plans/`，无产品语义）。不存在 ledger/branch/worktree，无需状态对齐；解除阻塞后按上述条件 1 或 2 重新开跑即可。

## Preflight Re-check（第二次显式调用，2026-09-23）

- 调用：再次执行 `$fast-p docs/plans/2026-09-23/00-batch-sender-filter-main.md`。
- 结论：仍为 `BLOCKED_PREFLIGHT`；阻塞条件逐项复检后未变化。

| # | 复检项 | 命令 | 观测结果 |
|---|---|---|---|
| 1 | 三份计划身份未变 | `shasum -a 256 docs/plans/2026-09-23/0[012]-batch-sender-filter-*.md` | 主计划 `b535d9388636dd7aa9a30180717770b5d62a46e1986a087d10ee2cc53adf4a16`、后端 `03cff279985163dcb2aaf780277d9f55ea58428d54503a00f934390eb1e863dc`、前端 `9d1ce050e13180d4df4e4e4b522d04cc3774bc1992a269a874cc4e1806dc9cc8`，与首次记录一致 |
| 2 | 最高迁移版本仍为 V133 | `ls src/main/resources/db/migration \| sort -V \| tail -5`；`git log --all --oneline --diff-filter=A -- '.../V134*' '.../V135*'` | 最高 `V133__create_discovery_paper_queue.sql`；V134/V135 在所有 ref 与所有 worktree 磁盘上均零命中 |
| 3 | 共享收件箱 01 仍未实施 | `docs/plans/fast/2026-09-23-shared-inbox-master/ledger.md`；该 worktree 的 `children/` | `Status: RUNNING`、`Current child: c1`、c1 `State: PENDING`；branch head 仍是计划播种提交 `daabfdc`；`children/` 为空目录 |
| 4 | `main` 基线未变 | `git rev-parse HEAD` | `9237d6f573335d1624217cbc5501f68a6f52b97b`（与首次记录相同），无新合入 |
| 5 | 工具链可用（非阻塞项） | `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock docker version`；zulu-11 路径 | Server 29.4.0；`/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` 存在，解除阻塞后 Required Commands 可执行 |
| 6 | 本组计划仍为 untracked | `git ls-files docs/plans/2026-09-23/` | 空输出；真正开跑时须先做计划播种提交（同共享收件箱 run 的 `daabfdc` 做法），否则 `Plan identity` 无法记录为 `commit:<SHA>` |

- 复检期间未创建 worktree/branch，未改产品代码/测试，未产生 commit。
- 解除条件仍为上一节 1 或 2；两者都属于人工决定（`## Amendments` 需要 `HUMAN:` 批准），执行者不得自行改写已批准计划或抢号 `V134`。
