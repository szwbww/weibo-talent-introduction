# 完整前端样式契约

状态：待实施。以下为最终落地文本，不是示意 CSS。禁止自行调色、字号、间距；新增 DOM 如超出契约须先修订计划。禁止新增 inline style、未声明 class、全局覆盖 `.button`/`.document-row`/`[hidden]`。继承的现有子组件不要求本期清除全部历史 inline style，但新 host 不得照搬。

## 复用与布局边界

复用 styles.css:802 的 `.button`、:838 的 `.button.primary`；专家页 `.contacts-layout`（:906）、`.metadata-card`（:1659）及已有 AI 结果编辑器、可信工作台内部 DOM/CSS 不改。原文全文见 [frontend-baseline.md](frontend-baseline.md)。所有本期尺寸规则只作用于新 namespace，因而不改变既有 class 任何使用点。

页面导航、筛选/发现专家/回刷 ES、发件账号/模板/发送邮件/更多、材料类型菜单、邮箱别名、阶段历史保留原结构与事件。专家页只替换“专家上传资料”内部清单。生产部署前后同一窗口截图对比这些区域，应无布局变化。

桌面验收 1440×900、1920×1080；窄屏 1024×768、390×844，缩放100%。颜色基准与生产 source 对齐；资料主体12px，辅助11px，不复制预览9px小字。附件很多时分页，不缩字体或无限拉长页面。

## S-1：共享资料组件、专家内嵌、邮箱抽屉

- 目标文件 `src/main/resources/static/expert-materials.css`。逐字复制下列完整块。
- inline 与 drawer 均调用同一 render；inline 不含 dialog，无关闭按钮，header 右侧放既有“AI 智能分析”。drawer 使用原生 showModal()/close()，Esc 关闭且返回触发按钮焦点。关闭不取消服务器下载。
- 搜索、来源与状态筛选固定顺序；10条/页。表头勾选文案/aria 为“选择本页”，不能暗示选择整个结果集。跨页选择与当前筛选分开计数。
- 文件名一行省略，但 title 和可访问名称保留全文；类型/审核/大小、主题/时间两行辅助信息。无大小显示“大小未知”；有远端大小“邮箱估算 X”；已落地“X（实际）”。
- 状态文案唯一：METADATA_ONLY=仅文件信息；QUEUED=排队中；DOWNLOADING=获取中；STORED=已存服务器；FAILED=获取失败；SOURCE_UNAVAILABLE=来源不可用。未知总长 progress 不设置 value；不编造百分比。
- STORED 行按钮“下载到电脑”，仅支持类型显示“预览”；未落地仅“获取到服务器”；FAILED“重试”；SOURCE_UNAVAILABLE 无重试盲拉按钮，说明具体原因。选0时主按钮 disabled；请求提交中显示“正在提交…”并禁重复。
- 骨架中样例数据只说明格式，生产全部由 DTO 安全渲染。动态元素仅使用下列声明 class 或原有 `.button`。

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

加载态使用 em-empty 文案“正在加载资料…”并 aria-busy=true；空态“暂无资料文件。”；筛选空态“没有符合条件的资料。”；错误 em-error+重试。header/filters/pager 不因轮询重绘，焦点和选择位置不丢失。

## S-2：AI 选件与等待

使用 S-1 同一组件的 selectionOnly 模式。AI 新增类 CSS 已完整包含在上块 em-analysis 规则，不创建第二份选件样式。既有 #aiAnalysisModal 外壳、分析结果字段编辑/新增/重新评估保持原事件。已有资料直接“开始分析”；缺少所选资料“获取所选文件并分析”；传输中“正在获取所选文件（1/2）”；失败“1 份获取失败”，提供重试失败项，不显示分析完成。图片行 checkbox disabled、原因“当前不支持图片文字识别”。

```html
<!-- S-2: 嵌入既有 #aiAnalysisModal 的选件内容区，沿用原弹窗标题/关闭/结果编辑结构 -->
<section class="em-analysis">
  <p class="em-analysis-note">仅分析所选文件。未获取的文件将在确认后下载到服务器；图片暂不支持文字识别。</p>
  <div data-material-picker><!-- S-1 相同 expert-materials，selectionOnly 模式，不渲染下载 footer/关闭按钮 --></div>
  <div class="em-analysis-actions"><span role="status">已选 2 份，已存 1 份，需获取 1 份</span><button class="button primary" type="button">获取所选文件并分析</button></div>
</section>
```

