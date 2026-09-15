# 手动批量快照与专家筛选布局修复

## 需求描述

手动批量发送在选择 `SBIR导入` 标签与研发类型后，收件人预估和实际执行必须带上同一份 `expertTypes`，不再因空类型集合命中 0 人。专家列表筛选区在 1181–1500px 逻辑宽度下必须以 3×3 的完整网格展示 9 个基础筛选，避免当前 5+4 产生的右侧空洞。

不得改变后端“INTRODUCTION 空研发类型 fail-closed”的安全规则、现有标签/地区/状态筛选语义、1500px 以上的 9 列布局以及 1180px 以下既有断点。范围外：不修改 ES、数据库、后端 API、批量发送配置或 HTML 结构。

## 关键不变量

### Invariant I-1: 手动快照字段完整
- Rule: `buildManualExecutionSnapshot()` 必须逐字传递 `readManualFormValues().expertTypes`；预估和确认执行均复用该快照。
- Applies to: `app.js:buildManualExecutionSnapshot` → `/api/mail/batch-send/recipients/preview` 与 `/api/mail/batch-send/manual-executions`。
- Violation consequence: 后端收到空集合并追加 `MATCH_NONE_FILTER`，手动批量发送错误显示 0 人。
- 来源: K-batch-snapshot-two-write-entrances。

### Invariant I-2: 空研发类型仍拒绝发送
- Rule: 本修复只修复字段遗漏；没有选择研发类型的 INTRODUCTION 快照仍必须由后端 fail-closed，不得改成“不限”。
- Applies to: `RecipientScope.matchesExpertType`、`ManualInitialOutreachService.buildEsFiltersForLevel`。
- Violation consequence: 未指定范围的介绍邮件可能扩大到非预期专家。
- 来源: 原有实现。

### Invariant I-3: 中等桌面筛选网格完整
- Rule: 1181–1500px 下 9 个 `.expert-filter-row-primary` 控件以 3 列展示；每个 label 纵向排列且 select 占满网格列。≤1180px 的既有 3 列规则和 >1500px 的 9 列规则不变。
- Applies to: `styles.css` 的专家筛选断点。
- Violation consequence: 5+4 布局留下空白列，控件宽度和视觉节奏失衡。
- 来源: 用户截图与现有 CSS。

## 样式契约

### S-1: 中等桌面专家基础筛选
- 复用：`.expert-filter-row-primary`（`styles.css:579-583`）、`.toolbar-label`（`styles.css:471-479`）、现有 1180px 断点（`styles.css:652-655`）。`expert-filter-row-primary` 在 `index.html:461` 仅使用一次；就地修改其 1500px 断点，不新增 HTML/class。
- 设计基准：正文 `13px/1.5`，筛选标签 `11px`、`var(--text-muted)`；主色 `#1e40af`；间距沿用 `gap: 8px`；不新增颜色、圆角、阴影或 inline style。
- 新增：将现有 `@media (max-width: 1500px)` 规则块逐字替换为：

```css
@media (min-width: 1181px) and (max-width: 1500px) {
    .expert-filter-row-primary {
        grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .expert-filter-row-primary > .toolbar-label {
        min-width: 0;
        flex-direction: column;
        align-items: stretch;
        gap: 4px;
    }

    .expert-filter-row-primary > .toolbar-label select {
        width: 100%;
        min-width: 0;
    }
}
```

- 禁止项：修改 `index.html`、新增 class、inline style、改动 ≤1180px 或 >1500px 的布局规则。

## 现状审计

### 手动批量发送快照（无持久化存储）
- Schema/mapping: `BatchExecutionSnapshot.expertTypes: List<String>`；快照直接由浏览器 POST，不写 DB/ES（K-batch-snapshot-two-write-entrances）。
- Write paths: `app.js:buildConfigEditorRecipientSnapshot` 已写入 `expertTypes`；`app.js:buildManualExecutionSnapshot` 读取手动表单但漏写该字段。
- Read paths: `BatchSendConfigController.previewRecipients` 和 `BatchSendControlService` 消费同一快照；`ManualInitialOutreachService.buildEsFiltersForLevel` 对 INTRODUCTION 的空集合追加 `MATCH_NONE_FILTER`。
- Interaction points: 手动表单 → 预估 API/实际执行 API → 后端 ES 收件人过滤；二者必须是同一个完整快照（I-1）。

