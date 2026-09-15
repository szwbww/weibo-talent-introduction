# 10 · 收发件箱专家聊天布局

状态：待审阅/未执行。前置：09子计划通过独立验证。 范围：10个文件；不超过2子系统。共同契约见[总计划](00-mailbox-materials-master.md)。

## 需求描述

左侧只显示专家，右侧按时间显示来往信件；关注、材料共享入口、折叠工作台和展开人工回复完整可操作。

不得改变：正式导航、筛选、任务钻取、未匹配处理、标签/专家设置、可信生成和人工发送校验、全局检查回复与批量发送入口。

范围外：不新增聊天数据库/即时通讯、不删除旧后台处理API、不新增无来信自由富文本发送旁路。

## 关键不变量

### Invariant I-1：单信操作
- Rule：普通专家聊天移除“查看/处理”入口；待处理来信只保留标记已处理动作，已处理显示只读状态；正文/标签/附件可原位展开，发件只有真实发送态。
- Applies to：聊天render/事件、处理回调
- Violation consequence：旧处理入口复活或发件被标处理
- 来源：original

### Invariant I-2：固定上下文与顺序
- Rule：右侧固定往来→workbench默认折叠→manual默认展开→日志折叠；workbench使用summary.latestInbound.processingId，与筛选后的最后一条可见消息无关；切专家销毁旧mount、丢弃旧响应。
- Applies to：chat host、workbench adapter、manual compose
- Violation consequence：tab切换/错专家回复
- 来源：K-shared-workbench-fixed-mode-host-adapter

### Invariant I-3：发送与草稿
- Rule：人工回复沿用submitManualRichReply和现有服务端校验/QA审计；内存草稿按contactId+targetInboundId+account保存；新来信不静默替换有编辑的目标；发送成功才清草稿和刷新计数。
- Applies to：富文本host与发送回调
- Violation consequence：草稿丢失或发错对象
- 来源：K-ai-adopt-direct-send-no-residual-gates

### Invariant I-4：纯发件专家
- Rule：received=0/SENT>0显示待专家回复与收0·发N；工作台明确无来信不能生成。人工区仍展开，但按钮“选择模板发送跟进邮件”走既有专家发件流程；不得捏造processingId。
- Applies to：sidebar、右侧空来信、跟进操作
- Violation consequence：专家被隐藏、生成无依据回复
- 来源：original

### Invariant I-5：范围与共用
- Rule：普通邮箱激活聊天；taskExecutionId仍旧批次明细；未匹配保持独立入口。材料使用08同组件drawer，同contactId store；关注调用07持久化接口。
- Applies to：loadMailbox、入口路由、所有异步请求
- Violation consequence：破坏任务跳转、两份资料状态
- 来源：original

## 样式契约

所有新DOM必须映射[完整契约S-1至S-5](ui-style-contract.md)，既有DOM/CSS逐字见[baseline](frontend-baseline.md)。禁止新增inline style、未声明class、修改既有全局规则。button复用styles.css:802/838；专家原卡复用:1659；可信内部样式复用:7329起。

### S-3：聊天全量CSS与DOM（原样复制）

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

S-1材料抽屉直接调用08，不复制材料DOM逻辑；S-4复用操作映射及S-5交互要求全部适用。

## 现状审计

[A审计E1–E7](audit.md)与[代码检索全集](code-search-evidence.md)为本计划组成部分：包括表schema、全部生产写/读入口及跨模块交互。执行时先按符号复核，不以旧行号代替源码。 本项直接核对：E5/E6/E7（真实邮箱/共享工作台/旧入口）。

新增/改动读写关系：本项实现方案逐任务明确生产者和消费者；只允许表中列出的文件引入这些写路径。迁移/源定位/任务字段的完整类型与默认值见总计划持久化契约，禁止再自增冗余状态。

### 前端样式盘点

[改动前逐字DOM/CSS](frontend-baseline.md)保留source路径、行号及SHA256。基准primary #1e40af、bright #3b82f6、background #f5f7fb、text #1e293b/#475569/#94a3b8；按钮32px/12px字/7px圆角；资料字12/11px；原contacts-layout与全部全局class不就地改写。新namespace派生规则只作用本host，无需改变其他使用点；所有原模块事件和原class仍按S-4来源复用。

## 实现方案

