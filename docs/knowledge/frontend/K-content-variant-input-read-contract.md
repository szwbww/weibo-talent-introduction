---
id: K-content-variant-input-read-contract
domain: frontend
created: 2026-09-23
last_used: 2026-09-23
hit_count: 12
source: create-p:ui-collapsible-preview-and-variant-carousel
severity: P1
---

2026-09-23 重新核实：当前 QA 编辑页已改成标准事实正文，没有 qaRuleVariantsContainer；回复片段仍使用 replySnippetVariantsContainer。

回复片段编辑器读取契约是遍历容器内所有 `.content-variant-input` textarea：app.js 的 collectContentVariants:10954、validateContentVariantInputs:10969、addContentVariantRow:11020、removeContentVariantRow:11029；saveReplySnippet:5897 最终提交完整数组。

改成一个可见编辑框时，最小兼容方案是每个变体保留常驻 DOM，仅切显隐；原文保留 name=content，不混入 variants。不要只保留活跃 textarea 却继续调用旧 DOM 收集器；若将来改成数组模型，必须同时替换所有上述读路径。本期计划选择常驻节点方案。

新增/删除前应收集 raw 值，包括空值；collectContentVariants 会 trim/filter，只适合校验通过后的提交。隐藏的 required 原文为空会触发原生表单无法聚焦问题，须由完整保存校验定位并显示错误版本。变量插入目标和片段预览目标也要跟随当前版本。
