# 收发件箱三态标记 02：状态菜单与完整Tab

日期：2026-10-04；待审阅。依赖01真实接口验收；本计划6个文件。正式代码基线见主计划哈希，不把预览JS搬进正式系统。

## 需求描述

1. 七个Tab顺序：全部 / 已提供 / 跟进中 / 待处理 / 已挂起 / 已回复 / 待匹配。原“关注”展示名称全部换为“跟进中”，不修改专家自身标签中的“重点关注”等数据。
2. 所有已关联专家卡片右上角展示“未标记 / 跟进中 / 已提供”文字按钮。菜单根据当前状态出现两个操作：NONE→“跟进中、已提供”；FOLLOWING→“取消跟进、已提供”；PROVIDED→“跟进中、取消提供”。**不得出现“进入跟进/进入提供”。**没有星号、勾号、箭头、左右开关、清除图标、撤销按钮；详情删除原关注按钮。
3. 状态真实保存，当前页签和所有筛选保持；全部页原位更新，状态页过滤移出，取消后在全部中还能再次选择状态。
4. 七个Tab文字及已有计数完整显示；保留12px字、桌面36px/手机44px最小点击高度，空间不足自然换行，不横向滚动、不省略任何Tab。

必须保留：原默认选中策略（有未挂起待处理选待处理，否则跟进中）；高级筛选与账号/日期/分页；挂起及消息处理/原因编辑；已回复移出；待匹配关联处理；人工回复/草稿/材料/排期；移动端列表详情往返。
范围外：预览外壳、整站视觉重做、App.js业务重构、状态计数新增、批量操作、撤销、乐观提前成功、多窗口冲突检测、第三个“未标记”Tab、实时推送。

## 关键不变量

### Invariant I-1: UI消费唯一真实状态
- Rule：合法progressStatus为NONE/FOLLOWING/PROVIDED，标签和菜单严格按表展示；选项映射到显式状态PUT，取消均写NONE。旧响应完全缺字段时可从followed派生FOLLOWING/NONE以便兼容；字段存在但非法须显示“状态不可用”且禁用，不能冒充NONE。
- Applies to：列表渲染、API回包、摘要/挂起内存态。
- Violation consequence：无入口、取消不彻底、两个状态同时选中。
- 来源：original、01 I-1/I-4。

### Invariant I-2: 服务端分页与用户上下文
- Rule：已提供请求providedOnly=true，跟进中仍followed=true；不从当前页或内存拼全量队列。标记后保持chip/searchText/filters/page，服务端重查；空页只依现有refreshListWithFallback规则回退。不得把“修改卡片A状态”当作“选中A”，不得把A摘要塞给B正文。
- Applies to：chipParams/conversationsParams、状态提交、列表/选中项协调。
- Violation consequence：错页漏人、丢筛选、串专家正文。
- 来源：K-group-before-pagination，resolveFocusAndSelection源码。

### Invariant I-3: 请求失败与异步保护
- Rule：写入成功前不改变已显示状态；按contactId禁用重复操作，列表重绘也继承busy。成功更新匹配ID摘要并重查；切换筛选/专家期间的响应不恢复旧上下文。卸载后不改DOM。写入已成功但列表重查失败，提示“状态已保存，列表刷新失败，请重试”，不能报成写入失败或把DB已提交状态回滚。单写请求失败则旧状态保留，可重试。
- Applies to：状态动作、renderPerson、fetchList序号、unmount。
- Violation consequence：假成功、错回滚、连点写竞争、迟到回包覆盖当前页。
- 来源：original；现有listSeq/convEpoch及实例disposed保护。

### Invariant I-4: 挂起返回包含已提供
- Rule：挂起结束按响应决定：pending>0→待处理；pending=0+PROVIDED→已提供；pending=0+FOLLOWING→跟进中；NONE沿现有已回复服务端资格。对应文案使用新名字。更新标记后使同专家旧挂起GET失效，并重新读取真实挂起状态；不得调用resetSuspensionState清掉草稿以外的处理确认/完成锚点来偷懒。
- Applies to：suspension缓存初始化/映射、afterSuspensionRemoved、完成提示、状态更新后刷新。
- Violation consequence：已提供误跳跟进中/已回复、挂起提示仍称关注、迟到GET回写旧状态。
- 来源：K-mailbox-replied-membership、01 I-6。