## S-3：专家聊天布局及操作区

目标 `src/main/resources/static/mailbox-chat.css`，完整原样复制。默认320px专家栏、16px间隔；1100px以下280px；760px以下上下布局，不能像 mock 那样隐藏未选专家。右侧一条滚动区，按“设置→来往信件→可信工作台→人工回复→日志”排序。工作台默认折叠、人工回复默认展开；不新增 tab。

```css
.mail-chat{display:grid;grid-template-columns:320px minmax(0,1fr);gap:16px;min-height:620px;height:calc(100dvh - 220px);color:#475569;font-size:12px;line-height:1.6}
.mail-chat *{box-sizing:border-box}
.mail-chat [hidden]{display:none!important}
.mail-chat :is(h2,h3,p){margin:0}
.mail-chat .mc-experts,.mail-chat .mc-conversation{display:flex;flex-direction:column;min-width:0;min-height:0;border:1px solid rgba(15,23,42,.11);border-radius:18px;background:rgba(255,255,255,.55);overflow:hidden}
.mail-chat .mc-list-tools{display:flex;flex-direction:column;gap:10px;padding:14px;border-bottom:1px solid #e2e8f0}
.mail-chat .mc-list-tools input{width:100%;height:32px;min-height:32px;margin:0;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#475569;font:inherit}
.mail-chat .mc-list-tools input::placeholder{color:#94a3b8}
.mail-chat .mc-filters{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-filter{min-height:28px;padding:3px 8px;border:1px solid #dce4ef;border-radius:7px;background:#f8faff;color:#64748b;font:inherit;cursor:pointer}
.mail-chat .mc-filter:hover{border-color:#93b4ec;background:#eff5ff}
.mail-chat .mc-filter:active{background:#dbeafe}
.mail-chat .mc-filter[aria-pressed=true]{border-color:#1e40af;background:#eff5ff;color:#1e40af;font-weight:600}
.mail-chat .mc-filter:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-expert-list{flex:1;min-height:0;overflow:auto;padding:8px;overscroll-behavior:contain}
.mail-chat .mc-person{position:relative;display:grid;grid-template-columns:minmax(0,1fr) 28px;gap:8px;margin-bottom:6px;border:1px solid transparent;border-left:3px solid transparent;border-radius:10px;background:transparent}
.mail-chat .mc-person:hover{background:#eff5ff}
.mail-chat .mc-person[data-active=true]{border-color:#4564df;border-left-color:#e11d48;background:#e8edfb}
.mail-chat .mc-person-main{display:flex;flex-direction:column;align-items:stretch;min-width:0;gap:5px;padding:12px 0 12px 10px;border:0;background:transparent;color:#475569;text-align:left;font:inherit;cursor:pointer}
.mail-chat .mc-person-main:active{opacity:.85}
.mail-chat .mc-person-main strong{font-size:13px;font-weight:600;color:#1e293b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.mail-chat .mc-person-main small{font-size:11px;color:#64748b;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.mail-chat .mc-person-meta{display:flex;align-items:center;flex-wrap:wrap;gap:6px;font-size:11px}
.mail-chat .mc-follow{align-self:start;margin:10px 4px 0 0;padding:0;width:24px;height:28px;border:0;border-radius:7px;background:transparent;color:#94a3b8;font-size:20px;line-height:1;cursor:pointer}
.mail-chat .mc-follow:hover{background:#fef3c7;color:#b45309}
.mail-chat .mc-follow:active{background:#fde68a}
.mail-chat .mc-follow[aria-pressed=true]{color:#d97706}
.mail-chat .mc-follow:disabled{opacity:.45;cursor:not-allowed}
.mail-chat .mc-badge{display:inline-flex;align-items:center;padding:1px 7px;border:1px solid #dce4ef;border-radius:12px;background:#f1f5f9;color:#64748b;font-size:11px;white-space:nowrap}
.mail-chat .mc-badge[data-tone=pending]{background:#fff7ed;border-color:#fed7aa;color:#b45309}
.mail-chat .mc-badge[data-tone=waiting]{background:#eff5ff;border-color:#bfdbfe;color:#1e40af}
.mail-chat .mc-badge[data-tone=success]{background:#ecfdf5;border-color:#a7f3d0;color:#059669}
.mail-chat .mc-badge[data-tone=error]{background:#fff1f2;border-color:#fecdd3;color:#e11d48}
.mail-chat .mc-pager{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:6px;padding:10px 12px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
.mail-chat .mc-header{display:flex;align-items:flex-start;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:16px 18px;border-bottom:1px solid #e2e8f0}
.mail-chat .mc-identity{flex:1;min-width:180px}
.mail-chat .mc-identity h2{font-size:16px;font-weight:600;color:#1e293b;overflow-wrap:anywhere}
.mail-chat .mc-identity p{margin-top:4px;color:#64748b;font-size:11px;overflow-wrap:anywhere}
.mail-chat .mc-actions{display:flex;align-items:center;flex-wrap:wrap;gap:8px}
.mail-chat .mc-scroll{flex:1;min-height:0;overflow:auto;padding:16px 18px;overscroll-behavior:contain;scrollbar-gutter:stable}
.mail-chat .mc-expert-settings{padding:10px 12px;margin-bottom:14px;border:1px solid #dce4ef;border-radius:10px;background:#f8faff}
.mail-chat .mc-expert-settings>summary{cursor:pointer;color:#475569;font-weight:600}
.mail-chat .mc-settings-content{display:grid;gap:12px;margin-top:12px}
.mail-chat .mc-timeline{display:flex;flex-direction:column;gap:14px;margin-bottom:16px}
.mail-chat .mc-load-older{align-self:center}
.mail-chat .mc-day{align-self:center;color:#94a3b8;font-size:11px;padding:2px 8px}
.mail-chat .mc-message{align-self:flex-start;width:min(88%,820px);min-width:0;padding:12px 14px;border:1px solid #dce4ef;border-radius:10px;background:#fff}
.mail-chat .mc-message[data-direction=OUTBOUND]{align-self:flex-end;background:#eff5ff;border-color:#cbdcf7}
.mail-chat .mc-message header{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;margin-bottom:8px;color:#64748b;font-size:11px}
.mail-chat .mc-message h3{font-size:13px;color:#334155;font-weight:600;margin-bottom:8px;overflow-wrap:anywhere}
.mail-chat .mc-body{white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;line-height:1.8;color:#334155}
.mail-chat .mc-message footer{display:flex;justify-content:flex-end;align-items:center;gap:8px;margin-top:10px;color:#64748b;font-size:11px}
.mail-chat .mc-mail-extras{margin-top:10px;padding-top:8px;border-top:1px solid #e2e8f0;color:#64748b;font-size:11px}
.mail-chat .mc-mail-extras summary{cursor:pointer}
.mail-chat .mc-attachment-names{padding-top:6px;overflow-wrap:anywhere;line-height:1.8}
.mail-chat .mc-attachment-names>div{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
.mail-chat .mc-section{margin-top:14px;border:1px solid #dce4ef;border-radius:10px;background:#f8faff;overflow:hidden}
.mail-chat .mc-section>summary{padding:12px 14px;cursor:pointer;color:#334155;font-size:13px;font-weight:600}
.mail-chat .mc-section[open]>summary{border-bottom:1px solid #e2e8f0}
.mail-chat .mc-section-content{padding:14px;min-width:0}
.mail-chat .mc-note{padding:12px;border:1px solid #dbe7fa;border-radius:7px;background:#eff5ff;color:#64748b;font-size:12px;line-height:1.7}
.mail-chat .mc-empty{padding:40px 20px;color:#64748b;text-align:center}
.mail-chat .mc-error{padding:12px;border:1px solid #fecdd3;border-radius:7px;background:#fff1f2;color:#be123c}
.mail-chat .mc-compose{display:flex;flex-direction:column;gap:10px;min-width:0}
.mail-chat .mc-compose label{display:flex;flex-direction:column;gap:6px;color:#64748b;font-size:12px}
.mail-chat .mc-compose input{width:100%;height:32px;min-height:32px;padding:0 10px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font:inherit}
.mail-chat .mc-editor-tools{display:flex;flex-wrap:wrap;gap:6px}
.mail-chat .mc-editor{min-height:160px;max-height:360px;overflow:auto;padding:12px;border:1px solid #dce4ef;border-radius:7px;background:#fff;color:#334155;font-size:12px;line-height:1.8;overflow-wrap:anywhere}
.mail-chat .mc-compose-footer{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:10px}
.mail-chat .button{white-space:nowrap}
.mail-chat .button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.mail-chat :is(button,a,input,select,summary,[contenteditable=true]):focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
@media(max-width:1100px){.mail-chat{grid-template-columns:280px minmax(0,1fr);gap:12px}.mail-chat .mc-header{padding:14px}.mail-chat .mc-scroll{padding:14px}.mail-chat .mc-message{width:94%}}
@media(max-width:760px){.mail-chat{display:flex;flex-direction:column;height:auto;min-height:0;gap:12px}.mail-chat .mc-experts{max-height:320px;min-height:240px}.mail-chat .mc-expert-list{min-height:100px}.mail-chat .mc-conversation{min-height:560px}.mail-chat .mc-scroll{max-height:none;overflow:visible;padding:12px}.mail-chat .mc-message{width:100%}.mail-chat .mc-header{padding:12px}.mail-chat .mc-identity{min-width:0;width:100%;flex-basis:100%}.mail-chat .mc-section-content{padding:12px}.mail-chat .mc-actions{width:100%}}
@media(prefers-reduced-motion:reduce){.mail-chat .button{transition:none}.mail-chat .button:hover,.mail-chat .button:active{transform:none}}
```

