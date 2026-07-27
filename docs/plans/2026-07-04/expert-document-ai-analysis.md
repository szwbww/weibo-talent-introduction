# 专家文档 AI 智能分析

## 需求描述

**可观察结果**：专家详情页"专家上传资料"区域新增"AI 智能分析"按钮（仅当有附件时可点击），点击后弹出文件选择弹窗，选择后调用 DeepSeek 解析文件内容，以结构化卡片形式展示分析结果（每条带溯源引用），运营可手动编辑后保存。

**不可改变**：
- 现有 `ExpertDocument` / `MailAttachment` 表结构和读写逻辑不变
- 现有 `LlmDraftClient` 接口和 `HttpLlmDraftClient` 实现不变（复用 `chat()` 方法）
- 现有文档下载/预览功能不受影响
- 专家联系人详情页其他功能不受影响

**不在范围**：
- Word (.docx) 文件解析（后续独立计划）
- 图片 OCR 识别（护照/学位证等扫描件，后续独立计划）
- 分析结果自动回填到 ES 专家索引
- 多次分析结果的版本对比

---

## 关键不变量

### Invariant I-1: 分析请求必须附带 attachmentIds，且全部属于目标 expertContact
- Rule: API 必须验证每个 `attachmentId` 通过 `expert_document.mail_attachment_id` 关联到请求中的 `contactId`，不得跨专家读取文件
- Applies to: `ExpertDocumentAnalysisService.analyze()`
- Violation consequence: 越权读取其他专家的文件内容

### Invariant I-2: LLM 调用使用专用超时配置，超时/失败时前端得到明确错误而非挂起
- Rule: 文档分析 LLM 请求使用 `talent-introduction.llm.timeout-ms` 配置的超时；异常时返回 HTTP 500 + 结构化错误信息，前端显示"分析失败，请重试"
- Applies to: `ExpertDocumentAnalysisService.analyze()` → `LlmDraftClient.chat()`
- Violation consequence: 慢请求阻塞运营工作台 (来源: K-llm-timeout-fallback)

### Invariant I-3: 溯源 excerpt 必须是原文子串
- Rule: 后端对 LLM 返回的每个 `excerpt` 校验是否为对应文件提取文本的子串；不匹配的标记 `verified: false`
- Applies to: `ExpertDocumentAnalysisService.verifyExcerpts()`
- Violation consequence: 运营看到虚假出处，产生误信

### Invariant I-4: 分析结果持久化后可编辑，编辑不改变溯源标记
- Rule: `expert_analysis_result` 表中 `value` 字段可被 UPDATE（手动编辑），但 `source_file_id`、`source_excerpt`、`verified` 字段仅分析时写入，编辑时不覆盖
- Applies to: `ExpertDocumentAnalysisController.updateField()`
- Violation consequence: 人工修正值的溯源被误标为 AI 原始出处

### Invariant I-5: 按钮仅当 documents 非空时渲染为 enabled
- Rule: 前端 `renderExpertDocuments()` 中，文档列表为空时不渲染分析按钮；非空时渲染可点击按钮
- Applies to: `app.js` → `renderExpertDocuments()`
- Violation consequence: 无附件时点击触发无意义请求

---

## 现状审计

### expert_document 表
- Schema: `id, expert_contact_id, mail_attachment_id, document_type, document_status, review_note, created_at, updated_at`
- Write paths:
  1. `AutoMailReplyService.processInbound()` → 新入站邮件含附件时创建
  2. `ExpertContactManagementService` → 文档审核状态更新
- Read paths:
  1. `ExpertDocumentBrowseService.listDocuments()` → 前端文档列表
  2. `ExpertDocumentBrowseService.resolveForDownload/Preview()` → 下载/预览鉴权
  3. `ExpertDocumentRepository.findFirstByMailAttachmentId()` → 鉴权校验
- Interaction points: 本计划读 expert_document 获取 attachment 关系，不写入此表

### mail_attachment 表
- Schema: `id, mail_record_id, inbound_processing_id, file_name, content_type, file_size, storage_path, created_at`
- Write paths:
  1. `MailAttachmentService` → 入站邮件附件保存
- Read paths:
  1. `ExpertDocumentBrowseService` → 通过 expert_document join 读取文件元信息
  2. `MailboxAttachmentService` → 邮箱附件展示
- Interaction points: 本计划通过 `storage_path` 读取物理文件内容提取文本，只读

### LLM 基础设施 (`HttpLlmDraftClient`)
- Config: `talent-introduction.llm.enabled/api-url/api-key/model/timeout-ms`
- Write paths: 无（只做 HTTP POST 调用外部 API）
- Read paths:
  1. `AiReplyDraftService` → 邮件回复草稿
  2. `LlmStitchService` → QA 拼接
  3. `AiQaExtractionService` → QA 提取
