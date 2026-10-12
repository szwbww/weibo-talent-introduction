# 05｜会议视频界面与文件命名

状态：待评审；依赖02/03/04均已部署验证。

## 需求描述

联系详情内展示会议视频列表；选择多个文件后分别设置名称、逐个上传，显示真实进度；已上传文件可重命名、播放、下载。每个文件最多1 GiB；原扩展名锁定。

保持：19项档案和共享备注、旧材料、邮件/会议安排、四标签保持；播放链接不随名称变更；未联系专家可使用。范围外：拖拽排序、视频剪辑/转码、删除已上传视频、断点续传、离开页面后后台续传。

## 关键不变量

### Invariant I-1: 列表和名字
- Rule: 绑定当前canonical expertId；GET分页20。每项显示自定义完整名、原始名、大小、上传人/时间；名称主体可改、扩展名只读。前端与04同NFC/code point/非法字符规则，100字符包含点和后缀。不同文件逐个命名，超长默认名必须人工改后才能上传。
- Applies to: 选择、队列、rename、列表
- Violation consequence: 名称规则前后不一致
- 来源: 本次需求与代码审计

### Invariant I-2: 原始文件与进度
- Rule: 每次XHR.send(file)原始File，Content-Type application/octet-stream，不用FormData/FileReader/base64。串行上传；File.size>1073741824在网络前拦截；upload.onprogress可计量才显示loaded/total，100%显示“上传完成，正在保存”，仅201才显示“已上传”。
- Applies to: 文件选择/上传/progress/load
- Violation consequence: 被100MB解析器拦截、假成功
- 来源: 本次需求与代码审计

### Invariant I-3: 状态与重试
- Rule: 队列仅 pending/uploading/processing/success/failed/uncertain/cancelled；pending可改名移除，活动项可取消；success禁止重发/移除（列表没有删除）；失败允许显式重试，不自动重传。abort/network/timeout的提交结果未知则uncertain：“结果未确认，请刷新视频列表”；刷新发现同名提示检查已上传记录，用户确认无记录后从头重传。
- Applies to: XHR全部事件、按钮状态
- Violation consequence: 重复1GiB上传或误报取消回滚
- 来源: 本次需求与代码审计

### Invariant I-4: 编辑与生命周期
- Rule: rename使用version，409保留输入并提示刷新，不自动覆盖。上传捕获原expertId；切专家/退出时abort当前XHR并停止队列、关dialog、移除监听/播放器src并load释放；晚响应不得写当前专家DOM。取消下载请求不声称删除已入库记录。
- Applies to: rename/mount/unmount
- Violation consequence: 串人、残留播放/上传
- 来源: 本次需求与代码审计

### Invariant I-5: 鉴权与播放
- Rule: 使用同源URL和会话，XHR默认同源凭据；401/403转Response供现有handleAuthResponse处理，并停队列；status0不构造非法Response。只从API白名单同源路径设video.src与下载href。HTML视频controls/preload=metadata；解码失败提示“浏览器不支持此视频编码，请下载观看”，下载仍保留。
- Applies to: 上传响应、播放器、download
- Violation consequence: 登录过期静默或不支持编码无出口
- 来源: 本次需求与代码审计

## 样式契约

### S-1：视频区与上传弹窗

复用02全部ec-*样式与原 `.em-upload-*` (`styles.css:11604–11819`)、`.metadata-card`(:1659)、`.button`(:802)、`.modal-close-btn`(:2560)。不修改既有规则；本计划完整新增DOM如下，动态队列/条目按同骨架重复：