```html
<!-- S-3: mount 于既有 #mailboxList；保留页面上方正式导航与操作区，不新增页面 tab -->
<div class="mail-chat">
  <aside class="mc-experts" aria-label="专家会话列表">
    <div class="mc-list-tools"><input type="search" aria-label="搜索专家" placeholder="搜索专家姓名、邮箱"><div class="mc-filters"><button class="mc-filter" aria-pressed="true">全部</button><button class="mc-filter" aria-pressed="false">关注</button><button class="mc-filter" aria-pressed="false">待处理</button><button class="mc-filter" aria-pressed="false">待专家回复</button></div></div>
    <div class="mc-expert-list"><div class="mc-person" data-active="true"><button class="mc-person-main" type="button" aria-current="true"><strong>专家姓名</strong><small>机构 · 账号</small><small>最近发件：项目介绍邮件</small><span class="mc-person-meta"><span>收 0 · 发 2</span><span class="mc-badge" data-tone="waiting">待专家回复</span></span></button><button class="mc-follow" type="button" aria-label="关注该专家" aria-pressed="false">☆</button></div></div>
    <div class="mc-pager"><span>第 1/4 页 · 共 80 位</span><button class="button" disabled>上一页</button><button class="button">下一页</button></div>
  </aside>
  <section class="mc-conversation" aria-label="专家往来信件">
    <header class="mc-header"><div class="mc-identity"><h2>专家姓名</h2><p>邮箱 · ORCID · 所属账号</p></div><div class="mc-actions"><button class="button">关注</button><button class="button">材料 40</button><button class="button">查看专家详情</button></div></header>
    <div class="mc-scroll">
      <details class="mc-expert-settings"><summary>专家状态、层级与标签</summary><div class="mc-settings-content"><!-- 复用现有专家状态/层级 select、保存变更、专家标签及邮件标签渲染器；不得复制业务事件 --> </div></details>
      <div class="mc-timeline"><button class="button mc-load-older">加载更早信件</button><div class="mc-day">2026-09-07</div>
        <article class="mc-message" data-direction="INBOUND"><header><span>专家来信 · 14:42 · LuKai</span><span class="mc-badge" data-tone="pending">待处理</span></header><h3>来信主题</h3><div class="mc-body">清洗后正文（文本安全渲染）</div><details class="mc-mail-extras"><summary>附件 20 份 · 仅文件信息</summary><div class="mc-attachment-names"><div title="完整文件名.pdf">完整文件名.pdf</div><button class="button">查看全部附件</button></div></details><details class="mc-mail-extras"><summary>原文、翻译、邮件标签与技术信息</summary><div><!-- 复用现有正文/翻译/邮件标签/Message-ID 模块，绑定本 processing.id --></div></details><footer><button class="button" data-action="mark-resolved">标记已处理</button></footer></article>
      </div>
      <details class="mc-section" data-section="workbench"><summary>可信回复工作台 · 基于最新来信</summary><div class="mc-section-content"><div data-trust-host><!-- 现有 TrustReplyWorkbench.mount 固定 LIVE 模式 --></div></div></details>
      <details class="mc-section" data-section="manual" open><summary>人工回复</summary><div class="mc-section-content"><div class="mc-compose"><label>主题<input aria-label="回复主题"></label><div class="mc-editor-tools"><button class="button">B</button><button class="button">I</button><button class="button">列表</button><button class="button">链接</button></div><div class="mc-editor" contenteditable="true" role="textbox" aria-multiline="true" aria-label="人工回复正文"></div><div class="mc-compose-footer"><span>回复账号与目标来信信息</span><button class="button primary">发送人工回复</button></div></div></div></details>
      <details class="mc-section" data-section="logs"><summary>操作日志</summary><div class="mc-section-content"><!-- 既有操作日志内容 --></div></details>
    </div>
  </section>
</div>
```