- Interaction points: 本计划复用 `LlmDraftClient.chat()`，共享同一超时配置；如 DeepSeek 需要独立 endpoint，在 config 层加子属性

### 前端 app.js `renderExpertDocuments()`
- 位置: L5115-5147
- 当前行为: 列表为空显示"暂无资料文件"；非空显示下载/预览按钮
- 本计划在此函数输出中增加"AI 智能分析"按钮

---

## 实现方案

### Phase A: 后端 — 新建分析服务与 API

#### Task A1: 新建 `expert_analysis_result` 表 (V59 migration)
- 遵循: I-4
- 文件: `src/main/resources/db/migration/V59__create_expert_analysis_result.sql`
- 表结构:
  ```sql
  CREATE TABLE expert_analysis_result (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      expert_contact_id BIGINT NOT NULL,
      field_key VARCHAR(64) NOT NULL,
      field_label VARCHAR(128) NOT NULL,
      value TEXT NOT NULL,
      source_attachment_id BIGINT,
      source_excerpt TEXT,
      excerpt_verified TINYINT(1) NOT NULL DEFAULT 0,
      display_order INT NOT NULL DEFAULT 0,
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      KEY idx_analysis_contact (expert_contact_id, display_order),
      CONSTRAINT fk_analysis_contact
          FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id)
  );
  ```

#### Task A2: Domain + Repository
- 遵循: I-4
- 文件: `src/main/kotlin/.../document/domain/ExpertAnalysisResult.kt`
- 文件: `src/main/kotlin/.../document/repository/ExpertAnalysisResultRepository.kt`
- Repository 方法: `findAllByExpertContactIdOrderByDisplayOrderAsc`, `deleteAllByExpertContactId`

#### Task A3: 文档文本提取服务
- 遵循: I-1
- 文件: `src/main/kotlin/.../document/service/DocumentTextExtractor.kt`
- 职责: 给定 `attachmentId` 列表，读取物理文件，按 content_type 分发:
  - `application/pdf` → PDFBox `PDFTextStripper` (已有依赖)
  - `text/*` → 直接读 UTF-8
  - 其他类型 → 跳过，返回空文本 + 标记"不支持"
- 鉴权: 接收 `contactId`，通过 `ExpertDocumentRepository` 验证每个 attachment 归属

#### Task A4: AI 分析服务
- 遵循: I-1, I-2, I-3
- 文件: `src/main/kotlin/.../document/service/ExpertDocumentAnalysisService.kt`
- 逻辑:
  1. 调用 `DocumentTextExtractor` 获取 `Map<attachmentId, ExtractedText>`
  2. 拼装 prompt（带 `<FILE>` 标签包裹，要求返回 JSON schema）
  3. 调用 `LlmDraftClient.chat(messages)` → 解析返回的 JSON
  4. 对每个 `excerpt` 执行子串校验 → 设置 `verified` 字段
  5. 批量写入 `expert_analysis_result`（先 delete 旧记录再 insert，即每次分析覆盖）
  6. 异常处理: LLM 超时/解析失败 → 抛自定义 `AnalysisFailedException`

#### Task A5: Controller
- 遵循: I-1, I-2
- 文件: `src/main/kotlin/.../document/controller/ExpertDocumentAnalysisController.kt`
- 端点:
  - `POST /api/expert-contacts/{contactId}/ai-analysis` — body: `{ "attachmentIds": [1,2,3] }` → 触发分析，返回结果
  - `GET /api/expert-contacts/{contactId}/ai-analysis` — 获取已保存的分析结果
  - `PUT /api/expert-contacts/{contactId}/ai-analysis/{fieldId}` — body: `{ "value": "..." }` → 手动修改单字段值
  - `DELETE /api/expert-contacts/{contactId}/ai-analysis` — 清除分析结果

### Phase B: 前端 — 文件选择 + 结果弹窗

#### Task B1: 在文档列表区域增加"AI 智能分析"按钮
- 遵循: I-5
- 文件: `src/main/resources/static/app.js`
- 改动点: `renderExpertDocuments()` 函数内，当 `list.length > 0` 时在 header 右侧渲染按钮

#### Task B2: 文件选择弹窗
- 文件: `src/main/resources/static/app.js`, `src/main/resources/static/index.html`
- 行为:
  - 点击按钮 → 弹出 modal，列出该专家所有文档（checkbox + 文件名 + 类型标签）
  - 默认勾选 CV 和学位类文件
  - "开始分析"按钮发起 POST 请求

#### Task B3: 分析结果展示 + 编辑弹窗
- 文件: `src/main/resources/static/app.js`, `src/main/resources/static/styles.css`
- 行为:
  - Loading 状态（骨架屏/spinner）
  - 结果以卡片列表展示：每行 = label + 可编辑 input + 来源文件名 badge
  - hover 来源 badge 显示 excerpt tooltip；`verified: false` 的显示警告图标
  - 每个 input 失焦时 auto-save (PUT 单字段)
  - 底部 "+ 添加字段" 按钮、"重新分析" 按钮

