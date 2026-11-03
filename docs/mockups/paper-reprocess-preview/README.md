# 按采集任务重新解析并补录：前端预览

- 线上独立预览：https://qingfei.szwbww.com/previews/paper-reprocess-20261101.html
- 静态部署位置：/opt/apache-tomcat-9.0.71/webapps/ROOT/previews/paper-reprocess-20261101.html
- 2026-11-01 读取线上 `/talent/` 与 `styles.css?v=20261031-mailbox-polish`，保存 production-* 快照，复用当前导航、论文页结构及 CSS。
- 正式管理页当时未登录；没有使用认证会话、没有修改正式页面或后端。
- 任务 #30711 的 3,185 篇有原文论文及 3 个列表样本来自本次既有只读核对；非实时计数，提交前应重新确定范围。
- 日志 100 篇、12 人、8 人等为明确标注的虚构示例，不是 325 人修复预测或真实执行结果。
- 页面内 CSP 禁止网络连接和表单提交，所有按钮仅改变演示 DOM。
- 可点击：整批入口、对照/应用模式、确认演示、6 步流程、冲突说明。其他页签与业务功能未接入。
- 已在浏览器核对入口、确认弹窗、应用模式演示日志及冲突步骤；截图为 entry.jpg、confirm.jpg、result.jpg、steps.jpg。
- 构建：`python3 build.py`。仅生成静态预览；未修改业务代码，未提交 Git。