```html
<section class="ec-section" data-role="expert-meeting-videos">
 <div class="ec-heading"><h3>会议视频</h3><div class="ec-actions"><button type="button" class="button" data-ec-video-refresh>刷新</button><button type="button" class="button primary" data-ec-video-upload>上传视频</button></div></div>
 <span class="ec-message" role="status" aria-live="polite">单文件最大 1 GiB（1024 MiB）；支持 MP4、MOV、WebM</span>
 <span class="ec-error" role="alert" hidden></span>
 <div class="ec-video-list">
  <article class="metadata-card"><div class="metadata-card-value ec-text" data-role="video-name"></div><span class="ec-message" data-role="video-meta"></span><div class="ec-actions"><button class="button" type="button" data-ec-video-play>播放</button><button class="button" type="button" data-ec-video-rename>重命名</button><a class="button" data-ec-video-download>下载</a></div><video class="ec-video-player" controls preload="metadata" hidden></video><span class="ec-error" role="alert" hidden></span></article>
 </div>
 <div class="ec-video-pager"><button class="button" type="button" data-ec-video-prev>上一页</button><span class="ec-message"></span><button class="button" type="button" data-ec-video-next>下一页</button></div>
</section>
<dialog class="em-upload-dialog ec-editor" aria-label="上传会议视频">
 <header class="em-upload-head"><div class="em-upload-title"><h3>上传会议视频</h3><span class="em-upload-subtitle">可设置文件名；断网后从头重传</span></div><button class="modal-close-btn" type="button" aria-label="关闭">×</button></header>
 <div class="em-upload-body">
  <label class="em-upload-picker"><span>选择视频</span><input type="file" multiple accept=".mp4,.mov,.webm"></label>
  <span class="em-upload-limit">每个文件最大 1 GiB（1024 MiB）</span>
  <div class="em-upload-queue">
   <div class="em-upload-row" data-upload-state="pending"><div class="em-upload-file"><small data-role="original-name"></small><label>文件名<span class="ec-video-name"><input type="text" data-role="name-stem"><span data-role="extension">.mp4</span></span></label></div><button class="button" type="button" data-ec-video-remove>移除</button><progress class="em-upload-progress" max="100" hidden></progress><span class="em-upload-state" role="status" aria-live="polite">待上传</span></div>
  </div>
  <span class="em-upload-error" role="alert" hidden></span>
 </div>
 <footer class="em-upload-actions"><span class="em-upload-summary"></span><button class="button" type="button" data-ec-video-cancel>取消当前上传</button><button class="button primary" type="button" data-ec-video-start>开始上传</button></footer>
</dialog>
```

- 区块在四组档案后、原有邮件/材料区域前；nav增加“会议视频”按钮沿用ec-nav/.button。列表空态ec-message“暂无会议视频”；失败ec-error+刷新按钮；分页只有需要时显示。
- 活动上传期间关闭按钮禁用、Escape阻止关闭并提示先取消当前上传；取消按钮保持可用。队列状态文字使用现有em-upload-state；重试/刷新列表按钮复用.button；未上传移除只影响队列。所有按钮disabled受02规则约束。
- 重命名复用02编辑dialog骨架，把ec-edit-grid的label替换为上面ec-video-name，保留原始文件名ec-message及保存/取消；没有其它class。label用唯一input id/for关联，不能把一行名字当路径。
- 不给原始名做可编辑input；后缀span始终不可编辑。多字节名字不要直接用HTML maxlength=100冒充code point计数，JS前后端按同算法验证。

### S-2：新增CSS逐字契约

```css
.ec-video-player { display: block; width: 100%; max-height: 420px; margin-top: 10px; border-radius: 7px; background: #0f172a; }
.ec-video-name { display: flex; align-items: center; gap: 6px; min-width: 0; }
.ec-video-name input { min-width: 0; flex: 1; }
.ec-video-name span { flex: none; color: #64748b; font-size: 12px; }
.ec-video-list { display: flex; flex-direction: column; gap: 8px; }
.ec-video-pager { display: flex; align-items: center; justify-content: flex-end; gap: 8px; margin-top: 12px; }
@media (max-width: 760px) { .ec-video-player { max-height: 260px; } .ec-video-pager { justify-content: flex-start; flex-wrap: wrap; } }
```