### Invariant I-5: 边界与资源生命周期
- Rule：状态操作只请求新状态接口；不调用resolve/send/标签/材料操作。待匹配卡片无专家状态菜单。菜单不嵌入mc-person-main按钮；在同一host内委托，最多一个展开；再次点、外部点、Escape、换Tab/搜索/分页/重绘/卸载关闭；卸载解绑新增监听。
- Applies to：模板、onClick/onKeyDown、外部点击、挂载卸载、列表刷新。
- Violation consequence：按钮嵌套、操作冒泡选中详情、菜单残留、误写邮件。
- 来源：K-mailbox-popover-scope-and-fixed-containing-block；采用卡片内菜单，不新建body portal。

### Invariant I-6: 原流程与缓存不变
- Rule：维持默认探测、搜索日期、账号范围、待匹配、已回复移出、挂起与草稿/材料/排期业务；sessionStore只存窗口位置/邮件/草稿，不新增状态持久缓存。只把缓存版本统一更新到新值，不增加资源数量或注册顺序。
- Applies to：删除旧follow UI、菜单动作、资源注册与所有相关回归。
- Violation consequence：改状态破坏邮件工作台或旧缓存页面仍显示旧菜单。
- 来源：K-frontend-cache-key-triad、K-js-test-invocation-surface。

## 样式契约

### S-1: 七个Tab完整展示
- 复用：`.mc-filters/.mc-filter`来自 `mailbox-chat.css:78–79`、`styles.css:12648,12654–12662,12701`。以下两条**现有高优先级规则逐字保留**，不改后面挂起逐字合同块，不扩大整站左侧栏：
```css
@media(min-width:761px){#view-mailbox.mc-refined .mail-chat{grid-template-columns:380px minmax(0,1fr)}}
#view-mailbox.mc-refined .mail-chat .mc-filters{flex-wrap:wrap;overflow-x:visible;row-gap:0}
```
- 设计实值：Tab font-size12px、line-height1.5、gap4px、桌面padding7px 5px 10px/min-height36px；手机≤760px min-height44px、左右padding6px。选中主蓝#3762d8/2px底线；已挂起沿既有灰色#475569/#64748b。待处理/挂起计数沿用16px高现有badge。
- DOM：保留 `.mail-chat > aside.mc-experts > .mc-list-tools > .mc-filters`；七个现有 `.mc-filter[data-action=mc-filter]`，data-chip严格 `all/provided/followed/pending/suspended/replied/unmatched`，各按钮完整中文名，pending/suspended保留 `.mailbox-suspend-count`。
- 不新增CSS来“近似”修复；现有规则已经wrap，需保留实际宿主 `#view-mailbox.mc-refined` 和 `.mail-chat`，不能用无宿主的预览代替验证。
- 使用点：`mailbox-chat.js` FILTER_CHIPS与主shell（见证据完整grep），`styles.css`/`mailbox-chat.css`规则；无其他页面独立mc-filters实例。新增一个按钮是唯一结构变化。
- 禁止：overflow-x:auto/scroll覆盖、nowrap覆盖、Tab文字ellipsis、display:none隐藏Tab、字体降为10px、强塞单行。

