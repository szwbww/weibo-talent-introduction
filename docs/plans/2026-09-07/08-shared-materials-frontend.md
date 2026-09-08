# 08 · 共享材料组件与专家页内嵌

状态：待审阅/未执行。前置：07子计划通过独立验证。 范围：5个文件；不超过2子系统。共同契约见[总计划](00-mailbox-materials-master.md)。

## 需求描述

专家上传资料区变为可搜索、分页、跨页选择的统一材料面板；提供同一个抽屉挂载能力供聊天页使用。

不得改变：正式专家列表/详情操作、宽度偏好、类型菜单、别名、阶段历史、AI入口、旧下载和预览。

范围外：不重建正式导航、不引入框架、不将预览mock数据/定时器/全局覆盖函数放进生产。

## 关键不变量

### Invariant I-1：唯一组件与存储
- Rule：新增window.ExpertMaterials IIFE，mount({host,contactId,mode})/unmount；同contactId共用一个store与下载请求实现，mode仅inline/drawer/selectionOnly布局差异；页面切换不重置服务器任务。
- Applies to：新JS、app专家host、未来聊天host
- Violation consequence：两个独立队列或入口状态不一致
- 来源：original

### Invariant I-2：渲染与请求
- Rule：GET不触发POST/文件获取；300ms搜索防抖与请求epoch避免旧专家覆盖；只局部更新行状态，不重建输入和编辑器。
- Applies to：组件加载/筛选/轮询/unmount
- Violation consequence：焦点丢失、串专家
- 来源：K-state-input-no-per-keystroke-innerhtml

### Invariant I-3：选择与任务
- Rule：10条/页，表头仅选本页；selection按contactId保存并跨页跨筛选保留；POST只传所选，最多500，不隐式全选全部专家；关闭仍可在另入口看进度。
- Applies to：选择store/提交/清空/状态轮询
- Violation consequence：选10却下载全部或选错专家
- 来源：original

### Invariant I-4：渐进加载
- Rule：新脚本未加载时app保留旧renderExpertDocuments；加载后只替换资料卡内部。原始ES专家无contactId不挂载网络材料组件，显示“尚未建立联系，暂无资料”。
- Applies to：loadContactDetail/showExpertDetail/renderExpertDocuments
- Violation consequence：分阶段部署空白页或假id
- 来源：K-expert-detail-two-panel-render-sites

## 样式契约

所有新DOM必须映射[完整契约S-1至S-5](ui-style-contract.md)，既有DOM/CSS逐字见[baseline](frontend-baseline.md)。禁止新增inline style、未声明class、修改既有全局规则。button复用styles.css:802/838；专家原卡复用:1659；可信内部样式复用:7329起。

### S-1：资料全量CSS与DOM（原样复制）

