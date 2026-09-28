# Fast-P Ledger — master: docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md

- Status: RUNNING
- Master plan: docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md (commit 3237f07e694565bda5e0e2d6a453fc654d695014)
- Amendments: N/A
- Master base: f98e27c7538d091bfcdecfcb6ffc10360a35ba04
- Branch: fast/mail-open-tracking-120-second-filter
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mail-open-tracking-120-second-filter
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-09-28T02:22:00Z
- Current child: 01-backend
- Waiting role: IMPLEMENTER
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline

- 授权依据：显式 `$fast-p docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md` 调用（2026-09-28），授权本 master plan 的一次本地 worktree、本地 branch 与本地 commit；不授权 push/merge/rebase/squash/amend/reset/worktree 删除。
- `MASTER_BASE_SHA` = `f98e27c7538d091bfcdecfcb6ffc10360a35ba04` = `main` HEAD；worktree `weibo-talent-introduction-fast-mail-open-tracking-120-second-filter` 在该 SHA 上创建。
- 计划播种：`3237f07e694565bda5e0e2d6a453fc654d695014`（`docs(plans): seed mail open tracking 120 second filter plan`），docs/plans-only，非 amendment。计划 sha256 = `8b2f24faaa1a11e741c8b3ca9407ee6cfb89cf52ae9c0a1f5467cd62db4693c1`，与主工作区 `docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md` 字节一致。
- 单文件 master 拆分（本 run 记录，非计划修订）：child `01-backend` = 计划「阶段 1」task 1-3；child `02-ui` = 计划「阶段 2」task 4-6。计划「阶段 3」的验证命令按 child 分派为必需命令，不单独成 child。计划「变更文件清单」的 5 文件 = 两个 child 的授权文件全集，执行期不扩文件。
- 环境：JDK zulu-11（`/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`）；node v25.7.0；Docker 29.4.0（OrbStack）`docker info` exit 0。testcontainers 需 `-Dapi.version=1.40`（OrbStack 拒绝默认 client API 1.32），本次 baseline 与后续 IT 命令一律显式带该参数与 `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock`。
- 迁移：本 run 无迁移改动；计划 I-4 明确「无需数据库迁移」。
- 主工作区（`~/IdeaProjects/weibo-talent-introduction`）存在与本 run 无关的未提交改动（`docs/knowledge/**`、`docs/releases.json`、discovery 三个 Kotlin 文件、若干未跟踪 audits）；本 run 不纳入、不覆盖、不提交它们。
- 前端缓存键基线：`index.html` 11 处 `?v=20260926-discovery-repair`（`:11-15`、`:2310-2315`），`src/test` 内无该字面量引用。

## Baseline Commands

| Command | Exit | Result |
|---|---|---|
| `node --test src/test/js/mailOpenTracking.test.js` | 0 | 8 tests / 8 pass / 0 fail（`baseline/js-single.txt`） |
| `node --test src/test/js/*.test.js` | 0 | 全量 JS 套件通过（`baseline/js-all.txt`） |
| `node --check src/main/resources/static/app.js` | 0 | 语法通过（`baseline/js-check.txt`） |
| `mvn -B -Dtest=MailOpenTrackingRepositoryIT -DmysqlIt=true -Dapi.version=1.40 test` | 0 | Tests run: 5, Failures: 0, Errors: 0（testcontainers MySQL 8.0.36，BUILD SUCCESS；`baseline/mvn-mysqlit.txt`） |
| `mvn -B -Dtest=MailOpenTrackingServiceTest,MailOpenTrackingControllerTest,SmtpMailDeliveryServiceTest test` | 0 | BUILD SUCCESS（`baseline/mvn-targeted.txt`） |

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| 01-backend | docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md | commit:3237f07e694565bda5e0e2d6a453fc654d695014 | none | 1 | LIGHT_PASS | 3237f07e694565bda5e0e2d6a453fc654d695014 | 84560652c3227cf95f50ddd12bd285c54ece96cb | 0 | — | 84560652c3227cf95f50ddd12bd285c54ece96cb | 5df5d0e07921ae15f94e28ac0aa5629bda33b911 | 单文件 master 的阶段 1（task 1-3）：120 秒谓词 + 列表/汇总/详情状态 + MySQL 边界 IT；授权 2 文件；实现者 Impl01Backend，验证者 Verify01Backend 四门全 PASS（IT 6/0、定向 47/0），无 AUTO_FIX；RECORD_ONLY O-1/O-2 见 verify-log |
| 02-ui | docs/plans/2026-09-28/mail-open-tracking-120-second-filter.md | commit:3237f07e694565bda5e0e2d6a453fc654d695014 | 01-backend | 1 | LIGHT_VERIFYING | 84560652c3227cf95f50ddd12bd285c54ece96cb | e0b002076b92d3e1e543040fb5640585fc2869aa | 0 | — | e0b002076b92d3e1e543040fb5640585fc2869aa | — | 单文件 master 的阶段 2（task 4-6）：S-1/S-2 文案 + 缓存键 + JS 断言；授权 3 文件；实现者 Impl02Ui，验证者 Verify02Ui |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
