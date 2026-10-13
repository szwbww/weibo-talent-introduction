# 07 一人多邮箱展示

状态：设计，未实施。依赖 [06](discovery-person-06-query.md)。上级：[总计划](discovery-identity-recovery-master.md)。

## 需求描述

专家列表一人一行，显示邮箱数量；无联系人和已有联系人的详情都能看到各邮箱状态及原历史入口。保持当前布局、原联系人操作、材料组件、移动端返回与异步防串页。只对 API 返回有效 person 的 discovered 主档展示；不做邮箱编辑器或全新审核页面。

## 关键不变量

### Invariant I-1：后端人数与原业务 ID
- Rule：列表使用 06 的人数及分组，不在当前页自行去重；保留 person 与全部成员元数据。原发送、标签、资料、材料操作仍用明确选中的 docId/contactId，不把 personId 冒充 ORCID。默认代表邮箱与可发送邮箱分别标明，未取得邮箱准入时不显示“可发送”。
- Applies to：loadContacts 两分支、renderContactListItems、详情行为。
- Violation consequence：分页失真、误操作或误导发信资格。
- 来源：总计划 I-5/I-6。

### Invariant I-2：两个详情宿主同一契约
- Rule：showExpertDetail 和 loadContactDetail 共用邮箱片段；获取人物详情捕获 generation、原专家/contact 身份及宿主，旧响应不得覆盖新页面。所有姓名/邮箱/原因经 escapeHtml 或 textContent；历史入口保留原 contactId。非 discovered 保持原 UI。
- Applies to：两详情模板及 click 委托。
- Violation consequence：一条入口漏邮箱、串页或历史断链。
- 来源：K-expert-detail-two-panel-render-sites（旧行号已重新核实）。

## 样式契约

### S-1：列表与头部邮箱数量
- 复用：`styles.css:1386 .list-item-meta`、`:1420 .expert-meta span`、`:1637 .expert-header-info p`；只替换既有邮箱 span 文本，不改 class 规则或外围布局。
- DOM：列表原 `<span>${escapeHtml(contact.email || "邮箱未知")}</span>` 改为 `<span>${escapeHtml(emailSummary)}</span>`；详情原邮箱 span 同样替换文本。单邮箱沿用原文本；多邮箱为 `主邮箱地址 · 共 3 个邮箱`（数量取响应）；“主邮箱”仅表示展示默认。
- 禁止项：新增 CSS、inline style、改行高/字号/颜色、把原成员的标签直接并集后作为新资格。

### S-2：详情联系方式卡片
- 复用：`styles.css:1652 .metadata-grid`、`:1659 .metadata-card`、`:1673 .metadata-card-header`、`:1690 .metadata-card-value`、`:1710 .metadata-card.span-all`、`:2683 .link-btn`。不改这些规则，不新增 class。
- DOM：在两个详情的联系资料面板 `.metadata-grid` 中增加同一片段。下列邮箱项按成员重复；使用既有 click 委托解析 data-action。无历史时不渲染 button。

```html
<div class="metadata-card span-all" data-role="discovery-person-emails">
  <div class="metadata-card-header"><span>联系方式</span></div>
  <div class="metadata-card-value" data-member-doc-id="原档案ID">
    <span>邮箱地址 · 展示主邮箱 · 准入状态 · 邮箱状态 · 来源</span>
    <button type="button" class="link-btn" data-action="open-person-member-history" data-contact-id="原联系人ID">查看该邮箱历史</button>
  </div>
</div>
```

- 无法读取状态：显示 `状态未取得`；确有成员资料冲突：另一个同 class value 行显示 `成员资料存在差异，请查看对应邮箱档案`。错误/加载文本在本卡片内使用 `.metadata-card-value`，不改变发送许可。
- 禁止项：新 CSS/inline style、重复全局 id、默认自动切换联系人、重新排列详情子标签。

## 现状审计

