# Fast-P Child Brief — 02（收发件箱三态标记：状态菜单与完整Tab）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-10-04/mailbox-progress-master.md`，identity `commit:9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4`。
- 本 child 批准计划（完整合同，必须先通读）：`docs/plans/2026-10-04/mailbox-progress-02-frontend.md`，identity `commit:9594d4b3b3c3022b2be2bc04b24d90ee40a58ad4`。「需求描述」「关键不变量」I-1～I-6、「样式契约」S-1～S-4、「实现方案」T-1～T-4、「变更文件清单」「验收标准」逐条生效；本 brief 是执行摘要，冲突时以计划原文为准。
- 执行授权：用户显式 `/fast-p docs/plans/2026-10-04/mailbox-progress-master.md`（2026-10-04）。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-04-mailbox-progress-master`；branch `fast/2026-10-04-mailbox-progress-master`；`child_base_sha` 见派发消息（= child 01 的 Code head）。
- 依赖：01 已交付（`PUT .../progress-status`、`providedOnly`、`progressStatus` 字段、挂起响应 progressStatus）。实施前先读 01 的实现与执行报告确认接口逐字相符；不符 → `PLAN_CONFLICT`。
- 基线命令结果（实施前先读对照）：`docs/plans/fast/2026-10-04-mailbox-progress-master/children/02/baseline.md`。

## 全局约束

1. 只允许修改「Authorized Files」表内 6 个文件；不得新建白名单外文件（含 fixture、工具脚本、静态资源、预览文件）。其余前端/后端/文档全部只读。
2. 不得修改 `docs/plans/**`（计划与证据由控制方提交）；不得修改 `docs/plans/fast/**`；执行报告写到本 child 目录 `children/02/execution.md`（fast-p 报告/日志不进入产品提交）。
3. 不得 push、merge、rebase、squash、amend、reset；不得改写已有提交。产品代码只提交一次：`feat(fast-p): implement 02`。
4. 计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不要自行扩范围或改计划。若并行变更引入固定缓存键测试使 S-4 的命中清单变化，先在报告中说明并按计划"修订清单"要求处理。
5. 禁止联网抓取、连线上 MySQL/ES、发信、部署；不得新增依赖；不得改 `pom.xml`。
6. `mailbox-chat.css` 为字节锁定，禁止修改；新增 CSS 只追加到 `styles.css`（S-2 逐字块）。
7. 正式代码不搬用预览 JS/CSS/宿主；预览只作设计参考。
8. 测试环境：JDK11 `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home`；node 可用。JS 用例跑法：单文件 `node --test src/test/js/<x>.test.js`、全量 `node --test src/test/js/*.test.js`、语法检查 `node --check src/main/resources/static/<x>.js`。不得修改既有测试的无关断言；不得以基线已知失败当作通过。
9. 版本键事实（实施前复核）：`index.html` 现有 11 个资源（styles.css/mailbox-chat.css/expert-materials.css/meeting-confirmation.css/world-clock.css 与 app.js/mailbox-chat.js/expert-materials.js/meeting-confirmation.js/trust-reply-workbench.js/world-clock.js）当前键均为 `20261004-mailbox-suspension-followup`，`src/test` 精确反查 0 命中。S-4 要求 11 项统一改为 `20261004-mailbox-progress`，资源名/顺序/数量不变；若实施时旧键命中非 0，按计划 S-4 与 K-frontend-cache-key-triad 处理并记录。

## Authorized Files（6）

| # | 精确路径（相对 worktree 根） | 改动 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-chat.js` | Tab、卡片菜单、真实保存、挂起状态映射 |
| 2 | `src/main/resources/static/styles.css` | 追加 S-2 逐字样式块 |
| 3 | `src/main/resources/static/index.html` | 11 资源同步版本键 |
| 4 | `src/test/js/mailboxChatBehavior.test.js` | 三态/异步/分页/详情隔离 |
| 5 | `src/test/js/mailboxSuspension.test.js` | 挂起与 PROVIDED 交互 |
| 6 | `src/test/js/mailboxChatStyle.test.js` | 样式契约与完整 Tab 约束 |

## 关键不变量（计划 I-1～I-6）

- I-1：合法 progressStatus 为 NONE/FOLLOWING/PROVIDED；标签与菜单严格按表（NONE→跟进中/已提供；FOLLOWING→取消跟进/已提供；PROVIDED→跟进中/取消提供）；旧响应完全缺字段可从 followed 派生兼容；字段存在但非法显示"状态不可用"并禁用，不冒充 NONE。
- I-2：已提供请求 `providedOnly=true`，跟进中仍 `followed=true`；不从当前页内存拼队列；标记后保持 chip/searchText/filters/page，服务端重查；修改卡片 A 不等于选中 A，不得把 A 摘要塞给 B 正文。
- I-3：写入成功前不改显示状态；按 contactId 禁用重复操作并继承 busy；成功更新匹配 ID 摘要后重查；迟到/过期响应不得恢复旧上下文、不得触碰卸载后的 DOM；写成功但列表重查失败提示"状态已保存，列表刷新失败，请重试"，不回滚已提交状态、不报写入失败。
- I-4：挂起结束按响应归类（pending>0→待处理；pending=0+PROVIDED→已提供；pending=0+FOLLOWING→跟进中；NONE 沿原已回复规则）；状态更新后使同专家旧挂起 GET 失效并重查，保留 reason/处理确认/完成锚点，不得用 resetSuspensionState 偷懒。
- I-5：状态操作只请求新状态接口；待匹配卡片无状态菜单；菜单不嵌入 `mc-person-main` 按钮、同一 host 内委托、最多一个展开；再次点/外部点/Escape/换Tab/搜索/分页/重绘/卸载关闭；卸载解绑新增监听。
- I-6：原流程与缓存不变（默认探测、搜索日期、账号范围、待匹配、已回复移出、挂起与草稿/材料/排期）；sessionStore 不新增状态持久缓存；只统一更新缓存键。

## 实现要点（计划 T-1～T-4 摘要）

- T-1：`CHIP_PROVIDED` 放 all 后、followed 前；"关注"label 全改"跟进中"；chipParams/conversationsParams 增 `providedOnly`；renderPerson 渲染 S-2 菜单（未标记/跟进中/已提供 文字按钮 + 两项菜单），所有已关联专家卡片（含无标记/待处理/挂起/已回复）可操作，renderUnmatched 不渲染；新增纯状态解析/菜单定义函数，escapeText；删除详情关注按钮与原 toggleFollow 路径及无调用辅助；实例级菜单打开 ID + contactId busy 集合；键盘行为（打开焦点首项、ArrowUp/Down/Home/End、Enter/Space、Escape 回触发按钮、Tab 不落 hidden）。
- T-2：`mc-set-progress` 捕获 contactId/显式枚举/实例身份；冻结默认探测；不提前改状态；PUT 新端点 body 仅 status；成功更新匹配 item/selectedSummary（followed 从 progressStatus 派生）再 `refreshListWithFallback`；`refreshListWithFallback` 首查与回退 fetch 后先检查 disposed/data==null 并短路；同专家挂起上下文递增 `suspension.seq` 后重查；写失败与刷新失败分开提示；finally 只对存活实例清 busy/重绘。
- T-3：所有 suspension 初始化/重置/view/apply 路径传 progressStatus（旧 followed 仅用于字段缺失兼容）；`afterSuspensionRemoved` 按 I-4 优先级选 Tab；提示文案按真实状态（"保留关注"等旧词替换）；菜单"已提供"不表示材料变化。
- T-4：index 只改 11 项版本键；三个 JS 测试文件更新（1373/2931 Tab 数组及相邻中文数组、2533 旧星标乐观测试替换、stub 增 progress-status 端点与 progressStatus 响应，覆盖 6 转换/失败/延迟/分页末行回退/详情无控件/待匹配无菜单）；Suspension 在 1409 归类矩阵加 PROVIDED 与迟到 GET 保护；Style 增 S-2 完整块字节断言、新 class 白名单、无旧星标/详情控件、S-1 高优先级换行规则仍在。

## 下游接口（消费 01，必须按此对接）

- `PUT /api/mail/mailbox/conversations/{contactId}/progress-status`，body `{ "status": "NONE|FOLLOWING|PROVIDED" }` → `{ "contactId": <Long>, "progressStatus": "...", "followed": <boolean> }`。
- 列表 `GET /api/mail/mailbox/conversations` 支持 `providedOnly=true`；列表项含 `progressStatus`。
- 挂起 GET/PUT/DELETE 响应含 `progressStatus`（`followed = progressStatus==FOLLOWING`）。
- 旧 PUT/DELETE follow 端点仍在（本片前端不再调用它做状态切换）。

## 必需命令（fresh 运行，逐条记录 exit code 与计数；与 baseline.md 对照）

```sh
node --check src/main/resources/static/mailbox-chat.js
node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxSuspension.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxSuspensionStyle.test.js src/test/js/mailboxSuspensionFollowup.test.js src/test/js/mobileCoreNavigation.test.js
node --test src/test/js/*.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

- 最后两条为发布前全量回归；不把 verify.sh 当作全量前端门禁。
- 每条命令的 exit code 与通过/失败计数写入执行报告，与 baseline.md 对照说明增量。
- 浏览器几何/持久化检查（A-1～A-7）属人工验收，不在本轮自动化范围；不得宣称已通过。

## 交付物

- 一个本地实现提交：`feat(fast-p): implement 02`（仅 6 个授权文件）。
- 执行报告：`docs/plans/fast/2026-10-04-mailbox-progress-master/children/02/execution.md`（命令与 exit/计数、基线对照、文件变更、验收标准逐条对照、DP 决策与偏差、未运行项）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
