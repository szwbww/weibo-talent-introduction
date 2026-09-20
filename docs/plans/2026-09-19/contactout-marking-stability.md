# ContactOut 标色稳定性修复

## 需求描述

自动分析与翻页后的 `more` 展开继续工作；同一资料卡在内容未变化时不得在黄色“待核实”和红色“可忽略候选”之间反复切换或短暂清空。不得改变企业评分、岗位分级、邮箱采集、显示邮箱、翻页或联网评分的行为。

范围外：不调整 Henkel 或任何企业分数；不改变 DeepSeek/Tavily 联网评分；不让插件点击邮箱、电话、AI、导出或分页控件。

## 关键不变量

### Invariant I-1: 单一自动重标入口
- Rule: 已保存的手工规则模式下，只有 `autopilot.js` 监听页面内容并触发自动重标；`collector.js` 的显式标记函数不得再注册第二个 DOM MutationObserver。
- Applies to: `collector.js:markContactOutIgnored`、`autopilot.js:run/start`。
- Violation consequence: 两套观察器以不同页面瞬间状态重算，导致黄/红来回闪烁。
- 来源: original

### Invariant I-2: 语义无变化时原位保留标记
- Rule: 同一资料卡的结论状态、范围和原因文本均未变化时，重标不得删除该卡的颜色、轮廓或提示条；仅发生实际结论变化的卡可替换自身标记。
- Applies to: `collector.js:markContactOutIgnored` 的渲染路径。
- Violation consequence: 即使评分结论相同，页面也会出现清空后重绘的视觉闪烁。
- 来源: original

### Invariant I-3: 内容指纹不包含插件标记
- Rule: 自动分析的页面签名必须同时包含每张当前资料卡的可见履历文本与 LinkedIn 标识，但必须排除 `data-contactout-review-badge` 自身；插件写入红黄提示不能作为下一轮自动分析的触发条件。
- Applies to: `collector.js` 导出的资料卡签名、`autopilot.js:resultSignature`。
- Violation consequence: 插件自身 DOM 写入会造成无限或重复重标。
- 来源: original

### Invariant I-4: 显式清空仍完整清理
- Rule: 用户执行“取消标记”或切换到已启用联网评分时，必须移除全部提示条、红黄属性和注入样式；自动重标停止时不得保留旧的手工标记。
- Applies to: `collector.js:clearContactOutIgnoreMarks`、`autopilot.js` 联网分支。
- Violation consequence: 页面会显示过期的手工结论，或保留干扰 ContactOut 的样式。
- 来源: original

## 样式契约

### S-1: 红黄资料卡标记
- 复用：`tools/contactout-visible-export/collector.js:258-260` 的既有属性选择器与色值；不新增 CSS class。
- 新增：无。现有 CSS 字符串必须逐字保留：
```css
[data-contactout-ignore="yes"]{background-color:#fff1f2!important;outline:2px solid #dc2626!important;outline-offset:-2px!important}
[data-contactout-review-badge]{display:block!important;box-sizing:border-box!important;margin:8px!important;padding:8px 10px!important;border-radius:6px!important;background:#fee2e2!important;color:#991b1b!important;font:600 13px/1.5 system-ui,sans-serif!important;white-space:normal!important}
[data-contactout-review="review"]{background-color:#fffbeb!important;outline:2px solid #d97706!important;outline-offset:-2px!important}[data-contactout-review="review"] [data-contactout-review-badge]{background:#fef3c7!important;color:#92400e!important}
```
- DOM 结构：`card` 的首个子节点为 `<div data-contactout-review-badge>...</div>`；卡本体只可有 `data-contactout-ignore="yes"` 或 `data-contactout-review="review"` 之一。
- 禁止项：inline style；新增 CSS class；变更上述色值、间距、字体、圆角或轮廓。

## 现状审计