### S-2: 卡片右上角状态与菜单（逐字CSS）
- 复用：`.mc-person/.mc-person-main/.mc-person-heading`见mailbox-chat.css:16–22,157–158；`[data-role=person-actions]`见styles.css:11960–11965。只派生 `.mailbox-progress-card`；全部既有使用点见证据，**不就地改写其他卡片规则**。
- 新增：下列块追加至styles.css末尾，必须逐字复制；新class仅限下列5个。禁改字节锁定mailbox-chat.css。
```css
/* mailbox-progress-contract:start */
.mail-chat .mc-person.mailbox-progress-card{grid-template-columns:minmax(0,1fr)}
.mail-chat .mailbox-progress-card .mc-person-main{grid-column:1 / -1;padding-right:12px}
.mail-chat .mailbox-progress-card .mc-person-heading{padding-right:76px;min-height:30px}
.mail-chat .mailbox-progress-card .mc-person-main small:first-of-type{padding-right:76px}
.mail-chat .mailbox-progress-card [data-role=person-actions]{position:absolute;top:10px;right:10px;width:64px;align-items:flex-end;gap:3px}
.mailbox-progress{position:relative;display:block;flex:none}
.mailbox-progress-status{display:block;min-width:56px;min-height:28px;padding:4px 9px;border:1px solid #dce4ef;border-radius:6px;background:#fff;color:#64748b;font:inherit;font-size:11px;line-height:18px;white-space:nowrap;cursor:pointer;box-shadow:none}
.mailbox-progress-status[data-progress=FOLLOWING],.mailbox-progress-status[data-progress=PROVIDED]{color:#1e40af;background:#eff4ff;border-color:#d3dff6}
.mailbox-progress-status:hover:not(:disabled),.mailbox-progress-status[aria-expanded=true]{border-color:#93addd;background:#eaf0fb}
.mailbox-progress-status:active:not(:disabled){background:#dfe9fb}
.mailbox-progress-status:disabled{opacity:.45;cursor:not-allowed}
.mailbox-progress-status:focus-visible{outline:2px solid #1e40af;outline-offset:3px}
.mailbox-progress-menu{position:absolute;right:0;top:calc(100% + 6px);z-index:20;width:116px;padding:4px;border:1px solid #dce4ef;border-radius:8px;background:#fff;box-shadow:0 6px 18px #26395918}
.mailbox-progress-menu[hidden]{display:none!important}
.mailbox-progress-option{display:block;width:100%;min-height:34px;padding:7px 11px;border:0;border-radius:5px;background:#fff;color:#475569;text-align:left;font:inherit;font-size:12px;line-height:20px;white-space:nowrap;cursor:pointer}
.mailbox-progress-option:hover:not(:disabled),.mailbox-progress-option:focus-visible{background:#eff4ff;color:#1e40af;outline:none}
.mailbox-progress-option:active:not(:disabled){background:#dfe9fb}
.mailbox-progress-option:disabled{opacity:.45;cursor:not-allowed}
@media(max-width:760px){.mail-chat.mobile-core-mailbox .mailbox-progress-status{min-height:44px;height:auto;white-space:nowrap}.mail-chat .mailbox-progress-card .mc-person-heading{min-height:44px}.mailbox-progress-option{min-height:44px}}
/* mailbox-progress-contract:end */
```
- DOM（以下以未标记为例；模板值一律escapeText；取消时对应data-progress=NONE）：
```html
<div class="mc-person mailbox-progress-card" data-contact-id="123" data-active="false" data-replied="false">
  <button class="mc-person-main" type="button" data-action="mc-select-expert" data-contact-id="123">
    <span class="mc-person-heading"><strong title="完整专家姓名">专家姓名</strong></span>
    <!-- 账号、最近发件、上次回复、收发计数/标签、排期槽逐字保留现有结构 -->
  </button>
  <span data-role="person-actions">
    <span class="mailbox-progress">
      <button class="mailbox-progress-status" type="button" data-action="mc-progress-menu"
              data-contact-id="123" data-progress="NONE" aria-haspopup="menu"
              aria-expanded="false" aria-label="专家姓名：未标记，选择状态">未标记</button>
      <span class="mailbox-progress-menu" role="menu" aria-label="专家姓名的状态操作" hidden>
        <button class="mailbox-progress-option" type="button" role="menuitem"
                data-action="mc-set-progress" data-contact-id="123" data-progress="FOLLOWING">跟进中</button>
        <button class="mailbox-progress-option" type="button" role="menuitem"
                data-action="mc-set-progress" data-contact-id="123" data-progress="PROVIDED">已提供</button>
      </span>
    </span>
    <!-- 已回复页原mc-dismiss-replied按钮保留在此；其他页无此按钮 -->
  </span>
  <!-- 原suspensionCardFooterHtml(item)原位保留 -->
</div>
```
- 实际新增class共5个：mailbox-progress-card、mailbox-progress、mailbox-progress-status、mailbox-progress-menu、mailbox-progress-option。规则中其余class均复用现有声明。
- 不把菜单搬到body，不添加背景遮罩。展开后仅在现有列表滚动容器内让菜单scrollIntoView(nearest)，确保最后一张卡片菜单可见；不得动态写inline style定位。
- 姓名空间不足可沿用已有ellipsis，title保留全名；**Tab不许ellipsis**。详情不显示此控件。保留既有已回复“移出”和挂起卡片底部操作，不把它们当“额外图标”删除。

