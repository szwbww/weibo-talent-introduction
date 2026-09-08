# Fast-P Child 08 — 执行报告（execution.md）

- 执行结果：READY_FOR_VERIFICATION
- 计划：docs/plans/2026-09-07/08-shared-materials-frontend.md（本子计划的完整批准契约）
- 计划 SHA-256：`4631f164ea37726a6d4a1782742907d6cb21c67a2b2b9a8d88c08164a1a6bda7`（执行前与执行后复算一致，未变化）
- 样式契约：docs/plans/2026-09-07/ui-style-contract.md（S-1..S-5）；baseline：docs/plans/2026-09-07/frontend-baseline.md（只读参考）
- Master：docs/plans/2026-09-07/00-mailbox-materials-master.md（G-4/G-5/G-6、样式契约、性能与错误契约）
- Worktree：/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials @ branch fast/mailbox-materials
- Worktree ID：`...fast-mailbox-materials@fast/mailbox-materials@.../.git/worktrees/weibo-talent-introduction-fast-mailbox-materials`
- Child base SHA：62d86310634e5cb1a9ae522f536cb2c91b0fc2b9（= child 07 code head）；执行起点 HEAD 7da63d39（+07 验证证据 commit）
- 实现提交：`47a72d885498ff473560d26901d2596ffdddb2cd` — `feat(fast-p): implement 08`（仅 5 个授权文件，+3120 行）
- 执行纪元：NEW（本 invocation 从零实现，无复用旧证据）

## 变更文件（精确 5 文件，与计划变更清单一致）

| 文件 | 操作 | 说明 |
|---|---|---|
| src/main/resources/static/expert-materials.js | 新增 | window.ExpertMaterials IIFE：configure/mount({host,contactId,mode})/unmount/unmountHostsIn；contactId→store Map；S-1 面板渲染 |
| src/main/resources/static/expert-materials.css | 新增 | S-1 CSS 契约逐字复制（60 行 / 7097 字节，SHA-256 c5bd312c…863e） |
| src/main/resources/static/app.js | 修改 | +73 行纯增量：宿主适配 helpers + loadContactDetail/showExpertDetail 的 guard/unmount/mount + ES 无联系空态 |
| src/test/js/expertMaterialsShared.test.js | 新增 | 行为测试（真实 DOM 能力的最小树，非空 stub）：I-1..I-4 全覆盖 |
| src/test/js/expertMaterialsStyle.test.js | 新增 | S-1 CSS 字节比对 + 渲染树 DOM 白名单（class/id/inline style/data-state） |

未触碰：index.html、styles.css、任何后端文件、docs（本报告文件本身除外，属 fast-p 证据，不入实现提交）。

## 命令证据（全部在本 worktree 根目录执行，均新鲜运行）

