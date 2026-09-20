# ContactOut 分析前自动展开履历

授权：用户要求分析/标记前自动点击当前页资料卡的 `more`。工作树 `/Users/lukai/IdeaProjects/weibo-talent-introduction`；不提交、不部署。

## 需求描述

用户点击“标记职级”或启用自动企业评分触发分析时，扩展先顺序展开当前已经加载的资料卡的 `more / Show more`，确认 DOM 已更新，再按完整履历标红/标黄。仅当前页，不能展开失败时仍按待核实处理。

不变：不点击 `View email`、`View phone`、AI写信、保存、导出、翻页；不调用 ContactOut 接口；邮箱采集范围与本地名单不变。

范围外：后台常驻展开、自动翻页、自动获取邮箱、点击任意非 more 控件。

## 关键不变量

### Invariant I-1: 操作白名单
- Rule: 分析展开只点击单张资料卡内、可见、未禁用、文本严格匹配 `more`/`...more`/`Show more` 的 button 或 role=button；不会点击 email/phone/AI/campaign/导出，也不翻页。
- Applies to: `collector.js:expandContactOutReviewDetails`，`popup.js` 调用路径。
- Violation consequence: 意外消耗 ContactOut 额度或触发外部动作。
- 来源: original。

### Invariant I-2: 当前页及完成确认
- Rule: 仅从已有 `profileCardsForReview()` 识别当前已渲染资料卡；逐张重新定位以兼容 React 替换，点击后须等待文字变化且 more 控件消失/变为 Show less 才计 expanded。失败保留，不伪称展开成功。
- Applies to: `collector.js`，`popup.js:runOnline/markIgnored`。
- Violation consequence: 把不完整履历误判为完整、或影响未加载/其他页资料。
- 来源: original。

### Invariant I-3: 分析顺序与陈旧保护
- Rule: 企业自动评分获取筛选快照后，先展开、再联网核查和标记；手动标记先展开、再标记。切换企业/页面后的旧 generation 不得应用标记；取消标记不展开。
- Applies to: `popup.js`、`collector.js:markContactOutForCompanySnapshot`。
- Violation consequence: 旧页结果覆盖新页，或取消操作产生新页面点击。
- 来源: original。

## 现状审计

### 资料卡与展开
- Schema/mapping: `collector.js:moreButton` 已严格识别 more；`expandAndCollectContactOutVisible` 只针对有完整邮箱的卡；`profileCardsForReview` 能识别用于职级分析的所有可见资料卡。
- Write paths: `expandAndCollectContactOutVisible` 通过 click 改变 ContactOut 页面 DOM；`markContactOutIgnored` 在卡片加仅本扩展 data 属性/徽章。
- Read paths: `visibleJobs`/`hasUnparsedEmployment` 依据展开后可见文本判断 incomplete；`popup.js` 在标记前注入 `core.js`/`collector.js`。
- Interaction points: 新展开函数必须复用 `moreButton` 和 review cards，不得借用邮件卡限定；自动和手动两条标记路径都需先调用。

## 实现方案

1. I-1、I-2：在 `collector.js` 新增 `expandContactOutReviewDetails`，基于 review card 的 LinkedIn/可见身份重新定位，逐张点击和确认，返回 scanned/expanded/expansionFailed，不采集邮箱。
2. I-2、I-3：在 `popup.js` 添加扩展调用；联网路径在快照后、请求前执行，手动标记路径在 mark 前执行；显示展开数与失败提示，取消标记跳过。
3. I-1-I3：在 `tests/browser.test.cjs` 加含 more、email/phone诱饵、React替换和失败的测试；在 `tests/research.test.cjs` 更新 popup scripting mock 与顺序断言；README 改写旧的“不展开more”说明；版本升 1.5.3 并打包。

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `tools/contactout-visible-export/collector.js` | 分析资料卡安全展开 |
| `tools/contactout-visible-export/popup.js` | 手动/自动分析前展开接线 |
| `tools/contactout-visible-export/README.md` | 分析前展开范围与限制 |
| `tools/contactout-visible-export/manifest.json` | 版本 1.5.3 |
| `tools/contactout-visible-export/tests/browser.test.cjs` | DOM展开白名单与完整性测试 |
| `tools/contactout-visible-export/tests/research.test.cjs` | popup 调用顺序与陈旧保护 |
| `outputs/contactout-visible-export/contactout-visible-export-1.5.3.zip` | 可安装包 |

## 验收标准

- I-1：测试包含 `View email`、`View phone`、AI按钮，计数均为0；only more点击；无翻页。
- I-2：React替换后完整文本可用于 mark；无 more/失败返回准确计数并仍 review。
- I-3：自动和手动路径先展开后 mark；企业切换旧快照不标记新页；取消标记没有展开调用。
- 回归命令：四个 Node 测试、collector/popup `node --check`、`git diff --check`、ZIP文件清单。

## 人工验收清单

### A-1: 分析前展开
- 前置条件: 当前 ContactOut 页有含 `...more` 的资料卡。
- 操作步骤: 1. 打开 1.5.3 插件。2. 点击“标记职级”。
- 预期结果: 资料卡的 `...more` 被展开；状态显示自动展开人数；随后标红/标黄依据完整履历更新。
- 覆盖: I-1、I-2。

### A-2: 自动企业评分前展开
- 前置条件: 已启用直连评分，当前企业筛选有值，页面有 `...more` 卡。
- 操作步骤: 点击“重新联网查询”。
- 预期结果: 先展开当前页 more，再显示企业评分；不打开邮箱/电话。
- 覆盖: I-1、I-3。
