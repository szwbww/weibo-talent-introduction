# Repair Plan: manual-reply-delay-master

Status: DRAFT — HUMAN APPROVAL REQUIRED
Baseline plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/2026-11-04/manual-reply-delay-master.md
Baseline identity: SHA256 4fa1f45fc2830aea7c138e3da44a60d5ea767fa48a01239ca01ed01989a89a2e; A1–A32 recorded amendments remain governing.
Verification report: AGGREGATE-20261106-E2, Epoch 2, FAIL / PROGRESSING; returned complete report for controller append to /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/review/manual-reply-delay-master/machine-verification.md. Original return retained at /tmp/aggregate-epoch2/report.md.
Implementation boundary: a224623c8dc17bdad1ce80e1c4391637bf7ee5ca → fc2750cd7ad4ae5200195036d874aeeb8b5ae400; selected evidence HEAD 6a2cc31f60c34819bed7edeeebf9300921d2ed19.
Execution worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master
Execution branch: fast/manual-reply-delay-master

本文件替换同路径上已经实施的九文件修复计划，历史由 Git 保留。旧执行批准只对应旧 SHA256 2faeacade4c57d7a03c3cfc211edc85be0da0f3beae00ba904fe1d6d64145e83，不批准当前新草案。禁止执行、改产品或创建新 repair 文件，直至用户明确批准当前内容。

## Objective

页面真实隐藏后清除旧撤回/重发确认的显示。回前台且服务端任务未改变时，旧确认不残留，当前合法操作仍可重新打开并明确执行一次。

## Findings in Scope

| Finding | Severity | Requirement | Root Cause |
|---|---|---|---|
| V-5 | P1; REPAIRABLE | master O-2/I-9、07-source C-7、08 must-not-change/I-4 的 hidden 清确认合同 | stopDelayPolling 清 closure confirm 但未同步已有 footer DOM；resume 的 serverNow-excluded unchanged 比较不重绘，留下点击无效的原确认 |

独立真实证据：/tmp/aggregate-epoch2/runtime/visibility-app4-failure.json 的 results[0]；32次真实 hidden 采样、隐藏期间0会话请求；回前台实际GET后 confirm1，原生点击后 confirm1/POST0。其后 logout204 JSON探针错误单独披露，不掩盖已完成的实际行为。前一 visibility-app2 也独立复现。源码 mailbox-chat.js:1239–1245、1293–1298、10028–10037；与修复前 stopDelayPolling 字节相同，属于先前 B-1 缺证据所掩盖的既存实现缺陷，不是本轮后端/恢复修复回归。

## Findings Excluded

| Finding | Reason |
|---|---|
| V-1 | RESOLVED；原混合排序规则下真实 GET200、Query10通过；禁止再改SQL/排序规则 |
| V-2 | RESOLVED；两入口真实超限413/零副作用、E2E30通过；禁止再改快照错误映射 |
| V-3 | RESOLVED；WriteSeam1/1；禁止改行号白名单/测试守卫 |
| V-4 | RESOLVED；双独立浏览器保留新普通稿及有序附件，显式冲突在旧编辑器挂载前；禁止改恢复/草稿内容路径 |
| B-1 | RESOLVED证据缺口；当前真实 Chrome hidden/resume 已取得，不作为待修代码项 |
| O-1/O-2 | 无关既有 Subject/size100 断言冲突，原失败与MASTER_BASE因果一致；不修、不排除为伪PASS |
| O-3 | 可选 InboundPendingOwnerIT 反射mock/target172夹具问题；不扩围 |
| O-4–O-7 | 可选tzdata、文档空白、已有文本MIME换行、历史证据散文hash笔误；均非本修复范围 |
| 人工 A1–A12 | 仍PENDING；机器修复通过不产生人工接受或部署授权 |

## Unchanged Contract

- 服务端固定60000ms、期限/权限/来源/expectedRevision/CAS、worker与真实SMTP路径不改。无自动重试、无额外POST、无时间戳或时钟伪造。
- A31/A32保留：截止前已合法打开、上下文仍有效的确认可跨截止时间；到期后不能新开或dismiss后重开。隐藏清理不能被当作到期例外。
- busy动作仍最多一个POST；隐藏不能重发/撤销已提交的网络动作；401/403、owner/account/contact/job/revision/generation/unmount/logout规则保留。
- 普通原稿正文/格式/版本/有序附件、LEGACY冲突保护、APPROVED审核正文和上下文确认不变；不得因清理整页而覆盖编辑器内容。
- 原footer/两按钮/文案/aria、样式与移动44px合同全部保持；不新增class/id/CSS/dialog/模块。
- 1秒倒计时、2/5秒详情、5秒列表、performance单调偏移、隐藏暂停/前台立即读取、GET abort及迟到响应隔离保留。
- 20条既有版本资源的集合/顺序/URL形状保留；task-modal-runtime.js继续无版本；JS与测试不硬钉新cache key。

## Authorized Files

| File | Purpose |
|---|---|
| src/main/resources/static/mailbox-chat.js | 仅共享确认生命周期清理与现有footer显示一致性；保持当前内容/权限/轮询/异步隔离 |
| src/test/js/mailboxDelayedReply.test.js | 一个可区分V-5的现有行为回归，覆盖隐藏后可见确认消失及未变任务恢复后的合法重开 |
| src/main/resources/static/index.html | 仅既有20个资源version值同步更新为20261106-manual-reply-visibility，防缓存旧确认行为 |

无其他授权文件；不得修改后端、数据库、CSS、app/readonly、其他测试、批准计划、历史审查证据、知识库。仅本文件定义的执行交接允许独立的 repair-execution.md 证据追加与证据提交。

