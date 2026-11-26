# 前端交互与样式综合优化设计方案

**日期**：2026-11-25  
**范围**：纯前端闭环（`src/main/resources/static/` + `src/test/js/`）+ 本地真实入口预览环境（`docs/mockups/ux-optimization-preview/`）

---

## 1. 目标与边界

在不修改 Kotlin 后端、ES 索引与数据库表结构，且 100% 兼容既有 2188 条前端契约单测的前提下，集中解决当前控制台的 **P0 交互隐患、P1 高频操作流痛点、以及 4 类视觉层级/样式一致性问题**，并提供直接挂载真实 `src/main/resources/static/` 生产代码的本地交互预览环境。

---

## 2. 模块设计

### 模块 1：全局浮层栈、非阻塞 Toast 与弹窗/深色样式统一
1. **右上角浮层 Toast 栈（`#consoleToastStack`）**：
   - 在 `body` 挂载固定容器 `.console-toast-stack`（`z-index: var(--z-toast)` 即 `9999`），`showStatus(message, type)` 在更新原 `#statusBar` 文本（兼容既有单测）的同时，在右上角渲染不遮挡输入焦点、支持手动关闭、悬停暂停倒计时、同帧去重、错误态延长停留（6.5s）的卡片；原流内 `#statusBar` 在 `body.console-ui` 下折叠为零布局占位，彻底消除弹窗遮挡与页面内容上下跳动。
2. **统一 `Escape` 浮层栈与表单未保存（Dirty State）拦截**：
   - 收敛全局 `Escape` 关闭优先级：变量插入菜单 / 下拉菜单 → `#previewDrawer` 预览抽屉 → 栈顶编辑弹窗（`#accountModal`、`#qaRuleModal`、`#replySnippetModal`、`#composeTemplateModal`、`#suppressionModal`），按一次 `Esc` 仅关闭当前最顶层浮层，修复预览抽屉与编辑弹窗同关丢稿问题；
   - 为四大配置编辑弹窗记录初始表单快照，用户有未保存修改时点击遮罩或按 `Esc` 弹出 `openActionDialog("confirm")` 二次确认。
3. **非阻塞反馈与链接对话框收敛**：
   - 浏览器 `body.console-ui` 运行态将阻塞式通知收敛至 Toast 栈（同时保留单元测试沙箱自定义 `sandbox.alert` 注入兼容）；
   - `openActionDialog` 新增 `insert-link` 模式（URL + 可选显示文本 + `http(s)://`/`mailto:` 校验），替换富文本编辑器的原生 `prompt()`。
4. **弹窗 `z-index` 修复与深色次级按钮降噪**：
   - 修正 `body.console-ui` 下 `.modal-shell`（提升至 `var(--z-modal)`）与 `.preview-drawer-shell`（提升至 `1040`）的层级倒挂；统一弹窗右上角关闭按钮尺寸与圆角；
   - 在 `console-theme.css` 中将深色模式普通 `.button` 降噪为中性灰白边框与深灰文字（`#344154`），保留 `.button.secondary` 浅蓝与 `.button.primary` 主蓝的清晰主次层级。

### 模块 2：RAG 可信回复工作台修复与检索增强
1. **逐字（`VERBATIM`）段落编辑文本隔离**：
   - 在 `trust-reply-workbench.js` 中为 `.trust-reply-para-tag` 添加 `contenteditable="false"`，新增 `extractParagraphBodyText(paragraphEl)`（优先克隆剔除 `.trust-reply-para-tag` 后读取文本，无 DOM 方法的沙箱单测回退读取 `textContent`），彻底防止 `"逐字"` 角标混入英文外发草稿。
2. **事实联想选择与复制微反馈**：
   - 加载 `/api/rag-kb/facts` 列表缓存，在 `add-fact-input` 输入时提供按 `factCode / title` 过滤的下拉候选面板，支持鼠标点选与 `Enter` 提交；
   - 点击「复制」按钮后按钮文案短暂切换为 `已复制 ✓`（1.5s）并清理定时器。

### 模块 3：收发件箱（`MailboxChat`）效率流与会话卡片打磨
1. **左栏可拖拽调宽**：
   - 在 `aside.mc-experts` 内置 `.mc-resizer` 拖拽条（保持 `.mail-chat` 下 `aside` → `.mobile-mailbox-back` → `section` 三兄弟契约不变），支持 `280px`～`520px` 拖拽并持久化 `localStorage("mailbox-chat-left-width")`，双击恢复默认宽度。
2. **键盘批处理流与快捷跳转**：
   - 非输入焦点下支持 `/` 聚焦会话搜索框、`↑`/`↓`（或 `j`/`k`）切换上下位专家会话、人工回复编辑器内 `Cmd/Ctrl + Enter` 触发发送；
   - 在 `.mc-timeline-head` 增加 `✎ 跳至回复` 快捷动作（自动展开人工回复折叠区并滚动聚焦编辑器）。
3. **会话卡片视觉层级**：
   - 为有待处理来信的 `.mc-person` 标记 `data-has-pending="true"`，在 `console-shell.css` 中强化左侧状态色条与专家姓名视觉权重。

### 模块 4：专家列表体验与全局排版打磨
1. **快捷搜索、激活筛选摘要条与视口优先缓存**：
   - 在 `.contacts-toolbar`（`#contactsFilterGroup` 外部，保持原三行网格契约不变）增加 `#contactQuickSearch`（支持当前页即时过滤 + 跨页调用 `/api/mail/unmatched-inbound/search-contacts` 下拉直达专家详情）与 `#contactClearFiltersBtn` 一键重置按钮，并在 DB 查询模式禁用 ES 专属筛选项时显示明确提示；
   - 在 `contact-details.js` 中保留 `cachedRead` 原签名，将每次切筛选清空缓存改为容量上限 500 的跨筛选缓存，并对列表行启用视口优先队列（可见行优先请求，离屏行后置）；
   - 移除 `app.js` 中 `#loadQaAuditBtn` 重复绑定的 click 监听器。
2. **扁平动效收敛与等宽数字**：
   - 在 `console-shell.css` 下收敛 `.metric-card:hover` / `.list-item:hover` 残余上浮阴影及表单 `label` 强制大写，为数据表格数字统计与分页信息启用 `font-variant-numeric: tabular-nums`。

### 模块 5：真实前端运行预览环境
- 新增 `docs/mockups/ux-optimization-preview/server.py` 与 `preview-fixtures.js`，以回环端口托管真实 `src/main/resources/static/` 全部资源并模拟完整读写 API，支持在真实浏览器端到端体验全部交互与样式修复。
