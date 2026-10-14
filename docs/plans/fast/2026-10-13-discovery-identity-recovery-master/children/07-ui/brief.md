# Child Brief — 07-ui（一人多邮箱展示）

- Child ID: `07-ui`；执行 epoch 1。
- Authoritative child plan: `docs/plans/2026-10-13/discovery-person-07-ui.md`（此 worktree 内已提交；含 **A12** 修订；**先完整阅读它，它是唯一权威合同**）。
- Master plan（全局合同）: `docs/plans/2026-10-13/discovery-identity-recovery-master.md`（I-5/I-6）。
- child_base_sha: `db8ed30807578f93e8082b636c52f33a9a4d1ac8`（06-query 的 Code head，已 LIGHT_PASS_WITH_NOTES 验证）。
- 起始工作树 HEAD（开始前必须核对且 `git status` 干净）: 以 dispatch 消息给出的 `docs(fast-p): prepare child 07-ui` 提交 SHA 为准（含前序 fast-p 记账提交 docs/plans/fast/**，属 controller 记账，不得回退、不得纳入你的提交）。
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch: `fast/2026-10-13-discovery-identity-recovery-master`
- 要求使用 `execute-p` 技能执行本子计划。

## 前置已完成（可直接引用）
- 06-query：只读 `GET /api/experts/person?docId=...&level=...`（personId/revision/代表 docId/邮箱成员/各成员层级与 contactId/状态）；专家列表与筛选计数返回人物口径 `totalHits`；`groupDiscoveredPersons=true` 分组分支（默认关闭、旧客户端契约不变）；桶按人去重。

## Authorized files（精确 3 个；如需第 4 个文件先停止并返回 PLAN_CONFLICT）

| # | 文件 | 动作 |
|---|---|---|
| 1 | `src/main/resources/static/app.js` | 修改 |
| 2 | `src/test/js/discoveryPersonEmails.test.js` | 新增 |
| 3 | `src/main/resources/static/index.html` | A12：16 个资源缓存键同值 bump 为 `20261014-person-emails`（仅键值，无结构改动） |

## Required commands（记录退出码与精确计数）
- C1：`node --check src/main/resources/static/app.js`
- C2：`node --test src/test/js/discoveryPersonEmails.test.js`
- C3：`node --test src/test/js/*.test.js`（全量；必须全绿；总量 ≥1657 + 本子计划新增用例）
- （等价可选：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -o -Dtest=DiscoveryPersonQueryServiceTest test` 也会经 exec-maven-plugin 重跑 node 套件；至少必须执行以上三条。）

## 必须兑现的合同要点（以子计划正文为准；这里是索引）
- I-1：列表使用 06 的人数与分组，不在当前页自行去重；保留 person 与全部成员元数据；原发送/标签/资料/材料操作仍用明确选中的 docId/contactId；不把 personId 冒充 ORCID；默认代表邮箱与可发送邮箱分别标明，未取得邮箱准入时不显示"可发送"。
- I-2：`showExpertDetail` 与 `loadContactDetail` 共用邮箱片段；获取人物详情捕获 generation、原专家/contact 身份及宿主，旧响应不得覆盖新页面；所有姓名/邮箱/原因经 `escapeHtml`/`textContent`；历史入口保留原 contactId；非 discovered 保持原 UI。
- S-1：复用既有 `styles.css:1386 .list-item-meta`、`:1420 .expert-meta span`、`:1637 .expert-header-info p`；仅替换既有邮箱 span 文本（单邮箱沿用原文本；多邮箱为 `主邮箱地址 · 共 3 个邮箱`，数量取响应）；禁止新增 CSS、inline style、改行高/字号/颜色、把原成员标签并集当新资格。
- S-2：复用既有 `.metadata-grid`/`.metadata-card`/`.metadata-card-header`/`.metadata-card-value`/`.metadata-card.span-all`/`.link-btn` 规则；在两个详情的联系资料面板 `.metadata-grid` 中增加同一片段（外层 `data-role="discovery-person-emails"`，成员行含 `data-member-doc-id` 与 `data-action="open-person-member-history"` + `data-contact-id` 按钮；无历史时不渲染 button；无法读取状态显示 `状态未取得`；确有成员资料冲突时另一 value 行显示差异提示）；不新增 class/CSS、不重复全局 id、不默认自动切换联系人、不重排详情子标签。
- app.js 实现步骤（详见计划实现方案 1-3）：列表保留 person 响应（DB 分支请求 06 的分组模式与服务器邮箱域名筛选；总数按响应；分页不在成员层）；详情在同一文件封装共用只读邮箱卡片并在两宿主调用人物接口（点击历史用原 contactId 导航并标明当前邮箱；重复渲染不重复绑定监听器）；定向 UI 测试用实际函数与两宿主 DOM 骨架，覆盖新响应映射、点击历史、快速切页、长邮箱转义，不以未声明的全局 stub 掩盖引用缺失。
- index.html：仅把 16 个 `?v=` 同值改为 `20261014-person-emails`；版本化资源数量必须保持 16，不得新增/删除/改动结构。
- 边界：不改 `styles.css` 与其他 JS/HTML 结构；无生产动作；`docs/plans/fast/**` 不得进入你的提交。

## 下游接口
- 本子计划完成 06↔07 的 API/DOM 联合契约（`GET /api/experts/person` 响应结构 ↔ 两宿主 DOM）；后续子计划不依赖 UI。

## 报告与提交
- 完整结果写入：`docs/plans/fast/2026-10-13-discovery-identity-recovery-master/children/07-ui/execution.md`（逐命令退出码/计数，改动摘要，未决问题）。
- 本地提交仅含 3 个授权文件：`feat(fast-p): implement 07-ui`。
- 返回仅限：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
- 不审查后续子计划、不修复无关行为、不 push/merge/改写历史；遇到计划未覆盖的行为决策或需要新文件：返回 BLOCKED/PLAN_CONFLICT。