```css
.expert-materials{display:flex;flex-direction:column;min-width:0;min-height:0;color:#475569;font-size:12px;line-height:1.5;background:#f8faff;border:1px solid #dce4ef;border-radius:10px;overflow:hidden}
.expert-materials [hidden]{display:none!important}
.expert-materials *{box-sizing:border-box}
.expert-materials h3,.expert-materials p{margin:0}
.expert-materials header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 14px}
.expert-materials header h3{font-size:13px;font-weight:600;color:#475569}
.expert-materials header h3 span{margin-left:8px;font-size:12px;font-weight:400;color:#64748b}
.expert-materials .em-policy{margin:0 14px;padding:10px 12px;border:1px solid #dbe7fa;border-radius:7px;background:#eff5ff;font-size:12px;color:#475569}
.expert-materials .em-stats{display:flex;flex-wrap:wrap;gap:8px 18px;padding:12px 14px;color:#64748b;font-size:11px}
.expert-materials .em-stats strong{margin-left:4px;color:#334155;font-weight:600}
.expert-materials .em-filters{display:grid;grid-template-columns:minmax(140px,1fr) 148px 120px;gap:8px;padding:0 14px 12px}
.expert-materials .em-filters input,.expert-materials .em-filters select{width:100%;min-width:0;min-height:32px;height:32px;margin:0;padding:0 8px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#475569;font:inherit}
.expert-materials .em-filters input::placeholder{color:#94a3b8}
.expert-materials .em-table-head{display:grid;grid-template-columns:20px minmax(0,1fr) 150px;align-items:center;gap:10px;padding:9px 14px;border-top:1px solid #e2e8f0;border-bottom:1px solid #e2e8f0;background:#f1f5f9;color:#64748b;font-size:11px}
.expert-materials input[type=checkbox]{width:14px;height:14px;min-height:0;flex:none;margin:0;accent-color:#1e40af;cursor:pointer}
.expert-materials input[type=checkbox]:disabled{opacity:.45;cursor:not-allowed}
.expert-materials .em-rows{min-height:0;max-height:360px;overflow:auto;padding:0 14px;overscroll-behavior:contain}
.expert-materials .em-row{display:grid;grid-template-columns:20px minmax(0,1fr) 150px;align-items:center;gap:10px;min-height:72px;padding:11px 0;border-bottom:1px solid #e2e8f0}
.expert-materials .em-row:last-child{border-bottom:0}
.expert-materials .em-row[data-selected=true]{background:#eff5ff}
.expert-materials .em-file{display:flex;flex-direction:column;min-width:0;gap:4px}
.expert-materials .em-file strong{display:block;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;font-size:12px;font-weight:500;color:#334155}
.expert-materials .em-file small{font-size:11px;color:#64748b;overflow-wrap:anywhere}
.expert-materials .em-state{display:flex;flex-wrap:wrap;align-items:center;gap:4px 8px;font-size:11px;min-width:0}
.expert-materials .em-state>span,.expert-materials .em-state>small{width:100%;overflow-wrap:anywhere}
.expert-materials .em-state>small{font-size:11px;color:#64748b}
.expert-materials .em-state[data-state=METADATA_ONLY]{color:#64748b}
.expert-materials .em-state[data-state=QUEUED],.expert-materials .em-state[data-state=DOWNLOADING]{color:#1e40af}
.expert-materials .em-state[data-state=STORED]{color:#059669}
.expert-materials .em-state[data-state=FAILED]{color:#b45309}
.expert-materials .em-state[data-state=SOURCE_UNAVAILABLE]{color:#e11d48}
.expert-materials .em-state progress{width:100%;height:5px;margin:2px 0;accent-color:#3b82f6}
.expert-materials .em-link{display:inline-flex;align-items:center;min-height:24px;padding:0;border:0;background:transparent;color:#1e40af;font:inherit;text-decoration:none;cursor:pointer}
.expert-materials .em-link:hover{color:#1e3a8a;text-decoration:underline}
.expert-materials .em-link:active{color:#172554}
.expert-materials .em-link:disabled{color:#94a3b8;cursor:not-allowed;text-decoration:none}
.expert-materials .em-empty{padding:28px 14px;text-align:center;color:#64748b}
.expert-materials .em-error{margin:10px 14px;padding:10px 12px;border:1px solid #fecdd3;border-radius:7px;background:#fff1f2;color:#be123c}
.expert-materials .em-pager{display:flex;align-items:center;flex-wrap:wrap;gap:8px;padding:10px 14px;border-top:1px solid #e2e8f0}
.expert-materials .em-pager>span{margin-right:auto;color:#64748b;font-size:11px}
.expert-materials footer{display:flex;flex-wrap:wrap;align-items:center;gap:8px;padding:12px 14px;background:#eff5ff;border-top:1px solid #dce4ef}
.expert-materials .em-selection{display:flex;flex-direction:column;gap:3px;margin-right:auto;min-width:140px}
.expert-materials .em-selection small{font-size:11px;color:#64748b}
.expert-materials .button{flex:none;white-space:nowrap}
.expert-materials .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.expert-materials :is(button,a,input,select,summary):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.em-drawer{position:fixed;inset:12px 12px 12px auto;width:min(720px,calc(100vw - 24px));height:calc(100dvh - 24px);max-width:none;max-height:none;margin:0;padding:0;border:1px solid #dce4ef;border-radius:14px;background:#f8faff;color:#475569;box-shadow:-12px 0 50px #24344c24}
.em-drawer::backdrop{background:rgba(28,48,72,.28)}
.em-drawer[open]{display:flex;flex-direction:column}
.em-drawer>.expert-materials{flex:1;border:0;border-radius:14px}
.em-drawer .em-rows{flex:1;max-height:none}
.em-analysis{display:flex;flex-direction:column;gap:12px;min-width:0;color:#475569;font-size:12px;line-height:1.6}
.em-analysis .em-analysis-note{margin:0;padding:10px 12px;background:#eff5ff;border:1px solid #dbe7fa;border-radius:7px}
.em-analysis .expert-materials .em-rows{max-height:300px}
.em-analysis .em-analysis-actions{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap;padding-top:10px;border-top:1px solid #e2e8f0}
.em-analysis .em-analysis-actions>span{color:#64748b}
.em-analysis .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.em-analysis :is(button,a,input,select):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
@media(max-width:1100px){.expert-materials .em-filters{grid-template-columns:minmax(0,1fr) 120px}.expert-materials .em-filters input{grid-column:1/-1}.expert-materials .em-table-head,.expert-materials .em-row{grid-template-columns:18px minmax(0,1fr) 126px;gap:8px}.expert-materials footer .em-selection{width:100%}}
@media(max-width:760px){.em-drawer{inset:8px;width:calc(100vw - 16px);height:calc(100dvh - 16px)}.expert-materials header{align-items:flex-start}.expert-materials .em-filters{grid-template-columns:1fr 1fr}.expert-materials .em-table-head,.expert-materials .em-row{grid-template-columns:16px minmax(0,1fr) 108px;gap:6px}.expert-materials .em-rows{max-height:360px}.expert-materials header,.expert-materials .em-stats,.expert-materials footer{padding:10px}.expert-materials .em-rows{padding:0 10px}.expert-materials .em-policy{margin:0 10px}.expert-materials .em-filters{padding:0 10px 10px}.expert-materials .em-table-head{padding:9px 10px}}
@media(prefers-reduced-motion:reduce){.expert-materials .button{transition:none}.expert-materials .button:hover,.expert-materials .button:active{transform:none}}
```