禁止inline style和未列class。视频高度上限桌面420px/小屏260px，宽度100%；上传条进度复用5px高蓝#3b82f6；无虚构封面。

S-2 插入位置（amendment A3，2026-10-12）：逐字插入到 02 的 S-2 块之前，保持 02 的 S-2 块为 `styles.css` 末尾 CSS；不修改任何既有规则。

## 现状审计

- 宿主、样式、现有材料串行上传、会话处理与缓存事实见[审计](contact-profile-audit.md) E-5/E-6/E-8/E-9；现有api()基于fetch不能提供上传进度，故仅新视频上传用XHR；列表与rename仍用api()。
- 文件与DB全部写读所有者为04 service；本页不调用旧materials上传、不生成Blob作为“已上传”结果、不存localStorage视频。
- 交互 X1：XHR raw→04流式上传→刷新列表；X2：rename→播放器/下载；X3：专家切换→abort/晚响应；X4：04 Range/编码失败→播放器；X5：登录过期→原认证遮罩；X6：新界面→原材料/档案/备注/缓存。
- 样式基线复用02与E-6，追加S-2，不新增资源文件、不改11个资源数量。

## 实现方案

1. `app.js` 共用详情挂载时添加S-1视频section及列表独立请求，错误不阻断备注/档案；监听分页、刷新、play/rename/download；I-1/I-4/I-5、S-1/S-2。
2. `app.js` 建立局部队列并持有File引用，声明状态转移：pending→uploading→processing→success；校验失败→failed；活动项abort或断网→uncertain；未开始项取消→cancelled；failed/经用户核对的uncertain→pending；成功只刷新列表不回退pending。每次只一个XHR，任何401/403、容量/并发错误暂停余队列；其它文件失败也暂停供用户决定，避免一串大文件盲传。I-2/I-3、S-1。
3. `app.js` XHR.timeout=0避免拿短API超时裁掉长上传，服务端读超时仍有效；上传进度只展示实际事件，显示“可取消；断网需重新上传”。onload先检查状态/解析JSON再确认；status0/network/error/abort统一区分未开始与未知提交。401/403通过 `new Response(xhr.responseText,{status:xhr.status,headers:{"Content-Type":"application/json"}})`交handleAuthResponse，利用其clone/json约定。I-2/I-3/I-5。
4. `app.js` 销毁时停止队列、abort、清监听/文件引用/视频src；活动upload不能被标记为已取消服务器记录。同一列表一次只加载一个视频播放器，播放另一项先释放上一个；使用服务端previewUrl，不fetch整文件再Blob。I-4/I-5、S-1/S-2。
5. `styles.css` 逐字插入 S-2 到 02 的 S-2 块之前（保持 02 块为文件末尾 CSS，见 S-2 说明）；`index.html`11项缓存键同步`20261011-contact-profile-05`；`expertMeetingVideo.test.js`覆盖全部状态与实际挂载，配合04真实1GiB验收。I-1–I-5、S-1/S-2。

## 变更文件清单

| 文件 | 操作 / 职责 |
|---|---|
| `src/main/resources/static/app.js` | 修改：视频列表、文件名、串行上传/进度、播放/改名/销毁 |
| `src/main/resources/static/styles.css` | 修改：只新增视频契约 CSS（逐字插入于 02 S-2 块之前） |
| `src/main/resources/static/index.html` | 修改：原11个资源同步缓存键 |
| `src/test/js/expertMeetingVideo.test.js` | 新建：真实挂载及XHR状态/异常测试 |

共 **4 个文件**；未列文件禁止顺手修改。测试证据为执行产物，不扩张产品修改白名单。

## 验收标准

