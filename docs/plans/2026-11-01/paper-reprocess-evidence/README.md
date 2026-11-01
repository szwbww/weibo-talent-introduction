# 调研回执

时间：2026-11-01。只读源码扫描；本目录不是执行或生产验证结果。

- `baseline.json`：本地 HEAD、命中生产源文件 SHA-256。工作区存在先前未提交改动，执行前必须重核；本次不声称与生产二进制一致。
- `paths.json`：各主题全量命中文件。
- `*.txt`：命令及完整 rg 命中行，覆盖 src/main、src/test、scripts；测试命中不计生产写路径。
- `schemas.txt`：实际 schema/mapping 原文。
- `frontend-before.md`：当前论文页 DOM、现有 CSS 与相关全局规则逐字基线。

量化样本证据另见 docs/audits/2026-11-01-discovery-30711-pdf/report.md；该审计明确未完成全部生产 PDFBox 根因重放。