#### Task B4: 样式
- 文件: `src/main/resources/static/styles.css`
- 新增 `.ai-analysis-modal`, `.analysis-field-row`, `.source-badge`, `.excerpt-tooltip` 等样式

### Phase C: 测试

#### Task C1: 后端单测
- 文件: `src/test/kotlin/.../document/service/ExpertDocumentAnalysisServiceTest.kt`
- 覆盖: prompt 拼装、excerpt 校验逻辑、权限校验、LLM 超时降级

#### Task C2: 文本提取单测
- 文件: `src/test/kotlin/.../document/service/DocumentTextExtractorTest.kt`
- 覆盖: PDF 提取、纯文本读取、不支持类型处理

---

## 变更文件清单

| # | 文件路径 | 操作 |
|---|---------|------|
| 1 | `src/main/resources/db/migration/V59__create_expert_analysis_result.sql` | 新建 |
| 2 | `src/main/kotlin/.../document/domain/ExpertAnalysisResult.kt` | 新建 |
| 3 | `src/main/kotlin/.../document/repository/ExpertAnalysisResultRepository.kt` | 新建 |
| 4 | `src/main/kotlin/.../document/service/DocumentTextExtractor.kt` | 新建 |
| 5 | `src/main/kotlin/.../document/service/ExpertDocumentAnalysisService.kt` | 新建 |
| 6 | `src/main/kotlin/.../document/controller/ExpertDocumentAnalysisController.kt` | 新建 |
| 7 | `src/main/resources/static/app.js` | 修改 |
| 8 | `src/main/resources/static/index.html` | 修改 |
| 9 | `src/main/resources/static/styles.css` | 修改 |
| 10 | `src/test/kotlin/.../document/service/ExpertDocumentAnalysisServiceTest.kt` | 新建 |

---

## 验收标准

- **I-1**: 测试中构造 attachmentId 不属于目标 contactId 的请求 → 400/403 拒绝
- **I-2**: mock LLM client 抛 `ResourceAccessException` → API 返回 500 + `{ "error": "分析超时..." }`；前端显示重试提示；请求不超过配置的 timeout-ms
- **I-3**: LLM 返回的 excerpt 在原文中找不到 → 结果行 `verified=false`；前端该行来源 badge 显示⚠图标
- **I-4**: 保存结果后调 PUT 修改 value → DB 中 `value` 更新，`source_excerpt` 和 `verified` 不变
- **I-5**: documents 为空时按钮不出现；非空时按钮可点击
- **集成场景**: 选择 2 个 PDF 文件 → 提取文本 → LLM 返回含 source 的 JSON → 前端展示分析结果带出处 → 编辑某字段 → 刷新页面 → 已编辑值保持

---

## Prompt 设计参考

```
You are analyzing academic expert documents. Extract structured profile information from the provided files.

RULES:
1. Output valid JSON matching the schema below.
2. For each field, include "excerpt" — the EXACT substring from the source file that supports this value. Do NOT paraphrase.
3. If a field cannot be determined, omit it from the output.

OUTPUT SCHEMA:
{
  "fields": [
    {
      "key": "name",
      "label": "姓名",
      "value": "extracted value",
      "sourceFileId": "att_123",
      "excerpt": "exact substring from source"
    }
  ]
}

EXPECTED KEYS (extract if available):
- name (姓名)
- nationality (国籍)
- email (邮箱)
- phone (电话)
- current_institution (当前单位)
- current_position (当前职位)
- phd_institution (博士院校)
- phd_field (博士专业)
- phd_year (博士毕业年份)
- master_institution (硕士院校)
- research_areas (研究方向)
- publications (代表论文，逗号分隔)
- patents (专利，逗号分隔)
- awards (获奖，逗号分隔)
- h_index (h-index)

FILES:
<FILE name="{{fileName}}" id="att_{{attachmentId}}">
{{extractedText}}
</FILE>
```

---

## 配置说明

复用现有 `talent-introduction.llm.*` 配置。如果后续需要为文档分析指定 DeepSeek 独立 endpoint（与邮件回复用不同模型），可加子配置:

```yaml
talent-introduction:
  llm:
    document-analysis:
      api-url: ${DOC_ANALYSIS_LLM_URL:}
      api-key: ${DOC_ANALYSIS_LLM_KEY:}
      model: ${DOC_ANALYSIS_LLM_MODEL:deepseek-chat}
      timeout-ms: ${DOC_ANALYSIS_TIMEOUT_MS:60000}
```

本期先复用主配置，独立配置作为可选扩展点预留。