- I-1：两宿主19字段/视频都在；原始/自定义名及后缀正确，100/101字符、中文、组合字符、同名409。
- I-2：XHR.send参数严格File，网络Content-Type非multipart；1GiB+1在网络前阻止；50%/100%但未201不显示成功；没有FileReader/readAsDataURL/整文件arrayBuffer。
- I-3：七状态都有可控事件测试；失败/unknown不自动重传，重试前刷新；成功项不重复提交。不能把取消确认与服务端DELETE混淆。
- I-4：rename409保留输入；甲上传中切乙/返回列表，晚响应不改乙，旧video停止；没有重复事件或残留dialog。
- I-5：登录过期401/首次改密403都走原遮罩；异常编码有下载出口；视频通过真实Range请求播放，非先下载整文件。
- S-1/S-2：逐字CSS diff、DOM契约、无inline style，1366/390px目测、键盘关闭/焦点归还。
- `node --test src/test/js/expertMeetingVideo.test.js`；完成后 `node --test src/test/js/*.test.js`；运行04真实HTTP/1GiB验收并记录结果，线上预览本地Blob测试不算后端验收。

## 人工验收清单

### A-1: 批量命名和上传
- 前置条件: 测试环境，未联系专家；准备两个小MP4及一个1GiB验收MP4。
- 操作步骤: 1. 多选文件。2. 分别命名“第一次会议”“第二次会议”“年度会议”。3. 开始上传。4. 观察进度并刷新页面。
- 预期结果: 后缀.mp4不可编辑；同时最多上传1个；显示真实进度，201前不显示已上传；刷新后3条名称和字节数保留，1GiB显示1024MiB或1GiB。
- 覆盖: I-1/I-2；X1

### A-2: 上限与改名
- 前置条件: 已有第一次会议.mp4；另准备1GiB+1字节文件。
- 操作步骤: 1. 选择超限文件。2. 将已有视频重命名“第一次会议-补充”。3. 播放并下载。4. 输入空名、/、与已有名称相同的名。
- 预期结果: 超限在发请求前报最大1GiB；下载名第一次会议-补充.mp4；旧播放链接仍有效；非法名不提交，同名提示文件名已存在且不覆盖。
- 覆盖: I-1/I-2/I-4；X2

### A-3: 断网、取消与切换
- 前置条件: 甲大文件正在上传，乙无视频；浏览器可切离线。
- 操作步骤: 1. 上传中断网。2. 恢复后按提示刷新列表。3. 确认没有已完成记录才点重试。4. 上传中切乙或返回列表。
- 预期结果: 显示结果未确认，请刷新视频列表；不自动重新上传；重试从0开始；乙不出现甲条目，旧播放器/队列停止。已成功写入的记录不被误删。
- 覆盖: I-3/I-4；X3

### A-4: 拖动、编码和登录
- 前置条件: 有兼容MP4及浏览器不支持编码的视频。
- 操作步骤: 1. 兼容视频播放并拖动。2. 播放不兼容文件。3. 在另一页退出登录，再尝试上传。
- 预期结果: 兼容视频按Range拖动；不兼容提示浏览器不支持此视频编码，请下载观看且可下载；过期登录弹原登录遮罩、停止队列。
- 覆盖: I-5；X4/X5

### A-5: 整页与窄屏回归
- 前置条件: 有档案、收发件箱备注和旧材料的联系人；1366px与390px。
- 操作步骤: 1. 编辑译名保存、刷新备注、下载旧材料、切四标签。2. 查看视频和命名弹窗，Tab/Escape。3. 查看Network11项资源缓存键。
- 预期结果: 档案/备注/材料值不变，旧会议安排/邮件入口仍可操作；弹窗白底无横溢出，后缀可见，视频最大420/260px；资源键统一20261011-contact-profile-05。
- 覆盖: I-4；S-1/S-2；X6；全部保持项

## 修正记录

- 2026-10-12（fast-p amendment A3）：S-2 视频 CSS 的落位由「逐字追加」明确为「逐字插入到 02 的 S-2 块之前（保持 02 块为文件末尾 CSS）」。原因：02 静态测试断言其 S-2 块为 styles.css 末尾（`expertManualProfile.test.js` 的 `endsWith`），而 05 不得修改该测试文件；经人工批准 S1 调整计划措辞，零测试文件改动。