### S-3: 详情与挂起提示
- 删除renderHeader中旧mc-toggle-follow按钮，不新增替代元素；材料/管理/新增及编辑取消排期按钮DOM原样保留。
- 原挂起banner/完成行样式复用 `styles.css:12663–12701`，只改中文状态词与归类目标，不增图标、不复制菜单进详情。
- 所有提示通过既有hostShowStatus输出；success/error使用现有状态样式，不新增toast组件。

### S-4: 资源与CSS边界
- 保留index.html现有5 CSS+6 JS的名字、顺序、数量；11项版本统一改 `20261004-mailbox-progress`，无新script/link。
- mailbox-chat.css与历史target.css字节一致；CSS新增块只在styles.css，模板所有新业务class在该文件有字面声明，无inline style。
- 版本实施前重新按index当前键精确搜索src/test；当前`20261004-mailbox-suspension-followup`为0命中，不按历史知识凭空增加9个测试修改。若并行变更引入固定键文件，先修订清单。（来源：K-frontend-cache-key-triad）

## 现状审计

### 前端状态、请求与临时缓存

- `mailbox-chat.js:50–69`定义6个Tab；503 chipParams和1476 conversationsParams构造请求；首查1717附近总数为0退关注。新增provided不改变首查策略。
- `1575`卡片星标与`2893`详情关注按钮共用mc-toggle-follow；`4765–4796`toggleFollow是乐观更新后请求旧PUT/DELETE follow，再fetchList。新菜单应替换这条前端写路径，不能旁边再保留另一套状态逻辑。
- `fetchList:1689–1759`在失败和过期响应时返回null，catch不向外抛异常；仅写`.catch`不能识别“保存成功、刷新失败”。`refreshListWithFallback:2428`目前收到null仍继续空页/选中项协调，新操作接入时必须补null短路，避免旧请求的后续动作影响新页面。
- `1630`renderList及`fetchList`回写instance.list.items/total；`refreshListWithFallback:2428`和`resolveFocusAndSelection:2601`负责空页/当前详情。selectedSummary由列表行及选择逻辑维护。
- sessionStore:525–594以user|accountScope|contactId为键，只存消息窗口、滚动、草稿。touch/get/upsert/drop为写/读入口；状态不是持久缓存的一部分，本计划不改这些字段。
- `suspension`初始化630、reset1799、view1824、load1874、apply1897、afterSuspensionRemoved2220、完成提示2369含followed。必须加progressStatus传递，不能仅改最终跳转。
- 监听：host委托1213；outside/document keydown8231/8217；mount8514注册，unmount8469移除。复用这些机制，不复制预览document匿名监听到正式系统。

交互点：
- P-1 后端持久写→summary→卡片菜单/Tab与total。
- P-2 写入回包→列表重查→选中项/正文/草稿协调。
- P-3 标记写入→挂起GET/DELETE回包→结束挂起归类与提示。
- P-4 菜单与滚动/手机返回/卸载→事件和焦点归属。
- P-5 缓存键→浏览器新JS/CSS同时加载。

### 前端样式盘点

