# 02｜联系详情人工档案界面

状态：待评审；依赖01部署并验证。

## 需求描述

在截图所示专家列表→联系详情内展示19项新增业务字段，并分为身份基本信息、教育任职、产业技术、科研与企业匹配四组编辑。姓名、国籍、关键词使用已有响应，只读展示；缺值显示“未填写”。

保持：左右分栏、四个原标签、默认激活标签、既有邮件/会议/材料操作及列表筛选不变；不改变原字段权威来源。范围外：新导航页、原页面重构、名单新筛选、Excel导入、视频和备注写入。

## 关键不变量

### Invariant I-1: 双宿主同契约
- Rule: showExpertDetail 与 loadContactDetail 均调用同一个 render/mount；不能只改有contact一条路径。复用01全部字段 I-5–I-23，不新增第二份资料来源。
- Applies to: 两处 contact panel 渲染与挂载
- Violation consequence: 截图位置没有功能
- 来源: K-expert-detail-two-panel-render-sites

### Invariant I-2: 请求与节点生命周期
- Rule: 每次挂载捕获 expertId、contactId、generation 和宿主引用；GET/PATCH每次await后检查 generation、宿主连接性与key。切专家/返回列表时清理监听、关闭弹窗、中止读请求；已发出的保存不能因abort被声称回滚，下次GET确认。
- Applies to: mount/load/save/unmount
- Violation consequence: 慢响应把甲资料显示到乙
- 来源: 本次需求与代码审计

### Invariant I-3: 保存与错误
- Rule: 表单按组提交全部白名单字段及expectedVersion；只收到200后更新显示。400保留输入，409提示“资料已被更新，请重新加载后再编辑”，不自动覆盖重试；GET失败显示重试且禁编辑，不将失败当空档案。取消或Escape不发PATCH。
- Applies to: 四组表单与重试
- Violation consequence: 覆盖资料或虚假保存
- 来源: 本次需求与代码审计

### Invariant I-4: 既有字段与凭证
- Rule: 姓名用现有displayName/联系人expertName；国籍只用nationality，缺失不拿country代替；keyword只读；country/当前机构/阶段仍保留原区域。documentNumber仅编辑弹窗显明文、详情遮罩。用户文本用textContent或escapeHtml，禁止原样innerHTML。
- Applies to: 所有读显示
- Violation consequence: 语义替换、证件暴露、XSS
- 来源: 本次需求与代码审计

## 样式契约

### S-1：宿主与复用组件

- 复用 `styles.css:1652` metadata-grid、:1659 metadata-card、:1673 metadata-card-header、:1690 metadata-card-value、:1710 span-all；按钮 :802–873；输入 :1129–1166；弹窗 `.em-upload-*` :11604–11819、关闭按钮 `.modal-close-btn` :2560。逐字基线见审计 E-6。所有既有规则只复用、不修改，因此不会波及其它使用点。
- 两个 `data-panel="contact"` 内最前面各插入 `<div class="ec-profile" data-role="expert-manual-profile"></div>`；保留其后的所有原有节点/事件。挂载动态内容如下（占位文本由安全DOM赋值，重复组按同结构）：

```html
<nav class="ec-nav" aria-label="联系详情分组"><button type="button" class="button" data-ec-jump="matching">科研与企业匹配</button></nav>
<section class="ec-section" data-ec-group="matching">
  <div class="ec-heading"><h3>科研与企业匹配</h3><button type="button" class="button" data-ec-edit="matching">编辑</button></div>
  <div class="metadata-grid">
    <div class="metadata-card"><div class="metadata-card-header">科研能力</div><div class="metadata-card-value ec-text" data-ec-value="researchAbility">未评估</div></div>
  </div>
  <span class="ec-message" role="status" aria-live="polite"></span>
  <span class="ec-error" role="alert" hidden></span>
</section>
<dialog class="em-upload-dialog ec-editor" aria-label="编辑身份基本信息">
  <form>
    <header class="em-upload-head"><div class="em-upload-title"><h3>身份基本信息</h3><span class="em-upload-subtitle">保存后同步到当前专家</span></div><button class="modal-close-btn" type="button" aria-label="关闭">×</button></header>
    <div class="em-upload-body"><div class="ec-edit-grid"><label>中文译名<input name="chineseName"></label></div><span class="ec-error" role="alert" hidden></span></div>
    <footer class="em-upload-actions"><span class="em-upload-summary" role="status"></span><button class="button" type="button" data-ec-cancel>取消</button><button class="button primary" type="submit">保存</button></footer>
  </form>
</dialog>
```