### ContactOut 页面标记状态（DOM 属性与注入 style）
- Schema/mapping: 无持久化库。写入为资料卡 `data-contactout-ignore` / `data-contactout-review` 属性、提示 `div[data-contactout-review-badge]`、单例 `#contactout-review-style`。
- Write paths:
  1. `tools/contactout-visible-export/collector.js:238-301` — 清理、创建样式、计算资料卡结论、写入标记；当前还注册 180ms MutationObserver。
  2. `tools/contactout-visible-export/autopilot.js:24-73` — 读取已保存规则，展开 `more` 后调用标记；自身也注册页面 MutationObserver。
  3. `tools/contactout-visible-export/popup.js:220-285` — 用户点击标记、保存岗位规则、保存企业评分时，注入 `collector.js` 并直接调用标记。
  4. `tools/contactout-visible-export/popup.js:328` — 关闭联网模式时手工调用标记；开启联网模式走联网标记流程。
- Read paths:
  1. `collector.js:visibleJobs/hasUnparsedEmployment` 读取可见履历和 `more` 状态，供 `core.js:classifyProfileJobs` 判定。
  2. `collector.js:clearReviewMarks` 读取现存插件属性/提示以移除。
  3. `autopilot.js:resultSignature` 当前只读取可见 LinkedIn 链接，决定是否自动重跑。
  4. `collector.js:collectContactOutVisible` 的可见文本读取明确排除提示条，采集结果不包含标注。
- Interaction points: `autopilot.js` 在展开详情后调用 `collector.js`；`collector.js` 的第二个观察器会在 ContactOut 动态替换 DOM 时独立刷新。两者缺乏共同的内容签名与渲染所有权，是闪烁来源。Popup 仍保留一次性显式写入，但不能恢复第二个观察器。

### 前端样式盘点
- 可复用 class：无；标记依赖 S-1 的 data 属性选择器，定义位于 `collector.js:258-260`。
- 设计基准 token：红底 `#fff1f2`、红边 `#dc2626`、红提示底/字 `#fee2e2/#991b1b`；黄底 `#fffbeb`、黄边 `#d97706`、黄提示底/字 `#fef3c7/#92400e`；提示 margin `8px`、padding `8px 10px`、radius `6px`、字体 `600 13px/1.5 system-ui,sans-serif`。
- DOM 结构约定：提示条通过 `card.prepend(badge)` 成为资料卡第一子节点；采集选择器将 `[data-contactout-review-badge]` 视为忽略区域。
- 改动前基线：`collector.js:263-293` 每次 `refresh` 均 `clearReviewMarks()` 后全量插入属性/提示；`collector.js:295-298` 注册第二个 180ms observer；`autopilot.js:51-56` 注册另一 observer 且签名只由链接组成。

## 实现方案

### 阶段 1：稳定资料卡渲染
- 修改 `tools/contactout-visible-export/collector.js`，遵守 I-1、I-2、I-4、S-1：
  - 删除标记函数内部的持续 MutationObserver；显式调用只做一次当前内容计算与渲染。
  - 保持单例样式存在，不在自动重标时删除/重建它。
  - 为每张资料卡先计算期望状态与完整提示文本；若现有属性及提示文本完全相同，原样保留；仅更新状态改变、原因改变或已不在结果集的资料卡。
  - 保持 `clearContactOutIgnoreMarks` 的显式完整清理，并可安全处理无 observer 的新状态对象。
  - 导出基于 `profileCardsForReview + visibleText` 的内容签名；该可见文本已排除提示条。

### 阶段 2：让自动流程只对真实履历变化重跑
- 修改 `tools/contactout-visible-export/autopilot.js`，遵守 I-1、I-3、I-4：
  - 使用 collector 导出的资料卡内容签名替换仅链接签名；保留无导出函数时的链接回退。
  - 保留现有防抖、运行互斥、联网模式清理及绝不点击非 `more` 控件的限制。
  - 资料卡履历文本变化、真实翻页/列表替换时才触发自动展开和原位重标；插件自身提示条写入不得触发另一轮。