可复用class/实值：
- mailbox-chat.css:1 两栏306px/minmax(0,1fr)、gap16px、12px/1.6；实际正式页styles.css:12647覆为380px。
- mailbox-chat.css:15 列表overflow:auto；16 卡片position:relative、grid minmax(0,1fr) 28px；19主按钮12px 0 12px 10px；21姓名13px/600/ellipsis。
- mailbox-chat.css:157–164姓名/计数标签单行；styles.css:11960–11965已回复操作列42px和移出按钮；新派生类必须覆盖其布局但保留动作。
- styles.css:3主色#1e40af，4hover#1e3a8a；21正文#1e293b，25辅文#475569；15的panel-bg是半透明rgba(255,255,255,.55)，菜单不能复用它，使用#fff实底。
- styles.css:12648优先级(1,3,0)明确flex-wrap:wrap，胜过12654(1,2,0)的nowrap；保留760px手机44px规则。预览index未包含正式宿主，因此原预览Tab溢出不是“正式样式没有wrap”的证据。
- setRefined:819给view-mailbox加mc-refined；app.js:16817亦维护该宿主类。此计划不改app.js。
- DOM class测试`mailboxChatStyle.test.js:125`要求新mc-*必须定义在字节锁定CSS；故新增前缀用mailbox-progress-*，不是mc-progress-* CSS类。data-action可用mc-progress-menu，不受class规则限制。

改动前逐字摘录：
```javascript
const CHIP_FOLLOWED = "followed";
```
```html
<button class="mc-follow" type="button" data-action="mc-toggle-follow" data-contact-id="${escapeText(item.contactId)}" aria-label="${item.followed ? "取消关注该专家" : "关注该专家"}" aria-pressed="${item.followed ? "true" : "false"}">${item.followed ? "★" : "☆"}</button>
```
```css
#view-mailbox.mc-refined .mail-chat .mc-filters{flex-wrap:wrap;overflow-x:visible;row-gap:0}
#view-mailbox.mc-refined .mc-filters{display:flex;flex-wrap:nowrap;gap:4px;overflow-x:auto;scrollbar-width:thin}
```
完整Tab模板、卡片/详情片段、基础CSS逐字存于 [frontend-baseline.md](mailbox-progress-evidence/frontend-baseline.md)，全部相关class使用位置在 [grep-audit.txt](mailbox-progress-evidence/grep-audit.txt)。上述使用点派生追加，不删改现有逐字合同块。

## 实现方案

### T-1：Tab与卡片菜单（I-1/I-2/I-5/I-6；S-1/S-2/S-3）
文件：`src/main/resources/static/mailbox-chat.js`、`src/main/resources/static/styles.css`。
- 增加CHIP_PROVIDED，放all后、followed前；label“关注”改“跟进中”；chipParams/conversationsParams新增providedOnly。
- 在renderPerson渲染S-2菜单；所有已关联专家，包括无标记/待处理/挂起/已回复卡片都能操作；renderUnmatched不插入该控件。
- 新增一个纯状态解析/菜单定义函数，统一label与两选项；使用现有escapeText。删除详情关注按钮与原前端toggleFollow路径/disabled渲染辅助，只删无调用的相关函数，不改后端旧兼容API。
- 增加实例菜单打开ID与按contactId的busy集合；按钮只显示当前状态，aria-label含全名/当前状态/选择状态；无额外可见图标。
- 点击菜单按钮不调用selectExpert；打开焦点移到首项。ArrowUp/Down/Home/End移动，Enter/Space使用按钮点击，Escape关闭并回触发按钮；Tab按自然顺序离开，关闭菜单时焦点不得落在hidden元素。
- 复用host/onDocumentKeyDown/onOutsideFilterClick的生命周期，列表重绘关闭旧菜单。成功导致行移除时焦点移到当前Tab；行保留时可回同卡状态按钮。