## S-4：既有动作和状态映射（不得遗漏）

| 区域 | 必须呈现 / 行为 | DOM/CSS来源 |
|---|---|---|
| 全局 | 检查回复、批量发送、自动回复控制、完整原筛选、待匹配来信入口 | index.html:712 起原控件，正式 class；只调整可见性不改样式 |
| 专家头部 | 姓名/邮箱/ORCID/账号，关注、材料数量、查看专家详情 | S-3 mc-header/mc-actions |
| 专家设置 | 状态select、层级select、保存变更、专家标签增删 | 原渲染器放入 mc-settings-content；保留原 field/select/button class |
| 单信 | 原文/清洗/翻译、邮件标签、自动 QA 标签、技术信息原位展开；入站仅处理动作“标记已处理” | mc-mail-extras包裹原子模块，原标签样式复用，不增加查看/处理按钮 |
| 发件 | SENT“已发送”，FAILED“发送失败”；失败摘要原位展开 | mc-badge对应tone；不伪装成待处理来信 |
| 工作台 | 四项回复框架、模型、重新生成、编辑/复制、事实增删、未识别提问、采用到人工回复 | trust-reply-workbench.js 全部原内部 DOM；仅 host 移位 |
| 人工回复 | 主题、B/I/列表/链接、富文本、发送、当前账号/来信 | S-3 mc-compose；复用既有编辑/发送函数、校验与错误文案 |
| 无来信 | 时间线保留全部发件；工作台内“暂无专家来信，暂不能生成回复”；人工区“写跟进邮件”调用原专家发件流程 | mc-note + button primary，不能伪造富文本回复记录 |
| 任务进度 | “当前账号：LuKai · 读取邮件信息；已完成1/2个账号”；下载状态在材料区显示 | 原任务弹窗 class，不重写全局模态框 |

原模块动态DOM class白名单以 baseline 原文及对应 source render 函数为准；允许原样复用，不允许新仿写。契约未定义的装饰、图标库、动效、额外抽屉/状态字段不得自行添加。

## S-5：请求、输入与视觉回归

- 搜索300ms防抖、旧请求取消/版本号丢弃；不逐键 innerHTML 重建整个面板。
- 选择专家期间显示局部加载，上一专家异步响应不得覆盖；工作台只在当前上下文按需挂载一次。
- 关注为独立按钮不触发选中；乐观更新失败恢复星标与计数；重复点击提交中禁用。
- 搜索结果无专家显示 mc-empty“没有符合条件的专家”；无选择显示“请选择左侧专家查看往来信件”；接口失败 mc-error“加载失败，请重试”。
- 每条S验收：提取落地CSS与此块字节比对；新增DOM class查白名单、inline style=0；四种窗口实拍；键盘Tab/Space/Enter/Esc；抽屉关闭焦点返回；长文件名、1000附件、错误状态不溢出。
