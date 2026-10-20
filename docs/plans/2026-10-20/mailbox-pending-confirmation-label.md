# 收发件箱“待匹配”改为“待确认”

版本：正式版 V3（2026-10-20）；状态：待评审，未实施。配套：[自动回复不计待处理](./auto-response-exclusion.md)。
本计划可独立验证、部署；仅改显示，不扩大队列数据范围。配套后端只对已匹配专家首封回复加判断，未匹配队列继续原逻辑。

## 需求描述

将截图中的“待匹配”及对应空态、报错、搜索、详情辅助文案统一显示为“待确认”。

必须保持：未关联且 MANUAL_REVIEW 的原队列语义、分页/搜索、专家绑定动作、草稿隔离、样式与布局、API 参数和旧标签数据兼容。

范围外：把所有真人待处理迁入待确认、增设分类页、重构 CSS、改数据库/枚举/后端标签值。

## 关键不变量

### Invariant I-1：名称变，数据不变
- Rule：`CHIP_UNMATCHED`、`unmatchedOnly`、`/api/mail/unmatched-inbound`、DOM id、原标签 `"待匹配"` 及 option value 保持；仅显示字符串为“待确认”。新名称不代表新的数据库状态。
- Applies to：chip、搜索、分页、旧邮箱标签显示。
- Violation consequence：看似改名却使筛选结果为空或改变队列归属。
- 来源：original。

### Invariant I-2：文案完整
- Rule：tab、空态、错误提示、aria-label、详情占位、旧标签显示同步；保留“绑定专家”等实际操作动作名称。
- Applies to：mailbox-chat.js / app.js / index.html。
- Violation consequence：同一队列混用名称。
- 来源：original。

### Invariant I-3：缓存键同值更新
- Rule：index.html 中已有版本化 CSS/JS 引用统一更新为同一新键；实施前按真实旧键检索测试，不修改无关业务文件。
- Applies to：静态发布。
- Violation consequence：浏览器继续显示旧文案或测试缓存键不一致。
- 来源：K-frontend-cache-key-triad。

## 样式契约

### S-1：仅替换文字节点和可访问名称

- 复用 `.mc-filter`、`.mc-empty`、`.mc-error`、`.mc-scroll` 和既有 `.badge.warn`；不新增 class，不改 CSS 或 inline style。
- chip 最终样式来自 `styles.css:12865–12871`：字号 12px、line-height 1.5、padding 7px 5px 10px、圆角 0、透明底、默认文字 #7d8ca2；选中下划线/文字 #3762d8；hover #f4f7ff；active #e9efff；disabled opacity .45；focus outline 2px #9eb9ff。移动端 `styles.css:12912` 最小高度 44px 保留。
- 基础规则参考 `mailbox-chat.css:9–14/58–59/86–88`，最终覆盖以 styles.css 为准；不得只复刻早期基础 pill 样式。
- DOM 目标，结构/属性沿用原模板：

```html
<button class="mc-filter" type="button" data-action="mc-filter" data-chip="unmatched" aria-pressed="false">待确认</button>
<div class="mc-scroll" tabindex="0" aria-label="待确认来信详情"><div class="mc-empty">请选择左侧待确认来信</div></div>
<div class="mc-empty">暂无待确认来信</div>
<option value="待匹配">待确认</option>
```

- 错误模板仍为 `.mc-error[role=alert]` 包含原 `.button[data-action=mc-retry-list]` 重试按钮；仅“待匹配来信加载失败，请重试。”变“待确认来信加载失败，请重试。”。
- 原标签 badge 由 `badge("待确认", MAILBOX_TAG_BADGE_CLASS["待匹配"])` 产生，沿用 styles.css:1054/1074 的 badge 样式。
- 禁止改 class、布局、计数 span、按钮 action、过滤 key；原存在的样式不因文字调整重写。

## 现状审计

### 前端写读路径

- `mailbox-chat.js:61–69` FILTER_CHIPS 定义 tab；`:1514–1519` 渲染按钮；`:1936` 请求原未关联邮件队列；`:2083/2088/2325/3082–3084/3147–3154/3180` 为文案与 aria-label。
- 只读队列，持久化写仍经原专家绑定/处理接口；本计划不改其参数或后端状态。
- `app.js:17033–17045` 为邮箱系统标签显示；`:21663` 按原 tags 值过滤，因而标签值 `"待匹配"` 不可直接替换。
- `index.html:774` 旧筛选 `<option value="待匹配">待匹配</option>`；仅文字改名。
- 后端 `MailboxService.computeTags:363` 仍返回 `"待匹配"`，作为兼容内部标签；显示层转换。无 DB 写入、无 schema 变更，无需修改 MailboxService 或其行号守卫测试。
- 交互点 X1：后端原 tags 值 → UI 显示映射 → 原值筛选。
- 交互点 X2：chip 切换 → 未关联队列分页/详情 lease → 返回专家草稿。

### 前端样式盘点

改动前模板（逐字）：

```javascript
{ key: CHIP_UNMATCHED, label: "待匹配" }
return `<button class="mc-filter" type="button" data-action="mc-filter" data-chip="${chip.key}" aria-pressed="${instance.chip === chip.key ? "true" : "false"}">${escapeText(chip.label)}${countSpan}</button>`;
```