### T-2：真实保存与失败（I-1/I-2/I-3/I-5；S-2/S-3）
文件：`src/main/resources/static/mailbox-chat.js`。
- `mc-set-progress`捕获contactId、显式枚举、当前实例身份；冻结默认探测（调用现有freezeDefaultProbe），不改selectedContactId/chip/search/filters。
- 新PUT `/api/mail/mailbox/conversations/{id}/progress-status`，body仅status。保存中禁用该卡菜单相关按钮；不提前改变状态。重复相同状态不生成重复请求。
- 成功只更新matching ID的list item/selectedSummary，followed从返回progressStatus派生，再调用refreshListWithFallback，复用现有listSeq淘汰旧列表响应；不在前端按状态过滤一页伪造total。
- 同专家已有挂起读上下文时，递增suspension.seq使旧GET失效，更新progressStatus/followed，调用loadSuspensionState重查；保留reason/锚点/处理确认，不能reset整个挂起状态。若已换专家，禁止刷新旧专家详情。
- 写失败与后续列表重查失败分开处理：现有fetchList失败返回null，不能假定Promise会reject。在refreshListWithFallback的首次与回退fetch后均先检查disposed/data==null，命中即返回，不再做分页或选中项协调。状态动作记录本次刷新上下文，只有仍属当前上下文、list.error非空且非loading时显示“状态已保存，列表刷新失败，请重试”；过期null不报失败、不重试旧页。finally清busy，只对存活实例重绘。迟到的A请求不覆盖B摘要，卸载后的响应不触碰新host。

### T-3：挂起归类和文字（I-1/I-4/I-6；S-3）
文件：`src/main/resources/static/mailbox-chat.js`。
- 在所有初始化/重置/view/apply路径传progressStatus，旧followed兼容仅用于字段缺失。
- afterSuspensionRemoved按I-4优先级选Tab；已提供的零待处理提示“已结束挂起，可在「已提供」查看”，跟进中同理。完成提示中“保留关注”改按真实状态描述，“未标记”只说明沿原已回复规则。
- 标记状态变更本身不改变挂起、不标记消息已处理；菜单文案“已提供”不表示材料数变化。

### T-4：缓存与测试（I-1～I-6；S-1～S-4）
文件：`src/main/resources/static/index.html`、`src/test/js/mailboxChatBehavior.test.js`、`src/test/js/mailboxSuspension.test.js`、`src/test/js/mailboxChatStyle.test.js`。
- index只改11项版本键，资源名/顺序不变。
- Behavior复用现有DOM harness：1373/2931两处Tab数组及相邻中文数组更新；2533旧星标乐观测试替换为真实状态菜单行为测试。stub增加progress-status端点和progressStatus响应，覆盖6条转换、未标记入口、失败/延迟（含PUT成功后GET失败，以及GET过期返回null不能触发旧页回退）、不同卡片、页面/搜索保留、分页末行回退、详情无控件、待匹配无菜单；不改专家标签“重点关注”数据。
- Suspension在1409归类矩阵加入PROVIDED；检查旧字段缺失兼容、状态更改后旧GET不能覆盖、提供+挂起结束、手机返回及处理确认仍可用。
- Style新增S-2完整块字节包含断言、所有新class白名单、没有旧星标/详情控制、S-1高优先级换行规则仍在。保留原mailbox-chat.css字节比较和手机合同原断言。
- 浏览器复核使用真实正式宿主，不仅Node DOM stub。新功能实装前不能宣称几何/持久化检查已通过。

## 变更文件清单

| # | 文件 | 目的 |
|---:|---|---|
| 1 | src/main/resources/static/mailbox-chat.js | Tab、菜单、保存、挂起状态映射 |
| 2 | src/main/resources/static/styles.css | 追加S-2精确样式 |
| 3 | src/main/resources/static/index.html | 11资源同步版本键 |
| 4 | src/test/js/mailboxChatBehavior.test.js | 三态/异步/分页/详情隔离 |
| 5 | src/test/js/mailboxSuspension.test.js | 挂起与PROVIDED交互 |
| 6 | src/test/js/mailboxChatStyle.test.js | 样式契约与完整Tab约束 |

