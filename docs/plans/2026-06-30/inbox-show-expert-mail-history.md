# 收发信箱处理面板内嵌专家历史信件记录

> 计划类型：前端增量功能（纯 app.js + styles.css，无后端改动）
> 创建日期：2026-06-30

## 需求描述

- **可观测结果**：在「收发信箱」的来信处理/查看面板（`showUnmatchedDetail`，标题「来信详情与处理」）中，当来信已关联专家时，新增一个**默认折叠**的「与该专家的历史信件记录」区块；点击展开后显示该专家的完整往来邮件时间线，无需再跳转到「专家联系」列表。
- **必须不变**：
  - 专家联系详情页（`loadContactDetail`）的邮件时间线渲染与行为完全不变。
  - 未关联专家（`record.expertContactId` 为空）的来信处理面板不显示该区块，行为与现状一致。
  - 现有的来信正文、清洗后正文、QA 回复、自动回复预览、人工回复、操作日志各区块的顺序与功能不变。
  - `renderMailItem` 函数本身不修改（被专家详情页共用，K-mail-body-display-sites 站点①）。
- **范围外**：
  - 不改后端、不新增 API、不改 `ExpertContactDetailResponse` 结构。
  - 不做分页/懒加载/虚拟滚动（历史邮件量级小，一次性渲染即可）。
  - 不在该区块内提供任何操作按钮（仅只读展示），人工回复/QA 等操作仍走面板既有区块。
  - 不改专家联系详情页。

## 关键不变量

### Invariant I-1: 历史区块仅在已关联专家时出现
- Rule：历史信件区块的渲染条件是 `record.expertContactId` 存在**且**历史邮件接口成功返回非空 `mails`。任一不满足则区块 HTML 为空串，不占位、不报错。
- Applies to：`showUnmatchedDetail` 内新增的历史拉取逻辑与 `historyHtml` 构造。
- Violation consequence：未关联来信会出现空区块或 JS 报错，污染处理面板。
- 来源：original

### Invariant I-2: 复用 `renderMailItem`，不新增正文渲染分支
- Rule：历史邮件每条必须经由现有 `renderMailItem(mail)` 渲染，正文展开沿用其内部 `.mail-body-detail` + `translatableBody`。不得在本计划内复制/改写正文渲染逻辑。
- Applies to：`historyHtml` 的 `mails.map(renderMailItem)`。
- Violation consequence：正文展示点分裂，破坏 K-mail-body-display-sites 的「站点全集」契约，翻译/换行行为不一致。
- 来源：K-mail-body-display-sites

### Invariant I-3: 历史拉取失败不阻断处理面板
- Rule：历史邮件接口 `GET /api/expert-contacts/{expertContactId}` 必须以 `.catch(() => null)` 兜底；失败时仅不显示历史区块，面板其余部分照常渲染。
- Applies to：`showUnmatchedDetail` 内新增的 `await api(...)` 调用。
- Violation consequence：专家详情接口偶发失败会导致整个来信处理面板渲染中断，操作员无法处理来信（功能回退）。
- 来源：original

## 现状审计

### 后端 API（只读，本计划不改）
- `GET /api/expert-contacts/{contactId}` → `ExpertContactManagementController.getContactDetail`（`ExpertContactManagementController.kt:61-62`），返回 `ExpertContactDetailResponse`，其中 `mails: List<MailRecordResponse>`（`:264-266`）。
  - 这是专家联系详情页 `loadContactDetail` 已使用的同一接口（`app.js:4065`，渲染于 `:4125-4127`）。
  - `MailRecordResponse` 字段（`:368`）即 `renderMailItem` 所消费的字段（`direction`/`mailType`/`subject`/`body`/`sendStatus`/`errorSummary` 等）。
- 来信处理面板自身的数据接口 `GET /api/mail/unmatched-inbound/{id}`（`app.js:5418`）返回 `{ record, candidates, contact }`，其中 `record.expertContactId` 标识是否已关联专家。

### 前端写/读路径（app.js）
- **目标读路径（本计划新增消费）**：`showUnmatchedDetail(id)`（`app.js:5417`）
  - `:5419-5423` `Promise.all` 拉取 detail / mailSendOptions / operator logs。
  - `:5425-5427` 已有「关联专家时才请求」的串行先例：`const suggest = record.expertContactId ? await api(...) : null;`——历史拉取照此模式。
  - `:5433` `linkedExpertHtml`（关联专家卡片，已基于 `record.expertContactId && contact`）。
  - `:5517-5593` `panel.innerHTML` 模板，区块顺序：metadata-grid → 原始正文 → 清洗后正文 → `${linkedExpertHtml}` → `${qaReplyHtml}` → `${composeWorkbenchHtml}` → 自动回复预览 → 人工回复 → 操作日志。
- **复用渲染器**：`renderMailItem(mail)`（`app.js:3871-3910`），内部含 `.mail-item`、`.mail-preview`、`.mail-body-detail`（`<details>`）、`translatableBody`；专家详情页 `:4126` 用 `detail.mails.slice().reverse().map(renderMailItem)` 倒序渲染（最新在上）。
- **样式**：`.mail-timeline`、`.mail-item`、`.mail-body-detail`、原生 `<details>/<summary>` 样式均已存在于 `styles.css`（被专家详情页与 `renderMailItem` 使用）。

### Interaction points
- 写路径：无（纯只读新增）。
- 读路径交互：本计划在来信处理面板**新增一个** `renderMailItem` 调用点 → 落入 K-mail-body-display-sites 记录的「邮件正文展示点全集」。今后任何「所有正文位置统一加能力」的需求需把本新增点纳入全集（见 Phase 6 知识写回）。