### 前端样式盘点
- 可复用 class: `.expert-filter-row`（`styles.css:571-577`）、`.expert-filter-row-primary`（`:579-583`）、`.toolbar-label`（`:471-479`）。
- DOM 基线: `index.html:461-532` 包含 9 个 `label.toolbar-label > select`；不改变结构。
- 改动前基线: `styles.css:646-650` 在 `max-width:1500px` 将 9 项设为 `repeat(5, minmax(0, 1fr))`，必然排成 5+4。
- Interaction points: CSS 断点只影响专家列表筛选区；测试必须锁定断点范围、3 列布局与控件宽度（I-3/S-1）。

## 实现方案

1. `app.js`（I-1、I-2）
   - 在 `buildManualExecutionSnapshot()` 中加入 `expertTypes: values.expertTypes`，位置与 `operatorStatuses` 相邻。
   - 不改表单读取、后端参数、默认值或类型校验。

2. `batchSendTaskConsoleInteraction.test.js`（I-1、I-2）
   - 将“完整手动快照”样例加入 `expertTypes: ["PRODUCTION_RND"]`；断言预览/执行共用快照包含该精确数组。

3. `styles.css`（I-3、S-1）
   - 按 S-1 逐字替换 1500px 断点块。

4. `expertFilterLayout.test.js`（I-3、S-1）
   - 替换旧的五列断言：只在 `1181–1500px` 断点断言 3 列、纵向 label、`select` 100% 宽度；保留现有 1180px 三列断点断言。

## 变更文件清单

| 文件 | 目的 |
|---|---|
| `src/main/resources/static/app.js` | 手动快照传递研发类型 |
| `src/main/resources/static/styles.css` | 中等桌面筛选网格修复 |
| `src/test/js/batchSendTaskConsoleInteraction.test.js` | 快照字段回归测试 |
| `src/test/js/expertFilterLayout.test.js` | CSS 布局契约测试 |

## 验收标准

- I-1: `node --test src/test/js/batchSendTaskConsoleInteraction.test.js` 中完整快照断言包含 `expertTypes: ["PRODUCTION_RND"]`。
- I-2: 本次 diff 不修改 Kotlin 后端；现有 `batchExpertTypeFilter.test.js` 继续验证 INTRODUCTION 的空类型校验。
- I-3/S-1: `node --test src/test/js/expertFilterLayout.test.js` 断言中等桌面仅为 3 列，CSS 块与 S-1 完全一致；≤1180px 三列规则仍存在。
- 集成: `node --check src/main/resources/static/app.js` 与两个目标测试的联合命令均退出 0。

## 人工验收清单

### A-1: SBIR 手动收件人预估
- 前置条件: CANDIDATE 层存在 `SBIR导入` 标签且研发类型为“生产研发”的专家。
- 操作步骤: 1. 打开“专家列表”→“批量发送”；2. 选择介绍邮件模板；3. 标签选择 `SBIR导入`；4. 研发类型选择“生产研发”。
- 预期结果: 收件人预估显示大于 0 的命中数；确认执行弹窗与预估使用同一筛选条件。
- 覆盖: I-1。

### A-2: 未选研发类型仍安全阻断
- 前置条件: 打开手动批量发送面板。
- 操作步骤: 1. 选择介绍邮件模板；2. 清空全部研发类型；3. 查看预估并尝试执行。
- 预期结果: 预估为 0 或前端提示必须选择研发类型；不会启动扩大范围的介绍邮件任务。
- 覆盖: I-2。

### A-3: 1280px 逻辑宽度筛选布局
- 前置条件: 浏览器逻辑视口宽度调为 1280px，打开“专家列表”并展开“筛选”。
- 操作步骤: 目测 9 个基础下拉筛选。
- 预期结果: 9 个控件为 3 行×3 列；每列标签在上、下拉框铺满列宽；不存在第二行右侧空格。
- 覆盖: I-3、S-1。

### A-4: 宽屏和窄屏回归
- 前置条件: 同一页面。
- 操作步骤: 1. 调至 1600px；2. 调至 1180px；3. 检查标签、地区、学科筛选仍可选择。
- 预期结果: 1600px 保持原 9 列布局；1180px 保持 3 列；筛选值均能刷新专家列表。
- 覆盖: I-3、需求描述的“不改变”项。