共6文件、1子系统；不改mailbox-chat.css、app.js或既有mockups。独立预览是设计参考，不是正式代码路径。

## 验收标准

- I-1：六转换、取消回NONE；合法菜单逐字匹配；字段缺失兼容与非法禁用；刷新仍读取DB状态。
- I-2：providedOnly/followed请求明确互斥，服务端total使用；当前页签/全部筛选未清空；分页最后一条移出仅回退一页；修改非选中卡片不串正文。
- I-3：slow/reject场景验证busy继承、无提前成功、写成功而list失败单独提示、切Tab/搜索/选B/卸载的迟到回包；重试可恢复。
- I-4：挂起结束矩阵全部4分支；同专家延迟挂起GET不回写旧progress；处理完成锚点/原因/草稿保留。
- I-5：菜单开关/外部/键盘/翻页/重绘/卸载；动作不触发selectExpert；无多余write请求；待匹配仍无控件。
- I-6：默认选择、原高级筛选、已回复移出、待匹配、人工回复/草稿/材料/排期和手机返回现有用例通过；缓存不存状态。
- S-1：真实窗口宽320/393/760/1024/1440/1920px，各Tab文字完整，按钮边界在filters与tools内部，filters.scrollWidth<=filters.clientWidth+1；7按钮无水平遮挡，允许纵向wrap；计数以0/1/9999检查不裁切（验收夹具，不修改生产）。字体computed12px、桌面min-height36px、手机44px。
- S-2：styles新增块与本计划逐字一致；DOM骨架/所有新增类均映射；没有inline style/未声明class。首行、最后一行、长姓名、菜单展开均不挡状态按钮或被列表裁掉，菜单白底#fff且无图标。
- S-3：详情原关注控件不存在；其他动作及原挂起DOM保留，跳转/提示匹配状态。
- S-4：11个资源同键、无增删；冻结CSS仍字节一致；按实际旧键复查测试命中清单。

```sh
node --check src/main/resources/static/mailbox-chat.js
node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxSuspension.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxSuspensionStyle.test.js src/test/js/mailboxSuspensionFollowup.test.js src/test/js/mobileCoreNavigation.test.js
node --test src/test/js/*.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```
最后两条为发布前全量回归，不把verify.sh当作全量前端门禁。（来源：K-js-test-invocation-surface）

## 人工验收清单

### A-1: 未标记入口与全部六条转换
- 前置条件：隔离验收环境有专家X及真实往来邮件，当前用户未标记；登录后在全部页用姓名搜索X。
- 操作步骤：1.点右上角“未标记”，选择“跟进中”。2.选择“已提供”。3.选择“取消提供”。4.再选“已提供”。5.选“跟进中”。6.选“取消跟进”。每步刷新后重新查X。
- 预期结果：按钮依次未标记/跟进中/已提供/未标记/已提供/跟进中/未标记，菜单与需求表相同；任何菜单没有“进入”两字；当前仍全部，搜索未被清空；刷新持久化；始终能重新选择状态。
- 覆盖：I-1/I-2/I-5，S-2，P-1/P-5，需求2/3。

### A-2: 状态页互斥、分页与详情隔离
- 前置条件：当前用户21位跟进中专家，20人/页；另有专家A、B可同时在全部第一页查看。
- 操作步骤：1.跟进中第二页将唯一专家改已提供。2.查看已提供页。3.在全部选中B，再改A状态。4.取消A的提供后回全部搜索。
- 预期结果：跟进中total20，回第一页；已提供出现该专家且不在跟进中；修改A不把B详情名称/邮件正文换成A；取消后A显示未标记且未删除会话。现有时间顺序不因标记改变。
- 覆盖：I-1/I-2/I-3，P-1/P-2，原分页排序不变。