### 阶段 3：回归测试
- 修改 `tools/contactout-visible-export/tests/browser.test.cjs`，遵守 I-1 至 I-4、S-1：
  - 断言保存配置引发的同结论重标不会删除或替换既有提示节点。
  - 断言已展开/履历文本变化会由 autopilot 重新判定，且颜色从黄变红时只更新该卡。
  - 断言在线模式与显式清空仍清除所有手工标记。
  - 继续断言不会点击邮箱、电话、AI 或分页。

## 变更文件清单

| 文件 | 变更 |
|---|---|
| `tools/contactout-visible-export/collector.js` | 移除重复 observer；原位协调标记；导出无提示条内容签名。 |
| `tools/contactout-visible-export/autopilot.js` | 采用资料卡内容签名作为唯一自动重标触发依据。 |
| `tools/contactout-visible-export/tests/browser.test.cjs` | 覆盖无闪烁原位重标、真实履历变化、清理和非点击边界。 |

## 验收标准

- I-1: `rg` 证明 `collector.js` 不再创建标记刷新 MutationObserver；浏览器测试证明 autopilot 是保存配置后的唯一自动刷新入口。
- I-2: 浏览器测试保存同一企业配置后，既有 badge DOM 节点身份不变，且红色属性不曾被移除。
- I-3: 浏览器测试在插件已经写入 badge 后等待超过防抖窗口，自动标记调用次数不增长；改变履历文本后才增长一次并得到新结论。
- I-4: 浏览器测试启用联网评分或调用清空后，`[data-contactout-review-badge]`、`[data-contactout-ignore]`、`[data-contactout-review]`、`#contactout-review-style` 数量均为 0。
- S-1: `git diff` 和测试计算样式证明红黄色值、提示条结构不变；无新增 class 或 inline style。
- 集成: 运行既有全部 Node 测试；以已保存 Henkel 30 分配置翻页，当前 `R&D Scientist` 卡稳定红色，`more` 展开后仍不出现黄/红往返。

## 人工验收清单

### A-1: Henkel 当前页稳定标红
- 前置条件: 已导入 Henkel（30 分、L4）企业评分，关闭“自动企业评分”，ContactOut 搜索公司为 Henkel、岗位含 `Engineer OR Scientist OR Chemist OR Research`。
- 操作步骤:
  1. 重新加载扩展并刷新 ContactOut 搜索页。
  2. 等待当前 25 张卡的 `more` 自动展开完成。
  3. 观察任一当前职务为 `R&D Scientist at Henkel` 的资料卡 10 秒。
- 预期结果: 卡始终显示红底 `#fff1f2` 与红色轮廓；提示含“Henkel 30分”及“岗位 L1 低于 L4”；期间不出现黄色提示或无颜色空档。
- 覆盖: I-1、I-2、I-3、S-1。

### A-2: 翻页后的自动展开与稳定标记
- 前置条件: 同 A-1。
- 操作步骤:
  1. 点击 ContactOut 页码“下一页”。
  2. 不点击资料卡任何 `more`、邮箱、电话或 AI 按钮，等待 10 秒。
- 预期结果: 新页可展开的 `more / Show more` 被自动展开；低于门槛的 Henkel 当前职务稳定红色；高于门槛的卡无红黄标记；邮箱/电话显示状态和页码未被插件改动。
- 覆盖: I-1、I-3、需求描述第 1 条。

### A-3: 显式取消标记
- 前置条件: 当前页至少有一张红色或黄色资料卡。
- 操作步骤:
  1. 打开插件。
  2. 点击“取消标记”。
- 预期结果: 当前页不再有红黄背景、轮廓或提示条；资料卡本身的 ContactOut 文本不变。
- 覆盖: I-4、需求描述“不得改变邮箱显示/翻页”。

### A-4: 联网模式不遗留手工颜色
- 前置条件: A-1 已产生手工红色标记。
- 操作步骤:
  1. 打开插件并开启“自动企业评分”。
  2. 等待 3 秒。
- 预期结果: 旧手工红黄提示全部消失；不会自动点击邮箱、电话、AI、导出或分页。
- 覆盖: I-4、需求描述“不得改变联网评分与非 more 控件”。