## Repair Tasks

### R-1: 让 hidden 清理同步当前确认显示

- Resolves: V-5。
- Root cause: 内部确认已清空，但既有可见footer未更新；未变服务端回读不触发重绘。
- Files: 上述三个精确文件。
- Change: 在共享现有生命周期中使确认内部状态与当前DOM同步清除；隐藏前已打开cancel/retry均不能在恢复后留下不可操作的旧确认。服务端仍WAITING且截止前时，恢复显示原合法操作，用户可重新打开并明确确认一次。复用现有局部渲染/清理能力，不引入新状态框架；不要修改未涉及的正文/草稿/审核行为。
- Regression test: 在现有真实渲染fixture先合法打开确认，隐藏，恢复；GET返回同job/revision/状态/能力，仅serverNow改变。断言旧确认DOM与两个旧按钮已消失，旧引用点击0POST；当前合法入口可重新开启，显式点击恰好1POST且携带当前captured revision。此用例必须能在当前未修代码上因可见DOM残留失败；不能只断言closure清理或POST0，也不能靠手动重绘fixture掩盖问题。
- Existing verification: 下列精确命令和实际运行门禁；保留现有取消/重发/迟到409/READ/撤权/忙碌/网络/恢复冲突回归。
- Must not change: Unchanged Contract全部规则；隐藏期间不新增轮询，恢复不能重发旧动作；不靠新的业务字段或固定源字符串断言。
- Prohibited: 后端/worker/deadline/权限调整，扩大清理为内容丢失的重挂载，强制页面reload，删除旧断言，减少完整JS范围，重开已解决V-1–V-4，执行可选全JVM套件或修复其夹具。

## Verification Commands

只在上述 worktree；所有编辑结束后执行并记录真实exit/count/fail/skip。临时原生浏览器harness与原始输出仅放 /tmp；只用隔离服务/数据库和@example.test测试收件人。

1. `node --test src/test/js/mailboxDelayedReply.test.js src/test/js/mailReviewAdmin.test.js src/test/js/expertAccessWorkspace.test.js src/test/js/mailboxReadOnly.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxServerDrafts.test.js`
2. `node --test src/test/js/*.test.js`
3. `node --check src/main/resources/static/mailbox-chat.js`
4. `git diff --check -- src/main/resources/static/mailbox-chat.js src/test/js/mailboxDelayedReply.test.js src/main/resources/static/index.html`
5. 实际当前静态源码 + 真实Spring/MySQL/受控SMTP + 原生Chrome：普通与委派FULL的真实WAITING上合法打开确认，实际切同窗口后台标签，确认document.hidden确为true且会话轮询停止；前台恢复必须立即GET、旧确认不可见，未变且未到期任务能重新打开并明确操作一次。READ仍无操作；保留普通/REVIEW/delegated既有59→61单POST409→GET行为直接证据。真实浏览器工具不得对visibility/clock/业务状态/响应做覆盖；只允许为既有late409场景延迟真实GET。
6. 记录served index/chat源SHA与当前工作树相等、20资源同键/集合/顺序、1440/390旧footer样式无变化。运行证据与失败探针全部保留，清理仅自有进程/容器。

本计划只有前端三文件，不要求无关Kotlin再编译。独立aggregate回审仍须按Epoch2完整master矩阵与全部必跑命令重新验证，含mysqlIt/migrationIt/完整JS/真实运行；此限定执行门禁不能替代master PASS。

## Completion Criteria

- 真实隐藏后旧确认显示立即或在恢复可交互前清除，未变服务端回读不会留下无效按钮；自动动作0，合法重新打开后显式请求1。
- 判别回归证明当前错误分支被修复；所有既有指定回归及完整JS通过，无跳过伪PASS。
- 原生普通与delegated FULL/READ生命周期及三宿主late409保留；草稿/审核正文、附件、身份、busy与迟到回包规则不变。
- 仅三授权产品/测试文件；index仅20既有版本值；不修改CSS或新增DOM约定。
- 记录独立可重放命令、当前源hash、实际返回/请求数量/截图/失败探针/清理，无历史PASS替代。
- 完成本修复不表示人工A1–A12通过，也不授权部署。

## Human Approval

Execution is prohibited until the human explicitly approves this plan.
After approval, run `execute-p` with this file.

必须由用户明确批准当前内容。审查代理、控制器转交、自动继续、旧repair执行批准均不是当前草案授权。

## Review-Fast-P Execution Handoff

明确的人类原始调用 `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/fix/manual-reply-delay-master/repair.md` 批准当前内容并授权：

1. 只执行三Authorized Files与上述验证；不扩大修复。
2. 所有任务/门禁通过后，在READY_FOR_VERIFICATION前恰好一个本地产品提交；仅stage三个授权文件中实际变化者。主题：`fix(mail): clear delayed confirmation on hidden`。
3. 追加精确 `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-reply-delay-master/docs/plans/review/manual-reply-delay-master/repair-execution.md`，记录当前repair SHA、准确人类批准来源、前后代码SHA、执行器身份、文件/命令/计数/失败探针/清理与干净状态；保留旧epoch，不覆盖。
4. 恰好一个仅含上述 repair-execution.md 的docs-only证据提交，主题：`docs(review-fast-p): record hidden confirmation repair`。
5. 如用户同时明确要求修后回审，回到同一聊天已授权的review-fast-p流程，由修复之后创建的独立新reviewer完成aggregate/master。基准路径和报告路径保持本文件顶部的精确值。

不含amend、重写历史、额外文件、push、merge、部署或自动下一轮修复。本轮仅生成草案，未执行修复。