```html
<!-- S-1: 同一 ExpertMaterials.mount 渲染；inline 无 dialog，drawer 仅多一层原生 dialog -->
<dialog class="em-drawer" aria-label="专家资料管理">
  <section class="expert-materials" aria-label="专家上传资料" aria-busy="false">
    <header><h3>专家上传资料 <span>40 份</span></h3><button class="button" type="button" data-action="close">关闭</button></header>
    <p class="em-policy">检查回复只登记附件信息。查看清单不会获取文件；点击“获取到服务器”后开始下载。</p>
    <div class="em-stats"><span>已存服务器<strong>1</strong></span><span>待获取<strong>39</strong></span><span>获取中<strong>0</strong></span><span>失败<strong>0</strong></span></div>
    <div class="em-filters"><input type="search" aria-label="搜索文件名" placeholder="搜索文件名"><select aria-label="来源来信"><option>全部来信</option></select><select aria-label="存储状态"><option>全部状态</option></select></div>
    <div class="em-table-head"><input type="checkbox" aria-label="选择本页"><span>文件 / 类型 / 来源</span><span>存储状态 / 操作</span></div>
    <div class="em-rows" aria-label="材料列表">
      <div class="em-row" data-selected="false"><input type="checkbox" aria-label="选择文件"><div class="em-file"><strong title="完整文件名.pdf">完整文件名.pdf</strong><small>简历 · 待审核 · 邮箱估算 182.5 KB</small><small>来信主题 · 2026-09-07 14:42</small></div><div class="em-state" data-state="METADATA_ONLY"><span>仅文件信息</span><button type="button" class="em-link" data-action="fetch">获取到服务器</button></div></div>
    </div>
    <p class="em-empty" hidden>暂无资料文件。</p><div class="em-error" role="alert" hidden>加载失败，请重试。<button class="em-link" type="button" data-action="reload">重试</button></div>
    <div class="em-pager"><span>共 40 份 · 10 条/页 · 第 1/4 页</span><button class="button" type="button" disabled>上一页</button><button class="button" type="button">下一页</button></div>
    <footer><div class="em-selection"><strong>已选 0 份</strong><small>筛选与翻页保留已选文件</small></div><button class="button" type="button" disabled>清空选择</button><button class="button primary" type="button" disabled>获取所选到服务器</button></footer>
  </section>
</dialog>
```

## 现状审计

[A审计E1–E7](audit.md)与[代码检索全集](code-search-evidence.md)为本计划组成部分：包括表schema、全部生产写/读入口及跨模块交互。执行时先按符号复核，不以旧行号代替源码。 本项直接核对：E2/E6/E7（专家两入口/旧DOM/资源注册）。

新增/改动读写关系：本项实现方案逐任务明确生产者和消费者；只允许表中列出的文件引入这些写路径。迁移/源定位/任务字段的完整类型与默认值见总计划持久化契约，禁止再自增冗余状态。

### 前端样式盘点

[改动前逐字DOM/CSS](frontend-baseline.md)保留source路径、行号及SHA256。基准primary #1e40af、bright #3b82f6、background #f5f7fb、text #1e293b/#475569/#94a3b8；按钮32px/12px字/7px圆角；资料字12/11px；原contacts-layout与全部全局class不就地改写。新namespace派生规则只作用本host，无需改变其他使用点；所有原模块事件和原class仍按S-4来源复用。