| # | 命令 | 结果 | 证据 |
|---|---|---|---|
| 1 | `node --check src/main/resources/static/expert-materials.js && node --check src/main/resources/static/app.js` | PASS（exit 0） | 两文件语法通过 |
| 2 | `node --test src/test/js/expertMaterialsShared.test.js src/test/js/expertMaterialsStyle.test.js` | PASS（exit 0） | tests 21 / suites 7 / pass 21 / fail 0（约 2.1s） |
| 3 | `node --test src/test/js/*.test.js`（全量 JS 套件） | PASS（exit 0） | tests 692 / suites 123 / pass 692 / fail 0（3.1s） |
| 4 | `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | PASS（exit 0，BUILD SUCCESS，3:44 min） | Java：Tests run 3215 / Failures 0 / Errors 0 / Skipped 9（opt-in IT 按设计跳过，见下）；exec-plugin Node 阶段同跑 692 JS 全绿 |

> 9 个 skipped：仓库既有 `@EnabledIfSystemProperty(migrationIt=true)`/`mysql-it` profile 门禁的数据库/迁移 IT（FlywayMigrationIntegrationTest 等），需 Docker/独立测试库且总计划要求显式开启；非本子计划产物，未改动。

## 不变量核对

### I-1 唯一组件与存储
- window.ExpertMaterials IIFE；`mount({host, contactId, mode})` / `unmount({host})` / `unmountHostsIn(root)`；`configure({api, contextPath, labels, pollMs})`。
- contactId → store Map；同 contactId 的 inline/drawer/selectionOnly 视图共享一个 store（数据/筛选/选择/轮询/提交锁全部 store 级）。
- 行为测试：双 host 一次初始 GET（第二 host 至多一次静默刷新）、行勾选/提交按钮/提交在途禁用跨 host 同步、单次 POST；抽屉关闭后另一 host 轮询继续；重开 drawer 看到服务器任务真实状态（I-3“页面切换不重置服务器任务”）。

### I-2 渲染与请求
- GET /api/expert-contacts/{id}/materials?page&size=10&q&source&sourceId&state 只读；任何渲染/搜索/翻页/轮询/重试路径绝不触发 POST 或文件获取（测试断言 GET URL 不含 download/preview/transfers/reconcile 且 POST 数 0）。
- 300ms 搜索防抖（可配置 debounceMs，默认 300）；store.seq 请求 epoch：q1 迟到响应被丢弃、跨专家 A 的迟到响应绝不写入 B 的 DOM、unmount abort 读请求。
- 行级局部更新：轮询只更新状态列/文件信息单元格（renderRows diff by attachmentId，snapshot 未变不动 DOM），不重建搜索输入与勾选框；输入框只建一次于 mount。
- AbortController 只作用于读请求；已提交 POST 服务端任务永不取消（unmount/关窗后继续）。

### I-3 选择与任务
- 每页 10 行；表头复选框 aria「选择本页」只选本页。
- selection 按 contactId 存于 store，跨页/跨筛选保留（筛选后计数不变、清空筛选仍 20、提交恰好 20 个去重 id）；清空显式。
- POST /materials/transfers 只发送所选 id：去重、1..500；>500 不发 POST，错误区提示「一次最多获取 500 份…请分批获取」，选择保留；从不隐式全选专家/页面外。
- 提交成功才显示 QUEUED（merge 服务器 202 items），失败保留选择并显示原因（em-error）；提交中/重复提交禁按钮（跨 host 同步 disabled +「正在提交…」）。
- QUEUED/DOWNLOADING 行含 progress 元素；DOWNLOading 有 encodedSize 时 determinate（value/max），未知长度不设 value、不编造百分比。
- 2s 轮询（默认 pollMs 2000）仅在存在视图且 summary.active>0 / 行内 QUEUED|DOWNLOADING 时调度；unmount 即停（dropStore 清 timer + abort），下次 mount 立即重新拉取。

### I-4 渐进加载
- app.js 全部改动带 `typeof window !== "undefined" && window.ExpertMaterials` / `typeof <helper> === "function"` 守卫：组件未加载（index.html 注册属 child 11）时 renderExpertDocuments/document-row/AI 按钮等旧路径逐字保持（全量 JS 回归 + 专项「旧路径」测试证明）。
- loadContactDetail：innerHTML 写入前 unmountHostsIn(contactDetail)（释放上一专家视图/监听/轮询），写入后按 contact.id 真实正数挂载 inline。
- showExpertDetail（ES 原始专家、无 contactId）：写 DOM 前同样卸载；不挂载网络组件；组件存在时渲染静态空态「尚未建立联系，暂无资料。」（S-1 class 面板 + 0 份计数），零 materials/documents 请求、零 mount（专项 vm 测试证明）。
- 两个专家详情入口（专家列表 select-expert → loadContactDetail / openContactInList 等复用路径）都只经 loadContactDetail 以真实 contactId 挂载；`mountExpertMaterialsInline` 对 null/0/NaN id 返回 null 不发请求。
- 原 AI 入口：inline header 右侧渲染既有「AI 智能分析」按钮（data-action=open-ai-analysis + data-contact-id，类名 button small primary），点击冒泡到 #contactDetail 既有委托 → 原 openAiAnalysisModal；组件不拦截该 action；child 09 对 openAiAnalysisModal 的桥接改造自动继承该入口。原所有其他详情事件/模板不改。

### S-1..S-5 样式契约（G-6）
- 字节证据：expert-materials.css = 计划 08 S-1 代码块 = ui-style-contract S-1 代码块（两两 strictEqual，样式测试固化）。文件 SHA-256 `c5bd312cfe9a61efd786780bd3a5b3fa2028e0fcd40135e20cb027601b4d863e`（与两文档代码块提取一致）。
- DOM 白名单：真实挂载 inline+drawer+selectionOnly 并覆盖六种存储状态后整树遍历——每个渲染 class ∈ (expert-materials.css ∪ styles.css)；元素 id 数为 0；inline style 属性数为 0；data-state ⊆ 六状态枚举；固定文案（已存服务器/仅文件信息/排队中/获取中/获取失败/来源不可用）与错误 message 展示逐项断言。
- 复用既有全局类：.button / .button.primary / .button.small（styles.css:802/838/2470）；既有 metadata-card / document-row / 全局规则零修改（app.js diff 纯增量，styles.css/index.html 未改）。
- 结构白名单（S-1 DOM）：expert-materials > header(h3+span)/em-policy/em-stats/em-filters(搜索+来源+状态)/em-table-head/em-rows>em-row(checkbox+em-file+em-state)/em-empty/em-error/em-pager/footer(em-selection+清空+获取所选)；drawer 模式 = 原生 `<dialog class="em-drawer">` 包裹同一 section，关闭/Esc 只释放 UI；selectionOnly 同渲染器仅隐藏传输 footer 与 header 动作。
- 无新 inline style、无未声明 class、无全局规则改动；未引入预览 mock/定时器覆盖/全局 override（pollMs/debounceMs 为 configure 注入的默认 2000/300 常量，非 mock）。

### 性能与错误契约（master）
- 1000 附件目录：分页 GET 每次仅一页，DOM 一次只渲染 10 行（测试翻至第 100/100 页仍 10 行）；长文件名 title/aria 保留全文、省略由 CSS 完成；pager 显示完整总数。
- 列表 total 受筛选；summary 恒为全专家统计（header 计数 = summary.total，stats 四桶不受筛选影响）；选择计数独立于筛选。
- 统计口径说明（实现解读，已在代码注释标注）：S-1 样例的 em-stats 只有 4 个 span，summary 的第 5 桶 sourceUnavailable 并入「失败」span 计数（`failed + sourceUnavailable`），行内 data-state 与原因文案区分「获取失败」与「来源不可用」，保证四数之和 = 总数（无隐藏缺口）。
- 来源来信 select：选项 = 「全部来信」+ 已见行 source 去重累积（type:id），选择值在轮询/翻页重建时保留；存储状态 select = 全部状态 + 六状态（状态文案唯一）。
- SOURCE_UNAVAILABLE 行不提供盲拉按钮（S-1 文案），行内展示具体原因（error.message/code）。
- 行内单件「获取到服务器 / 重试」走同一 POST transfers 语义（可请求状态才渲染按钮，服务端 canFetch 为最终依据）。

## 已知解读与边界（无契约偏离）
1. em-stats 的 4-span 标签对 5 summary 桶的映射见上（failedTotal = failed + sourceUnavailable）——样式/DOM 未超出 S-1 样例。
2. 来源筛选选项来自“已浏览页面所见来信”的累积（服务端 source+sourceId 参数化过滤），搜索可帮助定位任意来信的文件后形成该来源选项；A-1/A-2 人工验收路径不依赖跨页盲选来源。
3. 关闭 drawer = 释放该视图（store 在最后视图卸载时整体释放：清 timer/abort 读/丢选择缓存）；服务端任务继续，重开 drawer 立即重取并显示真实任务状态（已测）。同 contact 的其它 host（如 inline）挂载期间 store 存活、轮询继续。
4. UI 在“活动任务可见”时才轮询；单视图被卸载即暂停（隐含“隐藏即暂停”语义）。
5. browser 原生 dialog 的 Esc/焦点恢复语义由浏览器承担（组件只监听 close 事件释放视图），浏览器实拍属人工验收清单 A-1/A-2。

## 验证自检
- 命令全部本 invocation 新鲜执行，exit 0。
- 变更文件严格 = 计划 5 文件清单；未改 index.html/styles.css/后端。
- 提交 47a72d8 为 fast/mailbox-materials 的 HEAD，可达 child base 62d8631（经由 7da63d3），subject/文件集符合规则；fast-p 证据（ledger.md、children/**）未入提交。
- 计划身份复算一致（4631f164…）；worktree 身份复算一致。

## 残余关注
- 浏览器实拍（四视口、焦点/键盘/Esc、长名/1000 附件/错误态溢出、双 host 视觉同步）属计划人工验收 A-1/A-2，未在本阶段声称通过。
- index.html 资源注册与启用默认值属 child 11，本阶段组件已部署但未激活；未注册时旧页面行为经回归证明不变。