1. [I-1/I-4/I-5，S-3/S-4/S-5] 新mailbox-chat.js以IIFE导出mount/unmount，app.loadMailbox在新组件存在且无taskExecutionId时调用；原table/group代码保留给任务钻取与脚本未加载兼容。只在聊天激活时隐藏旧MAIL/EXPERT模式控件与旧外部分页；现有筛选读取器传到新summary API，左侧分页20位，不再客户端按当前页分组。
2. [I-1/I-2，S-3/S-4] 消息render使用(source,id)键和服务器DTO，安全文本/既有HTML清洗函数。入站footer只标记已处理按钮，通过现有pending/unmatched已处理API和原操作日志写入；点击成功局部刷新当前消息、专家pending数、全局未处理角标，不刷新编辑器。附件默认折叠显示数量，展开前三名称+“查看全部附件”打开同组件并按本信source筛选。
3. [I-2/I-3，S-3/S-4] 从原详情流程提取host适配入口（只在app.js内），mountLiveTrustReply继续固定LIVE_INBOUND；不改trust-reply-workbench.js内部实现。默认折叠不发生成请求；展开不自动生成；最新来信标识、时间、账号明确。已有编辑草稿时检测新来信，显示提示，由用户选择切换目标，不能静默重写主题/正文/QA信息。
4. [I-3，S-3/S-4] 人工区默认open，复用subject预填、富文本B/I/list/link、采用与最终发送函数；按钮提交中禁用，失败保留全部输入。工作台生成→编辑/事实修改→采用→人工发送走同一原路径；现有安全校验失败按原错误显示，不增加历史readiness审批门。
5. [I-4，S-3/S-4] 已确认生产ManualMailOptionType只有COMPOSE_TEMPLATE，command没有subject/body字段。因此没有来信时不展示一个无法发送的假富文本编辑器：显示说明+“选择模板发送跟进邮件”，打开原专家账号/模板/发件操作。预览曾模拟自由跟进属于未实现能力，本计划明确不冒充已有；如用户后续要求无来信自由富文本，应另行审计发送服务后追加独立计划。
6. [I-5，S-1/S-3/S-5] header材料按钮调用ExpertMaterials.mount drawer；关注按钮使用PUT/DELETE，先禁重复，乐观失败回滚。所有taskExecution/未匹配入口保持既有参数及跳转；不把未匹配记录伪装专家。新增CSS原样复制S-3；index注册留11。
7. [I-1..I-5，S-1/S-3/S-4/S-5] 新行为/样式测试覆盖上述链路；调整既有group/date/task/已处理/富文本测试只针对新激活分支的变化，旧兼容分支断言仍保留，不为了通过测试删发送校验。

任务中的领域文件路径全部以本计划下表为准；类内DTO/辅助函数不另拆文件。若必须增加文件，先修订计划与≤10文件边界。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-chat.js` | 新增 |
| 2 | `src/main/resources/static/mailbox-chat.css` | 新增 |
| 3 | `src/main/resources/static/app.js` | 修改 |
| 4 | `src/test/js/mailboxChatBehavior.test.js` | 新增 |
| 5 | `src/test/js/mailboxChatStyle.test.js` | 新增 |
| 6 | `src/test/js/mailboxExpertGrouping.test.js` | 修改 |
| 7 | `src/test/js/mailboxDateDefault.test.js` | 修改 |
| 8 | `src/test/js/taskDrilldown.test.js` | 修改 |
| 9 | `src/test/js/unmatchedDetailResolvedAction.test.js` | 修改 |
| 10 | `src/test/js/unmatchedQaReplySource.test.js` | 修改 |

## 验收标准

- I-1：普通聊天无查看/处理按钮；已处理点击一次后状态/计数/角标同步；原文/标签仍可原位操作。
- I-2：DOM顺序及open属性精确；latestInbound绑定真实processingId；切专家的旧异步结果无效。
- I-3：草稿跨专家恢复、切目标提示、失败不清空；可信采用到人工发送payload保持原QA审计。
- I-4：纯发件仍在全部/关注/待回复，failed-only不混入；无来信生成0调用；跟进走COMPOSE_TEMPLATE原端点。
- I-5：两个host同材料store；任务批次仍旧API；未匹配可绑定处理；关注刷新保留。
- S-1/S-3/S-4/S-5：CSS逐字、DOM层次、所有按钮/禁用/焦点；四视口实拍且正式上方操作不遗漏。

仅运行后才能记录通过。先本项测试，再mvn test（Java11，含Node），涉迁移追加MigrationIntegrationTest；协议/数据库IT必须按总计划显式启用，不用默认跳过当证据。

## 人工验收清单

### A-1：聊天与工作台完整链路
- 前置条件：测试专家A有2来信2发件，最新来信待处理且带20附件；专家B仅2成功发件，C仅失败发件。
- 操作步骤：1. 打开邮箱选A；2. 展开工作台生成、修改事实、采用；3. 编辑主题/正文后切B再回A；4. 标记一封已处理；5. 用测试SMTP发送人工回复。
- 预期结果：左侧专家右侧时间线；工作台初始折叠/人工初始展开；草稿恢复；标处理只改变处理态/角标且不下载附件；发送走原校验和QA记录，成功才清草稿。
- 覆盖：I-1/I-2/I-3/S-3/S-4/S-5；本项可观察需求与列明的回归/交互。

### A-2：纯发件、关注、材料与原入口
- 前置条件：B/C同上，A有40份材料；测试任务记录与未匹配信各1。
- 操作步骤：1. 切待专家回复并关注B、刷新；2. 打开B工作台/人工区；3. 从A材料抽屉获取2件后切专家页查看；4. 从任务和未匹配入口操作。
- 预期结果：B收0发2/待专家回复，C不在待回复；B无生成按钮且可选择模板跟进；关注保留；两入口同2件状态；任务明细、未匹配绑定原功能仍可用。
- 覆盖：I-4/I-5/S-1/S-3/S-4；本项可观察需求与列明的回归/交互。

### A-3：四视口与新来信竞态
- 前置条件：1440×900/1920×1080/1024×768/390×844，测试接口延迟A响应，另投递新来信。
- 操作步骤：1. 快速切A→B；2. 在B编辑草稿后刷新到新来信；3. 键盘遍历所有动作；4. 对照生产截图。
- 预期结果：无A内容覆盖B；新来信提示选择目标而不清草稿；主色#1e40af，专家栏320/280px断点符合CSS；窄屏保留全部专家列表；导航、状态/层级/标签/可信动作齐全且无tab。
- 覆盖：I-2/I-3/I-5/S-3/S-4/S-5；本项可观察需求与列明的回归/交互。