## 实现方案

1. [I-1/I-2，S-1/S-5] 新expert-materials.js封装API适配、store Map、订阅、分页/选择；不把provider状态存在两个DOM树。API全部经app注入api/contextPath适配，URL保留/talent上下文；以attachmentId为键合并服务器状态。
2. [I-2/I-3，S-1] 活动任务可见时每2秒刷新当前页与summary；隐藏页暂停轮询，下次mount立即重取。使用请求版本号和AbortController只取消读请求；已提交POST服务器任务不取消。提交成功再显示QUEUED，失败保留已选并显示原因；重复提交禁按钮。
3. [I-1/I-4，S-1/S-4] app.js在loadContactDetail完成当前contact DOM写入后mount inline；旧renderExpertDocuments仅在组件存在时输出data host，未加载仍旧渲染。切专家unmount订阅并释放监听；两处专家详情入口按真实contactId决定挂载。原AI入口调用09桥接（尚未加载时继续原函数）；原所有其他详情事件不改。
4. [I-1..I-4，S-1/S-5] expert-materials.css完整复制样式契约S-1；组件drawer使用原生dialog，close/Esc仅释放UI，不清队列；selectionOnly隐藏传输footer但仍同渲染器。精确DOM见本计划样式节。script/CSS注册留11，本阶段代码可部署但未激活。
5. [I-1..I-4，S-1/S-5] Node行为测试用真实DOM能力足够的测试适配（不要用无querySelector的空stub掩盖错误），验证跨host、跨专家、请求竞态、长名、1000条/10页窗口仅10行、500上限；样式测试提取CSS与契约逐字比较，浏览器实拍另由人工完成。

任务中的领域文件路径全部以本计划下表为准；类内DTO/辅助函数不另拆文件。若必须增加文件，先修订计划与≤10文件边界。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/resources/static/expert-materials.js` | 新增 |
| 2 | `src/main/resources/static/expert-materials.css` | 新增 |
| 3 | `src/main/resources/static/app.js` | 修改 |
| 4 | `src/test/js/expertMaterialsShared.test.js` | 新增 |
| 5 | `src/test/js/expertMaterialsStyle.test.js` | 新增 |

## 验收标准

- I-1：inline/drawer mount同导出，无第二fetch队列；切页后同attachmentId状态相同。
- I-2：搜索输入焦点/光标不丢；旧响应丢弃；打开清单只GET，无IMAP/POST。
- I-3：翻两页勾20只POST20；筛选清空仍显示已选20；切B不提交A；服务器任务关闭后继续。
- I-4：组件未注册旧页面仍可用；原始ES专家不请求undefined/null materials。
- S-1/S-4/S-5：CSS逐字、DOM白名单、无新增inline style；保留专家上方操作；四视口截图、焦点/键盘回归。

仅运行后才能记录通过。先本项测试，再mvn test（Java11，含Node），涉迁移追加MigrationIntegrationTest；协议/数据库IT必须按总计划显式启用，不用默认跳过当证据。

## 人工验收清单

### A-1：40份真实布局与两host同步
- 前置条件：测试账号打开依据生产截图建立的专家场景：1旧CV+39 metadata，1000材料压力专家另1位。所有下载只连测试邮箱。
- 操作步骤：1. 专家列表打开资料；2. 勾第一页10件和第二页10件；3. 搜索后清空；4. 获取20件后在组件测试页drawer挂同contact；5. 关闭重开；6. 打开1000件专家。
- 预期结果：40份4页，每页10；已选20跨筛选保留；仅20件进入队列；drawer状态一致；关闭不停止；1000条不一次渲染1000行，文件名可查看全文。
- 覆盖：I-1/I-2/I-3/I-4/S-1/S-5；本项可观察需求与列明的回归/交互。

### A-2：专家原操作与样式
- 前置条件：同一浏览器1440×900、1920×1080、1024×768、390×844；保留未建contact的ES专家fixture。
- 操作步骤：1. 对照baseline查看导航、专家宽度控制、发件、材料类型、别名与阶段历史；2. 用键盘打开/关drawer；3. 查看无contact专家。
- 预期结果：既有区域结构不变；新资料主字12px/辅助11px/圆角10px，无横向溢出；Esc返回触发按钮焦点；无contact显示明确空态且无错误请求。
- 覆盖：I-2/I-4/S-1/S-4/S-5；本项可观察需求与列明的回归/交互。
