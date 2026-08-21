# Manual Acceptance — docs/plans/2026-08-21/ui-tweaks-00-execution-order.md

Generated from the master plan's child-plan manual acceptance checklists (P1 ui-tweaks-01 A-1..A-11, P2 ui-tweaks-02 A-1..A-10, P3 ui-tweaks-03 A-1..A-9, P4 qa-gate-visibility A-1..A-13). No optional product requirements added. All items PENDING; none performed or simulated by the machine review.

## Epoch 1 — 2026-08-21

- Reviewed code boundary: bb34ca2001d0abeac3bd7a8fc13995769e14143e..c13b12d8c25652b5047889c4075aba6c9c4a5bbf
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| P1-A-1 | Yes | 专家列表工具栏不再有「检查回复」 | 工具栏无「检查回复」四字；按钮序为刷新/发现专家/自动回复状态/回刷 ES | PENDING | | | |
| P1-A-2 | Yes | 收发件箱面板标题栏出现「检查回复」，紧贴「批量发送」左侧 | 标题贴左，右侧白底灰边「检查回复」+ 蓝底白字「批量发送」，间距约 8px 紧邻 | PENDING | | | |
| P1-A-3 | Yes | 「检查回复」功能与迁移前一致（无运行中任务） | 弹窗标题「检查回复」、进度视图、终态与迁移前一致 | PENDING | | | |
| P1-A-4 | Yes | 跨路径：专家列表勾选 → 收发件箱点「检查回复」仍定向生效 | 弹窗显示定向范围，处理条数为勾选数（2），非全量 | PENDING | | | |
| P1-A-5 | Yes | 回归：运行中任务的按钮状态仍能恢复 | F5 刷新后按钮显示运行中态，进度弹窗自动打开，结束后恢复可点 | PENDING | | | |
| P1-A-6 | Yes | 回归：「批量发送」按钮功能未受影响 | 弹窗正常、模板/预估正常、按钮文案样式不变 | PENDING | | | |
| P1-A-7 | Yes | 来信详情不再有「自动回复预览」 | 处理与回复分组无「自动回复预览」折叠块及副标题 | PENDING | | | |
| P1-A-8 | Yes | 未绑定专家的来信，降级灰字块一并消失 | 无「该来信尚未绑定专家联系人…」灰字；工作台/人工回复显示与迁移前一致 | PENDING | | | |
| P1-A-9 | Yes | 回归：可信回复工作台照常可用 | 摘要卡片加载、一键预判生成、整合产出、采用后填入正文并弹提示，无资源加载失败 | PENDING | | | |
| P1-A-10 | Yes | UI 目测：面板标题栏排版 | 按钮垂直居中同基线、间距 8px、贴右内边距、1280px 不换行裁切、主次视觉层级 | PENDING | | | |
| P1-A-11 | Yes | 回归：其余面板标题栏未受牵连 | 邮箱账号/邮件模板/任务记录/监控四视图排版与改动前一致 | PENDING | | | |
| P2-A-1 | Yes | 一键预判期间遮罩明显可见且带取消 | 白色半透明+模糊遮罩，白卡片含转圈/阶段文字/灰字提示/红「取消生成」；控件不可点 | PENDING | | | |
| P2-A-2 | Yes | 遮罩不遮折叠标题，长内容时卡片跟随滚动 | 折叠标题不被盖住、可折叠；展开后遮罩仍在；滚动时卡片停在视口约 96px | PENDING | | | |
| P2-A-3 | Yes | 遮罩上的「取消生成」真的能取消 | 遮罩消失、恢复可操作、状态条显示取消结果、未完成条目保持未生成 | PENDING | | | |
| P2-A-4 | Yes | 跨路径：非生成类忙碌也出遮罩，且文案说得对 | factChangePending/frameSavePending/completePending 三种遮罩文案正确、肉眼可见、无取消按钮 | PENDING | | | |
| P2-A-5 | Yes | 确认弹窗不再透底、文字可读 | 弹窗不透明（浅色纯白/暗色深蓝灰）、标题深色加粗、正文深色 13px、橙色告警不变、按钮不变 | PENDING | | | |
| P2-A-6 | Yes | 回归：其他复用确认框同样不透且不变形 | resolve-handoff / switch-to-manual / confirm-typed 等抽查两种，样式不透不变形 | PENDING | | | |
| P2-A-7 | Yes | 回归：暗色主题下两者都不透 | 暗色主题下遮罩与确认框均为不透明、无主题撕裂 | PENDING | | | |
| P2-A-8 | Yes | 回归：工作台空闲时无任何遮罩残留 | 空闲态无遮罩残留元素 | PENDING | | | |
| P2-A-9 | Yes | 回归：收发件箱 AI 聊天面板的原有遮罩不受影响 | 既有聊天面板遮罩行为不变 | PENDING | | | |
| P2-A-10 | Yes | UI 目测：遮罩卡片排版 | 对照样式契约实值逐项目测（卡片位置、文案层级、按钮外观） | PENDING | | | |
| P3-A-1 | Yes | 普通来信主题自动加 `Re:` | 输入框预填 `Re: Application for the talent programme`，placeholder 不显示 | PENDING | | | |
| P3-A-2 | Yes | 专家回信主题不叠加 `Re: Re:` | 仅一个 `Re:`，大小写与来信一致 | PENDING | | | |
| P3-A-3 | Yes | 无主题来信预填 `Re:` | 输入框为 `Re:`（冒号后无空格） | PENDING | | | |
| P3-A-4 | Yes | 回归：清空主题仍然拦截发送 | 清空后发送弹红色「请输入邮件主题」，不发送、无确认框 | PENDING | | | |
| P3-A-5 | Yes | 改写主题后发出去的是改写值 | 发件记录主题逐字为改写值，服务端不额外补 `Re:` | PENDING | | | |
| P3-A-6 | Yes | 跨路径：超长主题不会导致发送报错 | 预填截断 ≤255 字符；发送成功，无 255 报错 | PENDING | | | |
| P3-A-7 | Yes | 回归：采用草稿后主题保持预填 | 从工作台采用草稿后主题仍为预填值 | PENDING | | | |
| P3-A-8 | Yes | 回归：详情面板重新打开后主题回到预填值 | 重新打开详情，主题回到预填值 | PENDING | | | |
| P3-A-9 | Yes | UI 目测：输入框外观未变 | 输入框外观与改动前一致 | PENDING | | | |
| P4-A-1 | Yes | 打开编辑框即可见授权绑定 | 「AI 覆盖能力（事实授权）」面板显示已勾选 N 项、chip 行、分组复选框列表 | PENDING | | | |
| P4-A-2 | Yes | 受控能力有可见标记 | 五项受控键有琥珀「受控」标签；「为什么有的能力带锁？」说明含规定表述 | PENDING | | | |
| P4-A-3 | Yes | 普通规则不受打扰 | 非受控规则绿色门禁条、保存可点、保存成功 | PENDING | | | |
| P4-A-4 | Yes | 规则 24 恢复可保存（本轮核心） | chip 行 9 个 chip 不含受控项；绿色门禁条；保存成功无 `Controlled coverage keys must form exactly one V82 atomic fact group` | PENDING | | | |
| P4-A-5 | Yes | 规则 24 正文未被改动（回归） | 正文以 `Two tracks:` 开头、含 `There are no fees...`；answer_body=reply_body 与 V107 前一致 | PENDING | | | |
| P4-A-6 | Yes | 总览型规则新建不再被误伤 | 勾受控键+非受控键可保存，琥珀提示条（非拦截），验收后删测试规则 | PENDING | | | |
| P4-A-7 | Yes | 受控规则正文被改动 → 中文拦截 + 三条出路 | 琥珀门禁条+红徽章+保存置灰；查看差异/恢复标准正文工作正常 | PENDING | | | |
| P4-A-8 | Yes | 解除授权 —— 最后授权源警告（红分支） | 确认卡含红色加粗「这是最后一个授权源。」及转人工说明；「再想想」收起且状态不变 | PENDING | | | |
| P4-A-9 | Yes | 解除授权 —— 仍有其它出处（绿分支）+ 成对移除 | G3 两键同进同出；绿「仍有其它权威出处」列出的规则真实存在；验收后点取消 | PENDING | | | |
| P4-A-10 | Yes | 取消勾选真的落库（IP-3 回归） | 全取消保存后重开仍空、SQL 查 coverage_keys 为空串 `''`；验收后删测试规则 | PENDING | | | |
| P4-A-11 | Yes | 启用受控脏数据规则仍被拦（回归） | 启用失败、顶部状态条显示后端原文（含 `canonical body`）、列表仍禁用 | PENDING | | | |
| P4-A-12 | Yes | UI 目测（对照样式契约实值） | 面板/chip/门禁条/对照卡 7 项样式实值逐项目测 | PENDING | | | |
| P4-A-13 | Yes | 缓存键生效（回归） | 不清缓存刷新后新面板正常；三处 `?v=` 均为 `20260821-v12-qa-coverage-gate` | PENDING | | | |
| PRE-1 | Yes | 执行前置条件 1（需求方/发布负责人） | `SELECT id, reply_subject, enabled, coverage_keys FROM qa_rule WHERE id = 24;` coverage_keys 与 T3 WHERE 基线串逐字一致；不一致→先人工合并再部署 | PENDING | | | |
| PRE-2 | Yes | 执行前置条件 2（需求方/发布负责人） | 盘点全部声明受控键的启用规则（正则 `fees\.policy|confidentiality\.materials|contract\.party|contract\.terms|ip\.arrangements`），确认无第二条「恰为同一受控组」的规则，用于 A-8/A-9 选样 | PENDING | | | |

## Human Sign-off
- Decision: PENDING
- Boundary: c13b12d8c25652b5047889c4075aba6c9c4a5bbf
- Reporter: N/A
- Timestamp: N/A
- Note: Machine review PASS (epoch 1). Human must run every item above and sign off the boundary. P1-A-4/A-5, P2-A-1..A-7, P3-A-4, P4-A-4/A-5/A-7/A-8/A-9/A-10/A-11 require a real browser/DB environment. PRE-1/PRE-2 are release pre-conditions requiring a live DB.
