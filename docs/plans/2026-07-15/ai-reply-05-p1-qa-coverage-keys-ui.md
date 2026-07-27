# P1-5：QA 覆盖能力标签管理界面

## 需求描述

在 QA 规则列表与编辑弹窗中查看、选择 coverage keys，使运营可以修正“规则正文实际覆盖什么”，不需要改数据库。选项从后端 metadata 加载。

Out of scope：在前端推断 key、自动生成 QA 正文、intent coverage 展示、发送确认。

## 关键不变量

### I-1：后端目录单源
- 页面只渲染 `/api/qa/coverage-keys`；不得在 app.js/index.html 写 key 常量表。

### I-2：空标签兼容
- 存量空 coverage rule 可编辑/保存；UI 显示“未配置 AI 覆盖能力”警告，不强制伪选 `general.answer`。

### I-3：表单 round trip
- edit 回显 rule.coverageKeys；save 提交当前 checked key 数组。
- reset/close/new rule 清空旧选中，不能串表单状态。

### I-4：现有 QA 管理不回归
- category、keywords、variants、占位符 preview、enable/handoff 行为不变。（K-content-variant-input-read-contract）

## 前端样式契约

- `#qaRuleForm` 仍为 `.form-grid` 两列；coverage block 使用 `.span-2` 横跨两列。
- 选项容器复用 `.metadata-grid`：`repeat(auto-fill,minmax(160px,1fr))`、gap 8px；单项复用 `.checkbox-row`，checkbox 14×14。
- 状态展示复用 `.badge.primary/.badge.warn`；不新增颜色、阴影、圆角、固定高度。
- modal 继续 `.modal-shell > .panel.editor-panel.modal-panel`，宽 `min(900px,100%)`、max-height 85vh；form 自带 overflow-y auto。
- 手机端沿用 `.form-grid` 单列 media rule；coverage `.span-2` 自动占整行。
- 本计划不修改 `styles.css`。

## 实现任务

### T1：state 与加载
文件：`src/main/resources/static/app.js`

- state 增加 `qaCoverageKeys: []`。
- `loadQa()` 并行加载 categories/rules/coverage-keys；失败时整个 QA 编辑不可进入半加载状态。

### T2：表单 DOM
文件：`src/main/resources/static/index.html`

- 在 keywords/match 配置之后、正文之前增加 `.span-2` coverage block。
- 包含说明、动态 options 容器 `#qaCoverageKeyOptions` 与空标签提示 `#qaCoverageKeyWarning`。
- QA 表格增加“AI 覆盖能力”列。

### T3：render/fill/save/reset
文件：`app.js`

- `renderQaCoverageKeyOptions(selectedKeys)` 按 metadata group/顺序创建 checkbox；label/description 全 escape。
- `fillQaRuleForm()` 回显；`hideQaRuleEditor()` 清理；新建为空。
- `saveQaRule()` 读取 `[data-qa-coverage-key]:checked`，提交 coverageKeys。
- 列表最多显示前 3 个 label + `另 N 项`；空标签显示 warn badge。
- 调整空表 colspan。

### T4：JS 测试
文件：`src/test/js/qaCoverageKeyEditor.test.js`

- metadata 非硬编码、正确 escape。
- fill/save/reset round trip。
- 空标签 warning。
- variants 读取、现有 payload 字段不丢失。
- 表格列/colspan 对齐。

## 变更文件清单（3）

1. `src/main/resources/static/app.js`
2. `src/main/resources/static/index.html`
3. `src/test/js/qaCoverageKeyEditor.test.js`

## 工作区冲突保护

- 开始前分别查看 app.js/index.html 当前 diff，保留批量发送控制台修改。
- 只在 QA state/load/form/table 函数与 `qaRuleModal` 窄区域 patch。
- 不修改 styles.css；不运行 HTML/JS 全文件格式化。

## 验收标准

- 运营可从 UI 完成 key 新增、删除、清空并刷新回显。
- UI 不展示 raw key 作为唯一说明，至少有中文 label；审计/调试仍可看到 key。
- 定向测试：

```bash
node --test src/test/js/qaCoverageKeyEditor.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js
```

## 人工验收清单

### A-1：编辑 Partner company
- 预期：只勾 enterprise.matching；不勾 enterprise.project_types。

### A-2：空标签规则
- 预期：列表 warning；编辑可正常保存其他字段，不崩溃。

### A-3：响应式
- 900px 与窄屏查看 modal；选项不横向溢出，保存按钮可滚动到达。