```html
<div class="mc-scroll" tabindex="0" aria-label="待匹配来信详情"><div class="mc-empty">请选择左侧待匹配来信</div></div>
<option value="待匹配">待匹配</option>
```

最终覆盖基线（逐字，styles.css:12866–12870）：

```css
#view-mailbox.mc-refined .mc-filter{flex:0 0 auto;min-height:36px;padding:7px 5px 10px;border:0;border-bottom:2px solid transparent;border-radius:0;background:transparent;color:#7d8ca2;font-size:12px;line-height:1.5;white-space:nowrap}
#view-mailbox.mc-refined .mc-filter:hover{background:#f4f7ff;color:#3762d8}
#view-mailbox.mc-refined .mc-filter:active{background:#e9efff}
#view-mailbox.mc-refined .mc-filter[aria-pressed=true]{background:transparent;border-bottom-color:#3762d8;color:#3762d8;font-weight:600}
#view-mailbox.mc-refined .mc-filter:disabled{opacity:.45;cursor:not-allowed}
```

当前 index 缓存键为 `20261020-apollo-matching-v2`；`rg -n -F '20261020-apollo-matching-v2' src/test` 无命中。本次不需凭过往知识扩改固定值测试；实施前重查。（来源：K-frontend-cache-key-triad）

## 实现方案

1. 修改 mailbox-chat.js：替换用户可见“待匹配”文案及 aria-label，不改 key/id/接口。遵守 I-1/I-2/S-1。
2. 修改 app.js：renderMailboxTagBadges 对系统标签 `"待匹配"` 映射显示 `"待确认"`，样式 class 继续用原 key；不改 tags 数组、筛选值或用户自定义标签。遵守 I-1/I-2/S-1。
3. 修改 index.html：旧 option 仅改 label；已有版本化资源统一 bump 到 `20261020-auto-response-confirmation`。遵守 I-1/I-3/S-1。
4. 更新 mailboxChatBehavior.test.js / mailboxChatStyle.test.js 的显示断言。追加旧标签显示映射、真实 HTML option 原 value、新 label 的断言；证明请求参数、草稿恢复与分页未变。遵守 I-1～I-3/S-1。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-chat.js` | tab/空态/aria 文案 |
| 2 | `src/main/resources/static/app.js` | 系统标签显示映射 |
| 3 | `src/main/resources/static/index.html` | option 显示、缓存键 |
| 4 | `src/test/js/mailboxChatBehavior.test.js` | 文案与参数回归 |
| 5 | `src/test/js/mailboxChatStyle.test.js` | 样式/静态资源契约 |

5 个文件，单前端子系统。CSS 文件无变更。

## 验收标准

- I-1：请求仍为 unmatched-inbound/unmatchedOnly；option value 与 tags 数组仍使用旧值；数据条数、排序、分页与基线一致。
- I-2：页面各入口显示“待确认”；aria-label 同步；绑定动作仍为绑定专家。
- I-3：全部版本化资源同键；按旧键检查无残留固定测试；node 全量回归通过。
- S-1：CSS 无 diff；不新增 class/inline style；DOM 结构和按钮 action 保持；桌面/移动端目测字体、边距、选中状态。
- 执行 `node --check src/main/resources/static/mailbox-chat.js` 与 app.js，`node --test src/test/js/*.test.js`；不只跑旧 verify.sh。

## 人工验收清单

### A-1：完整文案与原流程
- 前置条件：测试库准备 21 封未命中邮件头/五分钟规则的未关联 MANUAL_REVIEW 来信，分页每页 20。
- 操作步骤：1. 打开收发件箱，点击待确认；2. 翻到第二页；3. 搜索其中一个主题；4. 打开详情并绑定已有专家。
- 预期结果：第 1 页 20 封、第 2 页 1 封；标题及辅助文字显示“待确认”；搜索结果与原队列一致；绑定后该封退出未关联队列并进入该专家正常处理。
- 覆盖：I-1/I-2、X2。

### A-2：标签兼容、草稿隔离
- 前置条件：已有一封后端 tags 包含 `"待匹配"` 的记录；专家会话保存一份正文 `draft-confirmation-check`。
- 操作步骤：1. 在旧邮箱筛选中选“待确认”；2. 检查对应标签展示；3. 切入待确认详情再回专家会话。
- 预期结果：旧标签筛选仍能命中记录，badge 显示“待确认”；草稿仍为 `draft-confirmation-check`，未被详情切换覆盖。
- 覆盖：I-1/I-2、X1/X2。

### A-3：空态、报错、样式及缓存
- 前置条件：空队列测试账号；浏览器保留发布前缓存；开发测试环境可模拟列表请求失败。
- 操作步骤：1. 普通刷新；2. 打开空队列；3. 模拟失败再重试；4. 桌面与 760px 以下宽度查看 tab。
- 预期结果：显示“待确认”“暂无待确认来信”“请选择左侧待确认来信”；失败提示“待确认来信加载失败，请重试。”，重试可恢复；桌面字号 12px，选中 #3762d8 下划线，移动端最小点击高度 44px；布局、其他 tab、跟进/挂起按钮保持。
- 覆盖：I-2/I-3/S-1。