- `app.js` 当前 loadContacts 有 ES 与 useDbContactPath 分支；后者先请求全联系人，再前端域名筛选和 slice。06 的分组响应需在映射时保留 person；新分支域名条件交给后端。前端仍可对完整、已聚合 DB 人物数组切页，不可先切成员再聚合。
- 当前详情 `showExpertDetail:13477` 与 `loadContactDetail:13935`；列表 `renderContactListItems:9149`。行号随已有用户改动变化，以函数定位为准。两模板都存在 `.metadata-grid`；generation 保护及材料 unmount 必须保持。
- 当前基线（逐字）为列表 `<span>${escapeHtml(contact.email || "邮箱未知")}</span>`，无联系人详情 `<span>${escapeHtml(expert.email || "邮箱未知")}</span>`；原头部骨架 `.expert-profile-header > .expert-header-info > h2 + p` 不改变。
- 样式基线：`--primary:#1e40af`、`--text-main:#1e293b`、`--text-muted:#94a3b8`、`--panel-border:rgba(15,23,42,0.08)`、`--border:rgba(15,23,42,0.11)`、`--radius-sm:7px`。卡片 padding=10px 12px、gap=4px；grid gap=8px；头部邮箱 font-size=12px、gap=6px、flex-wrap=wrap；value font-size=13px、line-height=1.4、word-break=break-all。

```css
.metadata-card {
    background-color: rgba(15, 23, 42, 0.02);
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-sm);
    padding: 10px 12px;
    display: flex;
    flex-direction: column;
    gap: 4px;
}

.metadata-card:hover {
    border-color: var(--border);
}
```

只复用上述既有规则，无需修改其他使用位置。新内容读 06，界面不直接写人物表。

## 实现方案

1. **列表（I-1/S-1）**：app.js 保留 person 响应，DB 分支请求 06 的分组模式与服务器邮箱域名筛选；总数按响应，现有分页不在成员层运行；列表/头部仅改邮箱文字。
2. **详情（I-1/I-2/S-2）**：同文件封装共用只读邮箱卡片并在两宿主调用人物接口；点击历史使用已有原 contactId 导航，标明当前查看的邮箱，不把人物成员历史写回当前联系人。重复渲染不重复绑定监听器。
3. **定向 UI 测试（I-1/I-2/S-1/S-2）**：新增测试用实际函数及两宿主 DOM 骨架，覆盖新响应映射、点击历史、快速切页、长邮箱转义；不靠未声明的全局测试 stub 掩盖引用缺失。执行时沿用仓库 Node 测试命令习惯。

## 变更文件清单

|文件|动作|
|---|---|
|src/main/resources/static/app.js|修改|
|src/test/js/discoveryPersonEmails.test.js|新增|

共 2 文件；前端一个子系统；CSS/HTML 静态文件修改 0，共享存储字段新增 0。

## 验收标准

- I-1：列表人次和 total 一致；三个邮箱显示“共 3 个邮箱”；点击/发送仍携带原 docId/contactId。
- I-2：两详情入口邮箱集一致；旧响应不覆盖；特殊字符按文本展示；无 person 响应的原页面不变。
- S-1/S-2：diff 无 CSS/inline style/未声明 class；DOM 匹配契约；桌面与窄屏目测无溢出。

## 人工验收清单

### A-1：一人三邮箱与两个入口
- 前置条件：测试库一人 3 邮箱，1 个合格、1 个待核、1 个抑制；至少 2 个原联系人有邮件历史；另有普通非 discovered 专家。
- 操作步骤：从 ES 人物列表及 DB 联系人筛选分别打开；展开联系资料；点击两邮箱历史；查看普通专家。
- 预期结果：每列表此人 1 行、显示“共 3 个邮箱”；两详情显示独立三种状态，抑制邮箱不显示可发送；历史仍属于原联系人；普通页面文案/操作不变。
- 覆盖：I-1/I-2、S-1/S-2、双列表/双详情和历史回归。

### A-2：窄屏与异步
- 前置条件：375px 窄屏；一条长邮箱及包含 `<` 的来源文本；人工延迟人物详情请求。
- 操作步骤：打开详情后立即切另一专家；等待前请求完成；检查卡片与原材料/返回入口。
- 预期结果：不会串页；特殊字符显示为文本；卡片圆角 7px、内边距 10px 12px、文字 13px/1.4，长邮箱换行；原材料、返回入口可用。
- 覆盖：I-2、S-2及保持项。