- nav的四个按钮按 matching/identity/education/technology 跳转对应组，不增顶层标签；组顺序同此顺序。姓名/国籍/keyword在identity使用同一卡片只读；长文本卡片加span-all，输入label加ec-span。多企业textarea每行一家；六档科研select加一个“未评估”（null）选项，进度三选一。可变子元素仅 h3/span/button/label/input/textarea/select/option/div，全部受上述复用或 S-2 约束。
- 控件标签必须可访问关联；弹窗showModal、焦点归还触发按钮、Escape取消；保存期间禁保存与关闭，响应后恢复。失败重试是同 `.button`，无新图标或未声明class。

### S-2：唯一新增 CSS

以下为最终逐字规则，原样追加；禁止inline style、未声明class以及修改既有class。

```css
.ec-profile { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
.ec-section { border: 1px solid var(--panel-border); border-radius: 10px; padding: 14px; background: #fff; scroll-margin-top: 12px; }
.ec-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 12px; }
.ec-heading h3 { margin: 0; color: #1e293b; font-size: 14px; font-weight: 600; line-height: 1.5; }
.ec-actions, .ec-nav { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.ec-text { white-space: pre-wrap; overflow-wrap: anywhere; }
.ec-message { display: block; color: #64748b; font-size: 12px; line-height: 1.5; }
.ec-error { display: block; color: #be123c; font-size: 12px; line-height: 1.5; }
.ec-edit-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.ec-span { grid-column: 1 / -1; }
.ec-editor > form { display: flex; flex-direction: column; min-height: 0; max-height: calc(100dvh - 32px); }
.ec-editor label { text-transform: none; letter-spacing: 0; color: #475569; }
.ec-editor textarea { min-height: 88px; }
.ec-editor :is(input, select, textarea) { background: #fff; }
.ec-profile :is(button, a):focus-visible, .ec-editor :is(button, input, select, textarea):focus-visible { outline: 2px solid #3b82f6; outline-offset: 2px; }
.ec-profile button:disabled, .ec-editor :is(button, input, select, textarea):disabled { opacity: .45; cursor: not-allowed; transform: none; box-shadow: none; }
@media (max-width: 760px) { .ec-edit-grid { grid-template-columns: 1fr; } .ec-heading { align-items: flex-start; flex-wrap: wrap; } }
```

## 现状审计

- 两宿主、generation、API与缓存契约见[审计](contact-profile-audit.md) E-1/E-5/E-6。完整旧DOM/CSS在证据 `app-baseline.txt` / `styles-baseline.txt`。
- 当前 `showExpertDetail` 默认academic；`loadContactDetail` 默认contact；保留，不用预览的固定选中逻辑替换。未联系实例确实没有contactId。
- 新存储只由01 PATCH写；本页GET读。原姓名/国籍/关键词由 ExpertIndexController 的现有响应提供，本计划不写 ES，也不改其所有写路径。
- 交互 X1：01写→两宿主读；X2：慢请求→generation/页面切换；X3：新增分组→旧材料挂载、邮件按钮与四标签；X4：缓存键→实际浏览器使用新脚本。
- 样式盘点：metadata-grid最小列宽160px/gap8px；卡片padding10px12px、radius7px；header11px/600、value13px/500/1.4；primary#1e40af、hover#1e3a8a、正文#1e293b；panel-bg为rgba白.55，故新增section/dialog用实白。按钮完整hover/active与表单disabled见证据。来源 K-panel-bg-token-is-translucent。

## 实现方案

1. `app.js` 两模板加入S-1宿主及共用渲染/挂载/销毁函数（I-1/I-2/S-1）。不修改原材料owner或请求URL。将销毁调用接入已有 beginContactDetailPresentation / returnContactListMobile 生命周期；旧页面保留直到新页面替换时也必须使旧请求无效。01接口URL通过contextPath构造，所有expertId URL编码。
2. `app.js` 根据01明确字段元数据渲染四组；birthDateText使用文本输入提示YYYY或YYYY-MM-DD；不使用强制完整日期的date输入；其它上限由01契约驱动。复用api()；不将异步编辑结果并入state.contacts的ES数据对象（I-3/I-4/S-1/S-2）。
3. `styles.css` 逐字追加S-2；`index.html` 原11个版本化资源统一更新为 `20261011-contact-profile-05`，不增加script资产。保留已知11项资源顺序与数量（I-1/S-1；来源 K-frontend-cache-key-triad）。
4. `expertManualProfile.test.js` 通过真实挂载DOM与可控延迟请求覆盖下列检查；使用仓库已有Node测试机制，不加新构建系统（I-1–I-4/S-1/S-2）。

## 变更文件清单