## 实现方案

### Stage 1：拉取历史邮件（app.js `showUnmatchedDetail`）

任务 1.1（遵守 I-1、I-3）：在 `const suggest = ...`（`app.js:5425-5427`）之后，新增历史邮件串行拉取：

```javascript
const history = record.expertContactId
    ? await api(`/api/expert-contacts/${record.expertContactId}`).catch(() => null)
    : null;
```

- 放在 `record` 解构之后（依赖 `record.expertContactId`），与既有 `suggest` 同样的「关联才请求、失败兜底」写法。
- 不并入开头的 `Promise.all`（那里尚无 `record`）。

### Stage 2：构造默认折叠的历史区块（app.js `showUnmatchedDetail`）

任务 2.1（遵守 I-1、I-2）：在 `const composeWorkbenchHtml = ...`（`app.js:5515`）附近，新增 `historyHtml` 常量：

```javascript
const historyMails = (history && history.mails) || [];
const historyHtml = record.expertContactId && historyMails.length ? `
    <details class="detail-section mail-history-detail">
        <summary>与该专家的历史信件记录（${historyMails.length} 封）</summary>
        <div class="mail-timeline">
            ${historyMails.slice().reverse().map(renderMailItem).join("")}
        </div>
    </details>
` : "";
```

- `<details>` 不加 `open` 属性 → 默认折叠，点击 summary 展开（原生行为，无需 JS 绑定）。
- `slice().reverse()` 与专家详情页一致（最新在上）。
- 关联专家但历史为空时 `historyHtml` 为空串（I-1）。

任务 2.2（遵守 I-1）：把 `${historyHtml}` 插入 `panel.innerHTML` 模板，位置在 `${linkedExpertHtml}`（`app.js:5562`）之后、`${qaReplyHtml}`（`:5564`）之前：

```javascript
            ${linkedExpertHtml}

            ${historyHtml}

            ${qaReplyHtml}
```

- 语义上「关联专家卡片」之后紧跟「该专家历史信件」最自然。

### Stage 3：样式（styles.css，可选增强）

任务 3.1：为新区块 summary 增加指针/间距样式（复用现有视觉，不引入新组件）：

```css
.mail-history-detail > summary {
    cursor: pointer;
    font-weight: 600;
}
.mail-history-detail > .mail-timeline {
    margin-top: 12px;
}
```

- `.detail-section`、`.mail-timeline`、`.mail-item*` 已有样式直接复用；本任务仅补 summary 可点击观感，非必需，若时间紧可省略（功能不依赖它）。

## 变更文件清单

| 文件 | 改动 | 关联不变量 |
| --- | --- | --- |
| `src/main/resources/static/app.js` | `showUnmatchedDetail` 内新增 `history` 拉取（Stage1）、`historyHtml` 构造（Stage2.1）、模板插入（Stage2.2） | I-1, I-2, I-3 |
| `src/main/resources/static/styles.css` | 新增 `.mail-history-detail` summary/间距样式（可选） | — |

文件数：2（≤10）。子系统数：1（前端静态资源）。新增共享存储字段：0。

## 验收标准

- **I-1**：
  - 打开一封**已关联专家**的来信处理面板 → 「与该专家的历史信件记录（N 封）」区块出现且默认折叠；点击展开显示 N 条邮件。
  - 打开一封**未关联专家**（`expertContactId` 为空）的来信 → 不出现该区块，面板其余功能正常。
  - 关联专家但该专家历史邮件为空 → 不出现空区块。
- **I-2**：展开后的每条邮件外观与「专家联系」详情页时间线一致（同 `renderMailItem`）；正文「查看完整正文」展开、翻译按钮行为与详情页一致。
- **I-3**：模拟 `/api/expert-contacts/{id}` 返回 500（或断网）→ 来信处理面板仍正常渲染，只是不显示历史区块，无 JS 控制台异常中断。
- **集成场景**：从「收发信箱」点「查看/处理」进入面板 → 在同一面板内既能看历史、又能正常执行 QA 回复/人工回复/标记已处理，互不干扰；历史区块的折叠状态不影响其他区块。

## Self-Review Checklist

- [x] 关键不变量含每个新行为对应 invariant（I-1 出现条件 / I-2 复用渲染 / I-3 失败兜底）
- [x] 现状审计经 grep/读源确认（API 行号、`renderMailItem`、面板模板顺序、`suggest` 串行先例）
- [x] 无新增写路径（纯只读），故无「未被 invariant 覆盖的写路径」
- [x] 文件数 2 ≤ 10
- [x] 子系统数 1 ≤ 2
- [x] 各任务标注所遵守 invariant 编号
- [x] 验收标准每条 invariant 至少一项检查
- [x] 文件清单无「及相关文件/等」措辞，逐一具名
- [x] 范围外明确排除后端改动、分页、操作按钮、专家详情页改动
- [x] Phase 0 知识：K-mail-body-display-sites 已采用（I-2 复用 `renderMailItem` 站点①）；K-contact-list-dual-query-path 已评估为不适用（本计划用 detail 接口取 mails，不走列表双查询路径）
- [x] 计划已保存至 docs/plans/2026-06-30/

## 备注：知识写回（执行/验证后由 fix-v 落地，此处预登记）

- K-mail-body-display-sites 需新增一条正文展示点：「收发信箱来信处理面板（`showUnmatchedDetail`）内嵌专家历史信件时间线，复用 `renderMailItem`」。今后「所有正文位置统一加能力」类需求须纳入此新增点。
