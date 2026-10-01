# Emailable 验证结果多选放行：开发计划总览

状态：**待评审，未开始实现**。日期：2026-10-01。按用户已确认的 [界面预览](../../mockups/email-verification-policy-preview/index.html) 规划；本轮产物只有计划、源码证据及知识条目。未修改业务代码或线上配置。

## 需求描述

在收发件箱现有批量任务的发送控制中，以独立复选框选择允许的 Emailable 结果。只扩展现有开关、任务配置和验证流程，不新建策略系统。

| 场景 | 规则 |
|---|---|
| 可选结果 | deliverable / risky / unknown |
| undeliverable | 不展示可选框，后端永不允许放行 |
| 新任务、独立手动 | 默认只勾 deliverable；原验证总开关仍默认关闭 |
| 存量任务、旧快照缺字段 | 保留当前三态放行；编辑时真实回显三项 |
| 全不勾选 | []=不放行任何明确验证结果，不能解释为默认/不限 |
| 总开关关闭 | 保留选择，控件禁用；不执行 Emailable 验证 |
| 不符合本次选择 | 策略跳过，留验证明细，不打「邮箱异常」 |
| undeliverable | 保持跳过并标记「邮箱异常」 |
| 历史验证复用 | 复用供应商事实，按本次选择重新判放行 |
| 手动覆盖 | 只进入本次执行快照，不保存回来源任务 |

必须保持：供应商错误处理、原始结果有效期、现有成功配额与发送节奏、模板/账号/专家范围门禁、历史不可投递预筛选独立开关。这里的“永不允许放行 undeliverable”指**开启发送前验证后的决定**；本次不取消用户现有关闭验证能力，也不把两个开关合成一个。

范围外：自动修改存量任务、回复/材料提醒验证、全局/每账号策略、批量回填历史明细、发信信誉监控、新供应商功能、生产部署。

## 关键不变量

具体 I-n 按各子计划局部编号，执行/验收必须带子计划名引用，不能混用：

- 执行与审计 I-1～I-5：白名单、供应商事实/策略分离、跳过副作用、复用/清理对称、既有守卫。
- 任务配置 I-1～I-4：一列事实源、新旧默认差异、固定快照、生命周期兼容。
- 前端 I-1～I-5：勾选与请求一致、禁用保值、手动隔离、日志解释、缓存/预估回归。

只新增一个持久化配置列、一个执行快照属性、一个策略跳过原因码；复用已有 PASS/SKIP/ERROR 及原日志接口，不增加数据库表或 HTTP 端点。

## 现状审计

完整逐字源码与 grep 回执：[证据附件](email-verification-allowlist-evidence.md)。

已证实的关键点：

1. 当前放行硬编码在 BatchEmailVerificationService.kt:298-303，deliverable/risky/unknown 均 PASS。
2. 当前 conclude:191-206 对所有 SKIP 都加异常标签，不能直接把未选中的 risky/unknown 改成 SKIP 就结束。
3. 历史查询 :209-228 与 TaskExecutionRepository:178-193 都漏 SKIP deliverable，独立多选允许取消 deliverable 后必须同时补齐。
4. 实际循环 ManualInitialOutreachService:683-687 用成功数 roundPassed 控制配额；跳过只增 processed/rejected。部分旧注释说“占处理槽”，不能据此改变当前补足成功数行为。
5. 配置新增字段需覆盖 updateLegacyConfig、View、三类 toFields 与 toExecutionSnapshot；旧接口不能把新值重置。
6. 前端保存 payload、编辑预估、手动快照、草稿差异是分开的映射点，均需接入。
7. 没有现成「还原来源参数」按钮；恢复来源值应重新选择同一任务，计划不新造恢复入口。
8. Flyway 最新文件已到 V144，但集成测试最新版本断言仍写 142；新增迁移时需精确更新最新断言，不动历史目标版本。

知识已复核：K-batch-send-setting-kv、K-batch-config-legacy-adapter-field-preservation、K-batch-task-config-snapshot-log-identity、K-batch-send-legacy-routes-entity-ssot、K-batch-verification-latest-result-retention、K-batch-send-round-loop-symmetry、K-batch-console-regression-contract、K-js-test-invocation-surface、K-frontend-cache-key-triad、K-plan-quantified-claims-need-grep-receipts。旧 retention 条目“较新 PASS risky/unknown 未受保护”已过时，源码现已保护，已修订知识条目。本次相关条目未超 90 天，无需归档；未进行跨域知识清扫。

读取但不扩展范围：K-batch-send-filter-retry-parity（执行入口已统一验证，本次不新增 ES 预筛选）；K-batch-send-scheduler-reschedule-on-enable（复用既有 reload）；K-independent-manual-run-not-in-config-lists（当前已有全执行日志入口）；前端全局 p/透明 panel 经验（不改浮层结构/背景）。这些仅作回归检查，不新增相邻改造。

## 实现方案

create-p 要求每份执行计划最多 10 文件。为容纳已有测试依赖，拆成三个顺序切片，**不可并行修改共享文件**：

| 顺序 | 计划 | 文件数 | 独立可验证结果 |
|---|---|---:|---|
| 1 | [执行与审计](email-verification-allowlist-backend.md) | 10 | 手动 API 可按列表决定放行，复用/标签/保留正确 |
| 2 | [任务配置](email-verification-allowlist-config.md) | 7 | 任务 API 保存/回显选择，定时及按配置手动消费 |
| 3 | [前端接线](email-verification-allowlist-frontend.md) | 9 | 真实页面可多选、保存、手动覆盖及读取跳过日志 |

每份计划都有精确变更文件、代码行证据、I-n、测试矩阵、A-n；前端另含 S-n 与完整 CSS/DOM 契约。第 1、2 计划可独立部署使用 API；最终用户功能须三份全完成才宣布完成。

执行开始前：核对证据附件 SHA-256/相关 diff；若代码已变化，重查实际路径，不照旧行号盲改。V145 被占用时只顺延新迁移版本并同步文档测试。保持工作树已有其他修改。文件范围之外的问题只记观察，不能顺手重构。

## 变更文件清单

本总览不直接授权改业务文件；每个子计划的「变更文件清单」为该步的封闭范围，超范围先修订计划。

共享文件须由后一步基于前一步结果继续：BatchExecutionModels.kt、BatchSendControlServiceTest.kt。不建重复类型、重复验证服务、重复 endpoint 或前端状态库。

## 验收标准

- 每步执行对应单测/IT；跳过的 Docker/MySQL IT 必须记未验证，不当通过。
- 完成前端后执行 node 全量测试和真实后端页面保存/重开验证。
- 最后执行项目 mvn test；验证只看本次实际修改及既有行为回归，不把历史测试/工作树问题混算为已修。
- 证据区分：本轮确认了代码事实；计划中的新功能/浏览器接线尚未实现，未运行功能测试。

## 人工验收清单

各子计划的 A-n 为权威逐步清单，总验收必须覆盖其全部项：

- 第 1 步：手动 API 放行、策略跳过无副作用、undeliverable、开关/异常、复用与保留。
- 第 2 步：新旧默认、空选择/非法选择、旧接口/生命周期、定时与手动快照隔离。
- 第 3 步：真实页面编辑保存、来源覆盖恢复、全空/禁用/模板切换、日志、桌面/窄屏/键盘、缓存与预估。

仅在开始人工验收时，从各计划 A-n 导出同目录同前缀的 *-acceptance.md，包含勾选框、验收人、日期、结果/备注；当前不预先生成，不把预览认可当成功能验收。