| 文件 | 操作 / 职责 |
|---|---|
| `src/main/resources/static/app.js` | 修改：两个宿主、分组展示/编辑及清理 |
| `src/main/resources/static/styles.css` | 修改：只追加契约 CSS |
| `src/main/resources/static/index.html` | 修改：原11项资源统一缓存键 |
| `src/test/js/expertManualProfile.test.js` | 新建：真实节点、交互、异步与契约测试 |

共 **4 个文件**；未列文件禁止顺手修改。测试证据为执行产物，不扩张产品修改白名单。

## 验收标准

- I-1：同参数构造无contact/有contact两条实际宿主，19项全部出现且都能保存；不只grep函数名。01各字段边界在页面输入也适用。
- I-2：甲GET/保存延迟、先切乙再返回甲；乙DOM不出现甲数据，甲最终通过GET确认；返回列表后无悬挂弹窗或事件重复。
- I-3：200/400/409/401/503、失败重试、取消/Escape、双击保存；每次成功至多一个PATCH。
- I-4：恶意HTML作为纯文本；证件默认末四位；nationality缺失不显示country作国籍。
- S-1/S-2：CSS逐字diff；新增DOM只用契约class，无新增inline style；1366px与390px截图检查溢出、焦点与原操作可达。
- 运行 `node --test src/test/js/expertManualProfile.test.js` 后运行 `node --test src/test/js/*.test.js` 一次回归；记录浏览器真实页面验收，DOM stub通过不能替代浏览器。

## 人工验收清单

### A-1: 准确位置与字段
- 前置条件: 01/02测试环境部署；列表选一个无contact专家、一个有contact专家。
- 操作步骤: 1. 分别打开右侧联系详情。2. 查看四组并逐个打开编辑。3. 各填01字段样例保存。4. 刷新页面。
- 预期结果: 两种专家均显示19新项；六档与三档文案逐字符合需求；保存后刷新仍在。姓名/国籍/关键词只读；空值未填写。
- 覆盖: I-1/I-3；X1；S-1

### A-2: 不会串人
- 前置条件: 浏览器网络面板设慢速；准备甲乙专家。
- 操作步骤: 1. 打开甲后立即切乙。2. 等待甲响应。3. 返回列表再进乙。
- 预期结果: 乙没有甲字段、证件或旧错误；当前只有一个详情编辑弹窗宿主。
- 覆盖: I-2；X2

### A-3: 编辑取消、冲突与隐藏证件
- 前置条件: 同一专家两个浏览器窗口。
- 操作步骤: 1. 输入TEST001234保存。2. 退出编辑。3. 再编辑改译名后Escape。4. 两窗口同时改同版，甲保存后乙保存。
- 预期结果: 详情证件仅末四位1234可见；取消译名未变；乙显示“资料已被更新，请重新加载后再编辑”，保留输入、不覆盖甲。
- 覆盖: I-3/I-4

### A-4: 原流程与布局回归
- 前置条件: 一个有邮件/会议安排/材料的测试联系人；一个无contact专家。
- 操作步骤: 1. 使用学术档案、联系详情、模板预览、邮件预览四标签。2. 下载旧材料。3. 打开原邮件操作但取消发送。4. 改列表筛选并返回。5. 查看两种专家默认标签。
- 预期结果: 原四标签可达；旧材料下载内容一致；未多发邮件；筛选保持；无contact默认学术档案，有contact默认联系详情。
- 覆盖: I-1/I-4；X3；全部保持项

### A-5: 样式与缓存
- 前置条件: 1366px和390px浏览器，清缓存后加载，再正常刷新。
- 操作步骤: 1. 查看四组和弹窗。2. Tab聚焦控件、观察禁用保存。3. 查看Network资源版本。
- 预期结果: 无横向溢出；白色弹窗，标题14px、字段13px、焦点2px蓝#3b82f6；小屏单列；11个资源键均20261011-contact-profile-05。
- 覆盖: S-1/S-2；X4

## 修正记录

- 2026-10-11（fast-p amendment A1）：§实现方案 3 与 A-5 的缓存键字面量 `20261011-contact-profile-02` → `20261011-contact-profile-03`，使本计划的静态测试随 03 阶段键保持通过（02 测试把 index 实键钉死为本计划字面量；03 阶段按计划 bump 后该断言必然失败，且测试文件不在 03 授权内）。依据 master「执行门槛与顺序」2「每阶段缓存键统一11项」；05 阶段 bump 到 `20261011-contact-profile-05` 后将再次同步。
- 2026-10-11（fast-p amendment A2）：按同一口径将键字面量再次同步为 `20261011-contact-profile-05`（05 阶段键）。