### A-3: 失败、迟到回包与菜单键盘
- 前置条件：验收环境可通过浏览器网络工具将状态PUT延迟/阻断；有A、B两个专家。
- 操作步骤：1.阻断PUT，操作A，连续点击，再取消阻断重试。2.延迟A请求，切换Tab并选B，再放行。3.展开菜单按上下/Home/End/Enter/Escape及Tab。4.展开后点击外部、翻页、切离收发件箱再返回。
- 预期结果：失败前后A旧状态保留、提示失败；忙碌期间不重复PUT；重试成功；迟到A不切回旧Tab且B正文不变。Escape回触发按钮；Tab不留在隐藏菜单；外部/切页/卸载关闭菜单，没有双份监听。
- 覆盖：I-3/I-5，S-2，P-2/P-4。

### A-4: 七个Tab与卡片位置
- 前置条件：正式宿主页面，计数夹具覆盖0/1/9999；使用浏览器响应式模式设置320/393/760/1024/1440/1920px宽度；有长姓名专家及至少一整页列表。
- 操作步骤：1.逐宽度检查全部七Tab并依次点击。2.滚动到最后一张卡展开状态菜单。3.检查长姓名卡、详情栏；手机点卡片并返回。
- 预期结果：顺序全部/已提供/跟进中/待处理/已挂起/已回复/待匹配；12px文字完整、无省略号/横向滚动，窄屏换行；计数完整；桌面点击区≥36px、手机≥44px。卡片右上角只有当前状态文字按钮，白底菜单可见；无开关/星/勾/箭头/撤销，详情无状态操作；长姓名悬停能看全名。
- 覆盖：S-1/S-2/S-3/S-4，I-5，P-4/P-5，需求1/2/4。

### A-5: 挂起结束四分支与未处理保护
- 前置条件：准备4位已挂起专家：X有1条待处理且PROVIDED；Y无待处理且PROVIDED；Z无待处理且FOLLOWING；W无待处理、NONE且符合已回复真实来信/发件/水位条件。用验收后台/01API准备，禁止直接改生产数据。
- 操作步骤：1.依次结束挂起。2.检查各自归类。3.另选仍挂起专家切换标记，再改原因、处理来信至零、选择继续挂起/结束挂起。
- 预期结果：X→待处理且仍已提供；Y→已提供；Z→跟进中；W→已回复。状态切换不更改挂起或处理数，原因可保存；继续挂起仍保留；原完成确认可用；没有“关注”旧页面文案。
- 覆盖：I-4/I-6，S-3，P-3；原挂起/处理回归。

### A-6: 搜索、默认页、已回复移出与待匹配
- 前置条件：存在两个激活账号的会话、1个未挂起待处理、1个待匹配、1个NONE且符合已回复的专家；日期/主题/标签各有可区分样本。
- 操作步骤：1.首次进入收发件箱。2.切已提供设账号、日期、关键词、标签筛选，选择当前匹配专家进行取消。3.在已回复点击原“移出”。4.打开待匹配并执行已有匹配/处理流程。5.处理完全部未挂起待处理后重新进入页面。
- 预期结果：首次默认待处理；状态操作不清除筛选；已回复移出仍生效且未误改为提供；待匹配没有状态菜单、匹配和处理入口正常；无待处理时默认跟进中。
- 覆盖：I-2/I-5/I-6，P-1/P-2；筛选/默认/原队列回归。

### A-7: 草稿、材料、排期与手机布局回归
- 前置条件：验收环境启用模拟邮件传输，专家B有人工回复入口/材料/排期；A同页可操作。
- 操作步骤：1.B填写未发送草稿。2.在列表修改A状态再查看B。3.窄屏返回列表后重新打开B。4.查看B材料、编辑测试排期、发送到模拟收件箱。
- 预期结果：草稿内容和回复目标/账号保持；材料数和文件入口未被标记操作改变；原排期操作可用，状态操作本身没有新增邮件；仅最后显式发送产生1封模拟邮件；手机返回导航正常。
- 覆盖：I-2/I-3/I-6，S-3，P-2/P-4；原邮件工作台回归。

人工验收开始才从本节导出 `mailbox-progress-02-frontend-acceptance.md`，含A-n、勾选框、验收人、日期、结果/备注；修订先改本节再导出。
