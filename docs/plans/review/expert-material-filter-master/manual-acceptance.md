# Manual Acceptance — expert-material-filter-master

## Epoch 1 — 2026-11-05 (Asia/Shanghai)

- Reviewed code boundary: 71bb4879c77c9fcb69f1688e7192ad9aea5c0c4b..e73f902a06e45f42722e39682e20a8742ba9d388
- Machine report epoch: 1; machine-verification.md; PASS
- Status: PENDING
- Governing master SHA-256: 344158c4a2d30f05e7141544a7d4de64d3b33435752f322651ca861ec0f801fd
- 来源：主计划人工验收入口及其明确要求的子计划 A-n。仅转录批准项，不增加产品要求。
- 全部结果待人类提供；机器测试不替代人工验收。结果以追加记录保存，不改写本初始清单。

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| MASTER-A-1 | Yes | 截图场景与历史安全 | 材料 2、stored=2、metadataOnly=0；三份签名不显示；混选操作 400、有效项不被部分入队；原始行/审核状态/哈希不变。历史持久状态不因刷新改变。 | PENDING | — | — | — |
| MASTER-A-2 | Yes | 新收信与普通材料回归 | 材料数依次 0、1、2、3；第一封不推进 MATERIALS_RECEIVED，第二封保留既有推进；正常文件可下载；重复检查不重复创建或回复。 | PENDING | — | — | — |
| MASTER-A-3 | Yes | 绑定、旧查询、对账 | 只补普通材料一份；仅签名的 hasAttachment/attachmentIntentIgnored 为 false；对账 expected=REPLIED、dbStatus=MATERIALS_RECEIVED，DB/ES 没有自动回退。 | PENDING | — | — | — |
| 01-A-1 | Yes | 历史 5→2 与分页 | 材料总数 2、stored=2、metadataOnly=0；搜索返回 total=0，但 summary.total=2；两页各 1 项，仅 DOCX/PDF；会话材料按钮为 2。无需执行历史清理。 | PENDING | — | — | — |
| 01-A-2 | Yes | 普通图片、manual/processing 及旧入口 | 三个接口的材料/文档集合保留四个正常 ID，排除 p7s；详情 attachments 保持原来 record-owner 范围并排除签名，不额外混入 processing/manual；0 字节下载长度=0，图片哈希不变。 | PENDING | — | — | — |
| 01-A-3 | Yes | 历史选择绕过 | 均 400/BAD_REQUEST，提示“所选文件包含邮件数字签名，不属于专家材料”；没有 ZIP/图片/PDF/AI 结果输出；普通 ID transfer 状态不因失败请求改变。正常未就绪 PDF 单独下载仍 409。 | PENDING | — | — | — |
| 01-A-4 | Yes | 来源回退和只读 | 第一个 processing 附件列表=[]、attachmentCount=0，不出现 bridge 普通附件；歧义来源仍为空；没有业务 DML、IMAP FETCH，原件哈希不变。 | PENDING | — | — | — |
| 01-A-5 | Yes | 显式历史补建 | dryRun 仅有 PDF ID；第一次 createdCount=1，第二次=0；签名原始 attachment 仍在但未建 document，材料列表只有 PDF；不改变 owner、审核状态或其他联系人。 | PENDING | — | — | — |
| 02-A-1 | Yes | 仅签名与签名+简历 | 第一封材料0、不推进 MATERIALS_RECEIVED；第二封材料1，既有材料推进保留，下载文件哈希与输入一致；邮件正文可读，无二进制签名文本。 | PENDING | — | — | — |
| 02-A-2 | Yes | 关闭与人工分支 | 仅签名均为0材料，运营状态不因附件推进；图片+签名各1份；关闭/人工设置不被开启，不产生自动发送。 | PENDING | — | — | — |
| 02-A-3 | Yes | 事务桥接与重复收取 | 混合信只登记/桥接PDF1份，不因签名缺 transfer 失败，第二次无重复；故意缺 PDF transfer 时操作失败且不确认该信、不推进其成功游标，重试成功后仍1份。 | PENDING | — | — | — |
| 02-A-4 | Yes | 待匹配与无首信 | 新邮件各只登记PDF；旧 p7s 原行保留但不补 document；每组普通PDF最多1份；来源归属不移动、审核PENDING_REVIEW不因过滤被批准。 | PENDING | — | — | — |
| 02-A-5 | Yes | 手动上传 | 前两次400/BAD_REQUEST、“邮件数字签名不属于专家材料”，无新增数据库行和原件；后两次201/STORED/PENDING_REVIEW，材料增加2、图片哈希一致、文本0字节；运营状态不被上传修改。 | PENDING | — | — | — |
| 02-A-6 | Yes | DMARC 与 Drive | DMARC 仍走报表处理，purpose=DMARC、attachment_id为空，不产生专家材料；Drive 信只登记1个外链材料，partPath 为原 gdrive: 前缀；没有把链接提前交给 DMARC 解析。 | PENDING | — | — | — |
| 02-A-7 | Yes | 来源定位与正文 | 两模式正文保留、签名不出现在正文/材料；PDF仍按partPath=3读取正确内容；签名流不读取的证据来自对应协议IT；UID/账号/来源未变。 | PENDING | — | — | — |
| 03-A-1 | Yes | 旧邮箱与任务列表 | 第一封 hasAttachment=false、详情附件0；第二封 true、详情仅PDF1份；各入口数量标记一致，邮件本身仍在，账号筛选不会串出另一专家的文件。 | PENDING | — | — | — |
| 03-A-2 | Yes | 自动回复预览 | 布尔值分别false/true/false；同正文分类/动作保持一致；没有新增已发邮件、transfer或材料文档。 | PENDING | — | — | — |
| 03-A-3 | Yes | 历史状态差异不自动回退 | 样本 expectedStatus=REPLIED、dbStatus=MATERIALS_RECEIVED、category=DB_VS_EXPECTED；两次报告一致；数据库/ES原状态不变，“已回复材料”持久标签可以保留；材料页始终0份。 | PENDING | — | — | — |
| 03-A-4 | Yes | 人工和高里程碑保护 | ①humanOverride计数增加且不进dbVsExpected；②expected=INVITED；③不因expected比较计入DB异常；④expected=MATERIALS_RECEIVED；四人DB/ES均不因本次对账改变。 | PENDING | — | — | — |

## 批准计划原始步骤

### MASTER-A-1：截图场景与历史安全

来源：[expert-material-filter-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-master.md)

- 前置条件：在隔离测试环境、旧版本中，用测试邮箱发送三封各含一个 `smime.p7s` 的来信，其中一封附 DOCX；检查回复仅登记元数据，手动上传一个 PDF，再获取 DOCX，记录附件 ID、文档审核状态和原件哈希。此时 5 份、stored=2、metadataOnly=3。不得把截图姓名当唯一身份，使用测试 contactId。
- 操作步骤：1. 完成三子计划并部署测试环境；2. 重新打开专家材料与旧文档列表；3. 刷新会话；4. 用记录的旧签名 ID 调用获取/ZIP/下载；5. 比较升级前后的原始行和原件哈希。
- 预期结果：材料 2、stored=2、metadataOnly=0；三份签名不显示；混选操作 400、有效项不被部分入队；原始行/审核状态/哈希不变。历史持久状态不因刷新改变。
- 覆盖：O-1/O-2、N-1/N-2/N-4、I-1/I-2/I-4、IP-1/IP-2。

### MASTER-A-2：新收信与普通材料回归

来源：[expert-material-filter-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-master.md)

- 前置条件：隔离测试邮箱与已发首信的测试专家，运营状态 REPLIED；自动发送关闭以免验收发出真实自动邮件。
- 操作步骤：1. 发正文 `fixture-neutral-01`、只附 `smime.p7s` 的邮件并检查回复；2. 再发签名+`cv.pdf`；3. 再发 `certificate.png`；4. 手动上传 `scan.jpg`；5. 按 02 A-6/A-7 验证 DMARC/Drive/去重。
- 预期结果：材料数依次 0、1、2、3；第一封不推进 MATERIALS_RECEIVED，第二封保留既有推进；正常文件可下载；重复检查不重复创建或回复。
- 覆盖：O-2/O-3、N-1/N-3、I-3/I-6、IP-4/IP-5/IP-8。

### MASTER-A-3：绑定、旧查询、对账

来源：[expert-material-filter-master.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-master.md)

- 前置条件：按 01 A-5/02 A-4 准备未匹配 processing-owner 普通材料+签名；按 03 A-3 准备仅签名但历史状态为 MATERIALS_RECEIVED 的测试专家，无邀请、无人工覆盖。
- 操作步骤：1. 绑定并补建两次；2. 查看旧邮箱、任务邮件列表、自动回复预览；3. 调用只读运营对账；4. 刷新状态。
- 预期结果：只补普通材料一份；仅签名的 hasAttachment/attachmentIntentIgnored 为 false；对账 expected=REPLIED、dbStatus=MATERIALS_RECEIVED，DB/ES 没有自动回退。
- 覆盖：O-3、N-4、I-2/I-5/I-6、IP-3/IP-6/IP-7。

### 01-A-1：历史 5→2 与分页

来源：[expert-material-filter-01-read.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-01-read.md)

- 前置条件：隔离测试环境旧版本中，用测试邮箱三封来信登记三个 `.p7s`；一封带真实 DOCX，获取该 DOCX；手动上传真实 PDF。记录 contactId、五个 attachmentId。材料页显示 5、stored=2、metadataOnly=3。
- 操作步骤：1. 部署 01；2. 关闭重开材料弹窗；3. 刷新会话；4. 搜索 smime.p7s，再清空搜索；5. GET `/api/expert-contacts/{contactId}/materials?page=0&size=1`，再 page=1。
- 预期结果：材料总数 2、stored=2、metadataOnly=0；搜索返回 total=0，但 summary.total=2；两页各 1 项，仅 DOCX/PDF；会话材料按钮为 2。无需执行历史清理。
- 覆盖：O1、I-1/I-2、IP-1。

### 01-A-2：普通图片、manual/processing 及旧入口

来源：[expert-material-filter-01-read.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-01-read.md)

- 前置条件：旧版本中为测试专家手动上传 certificate.png 和 0 字节 notes.txt；另一封待匹配来信包含 scan.jpg，绑定该专家；另有正常 CV.docx 与 smime.p7s。记录四份正常材料的 ID，确认各自来源。
- 操作步骤：1. 部署 01；2. 打开材料页；3. GET `/api/expert-contacts/{contactId}/documents` 和 `/api/expert-contacts/{contactId}`；4. 下载已存图片和 0 字节文件。
- 预期结果：三个接口的材料/文档集合保留四个正常 ID，排除 p7s；详情 attachments 保持原来 record-owner 范围并排除签名，不额外混入 processing/manual；0 字节下载长度=0，图片哈希不变。
- 覆盖：O1、必须保持普通材料、I-1/I-2、IP-1/IP-5。

### 01-A-3：历史选择绕过

来源：[expert-material-filter-01-read.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-01-read.md)

- 前置条件：A-1 历史签名 ID 仍存在；为其中一个签名在旧版本先完成获取，另保留 metadata-only；记录全部原始状态。测试专家可由当前登录用户访问。
- 操作步骤：1. POST `/materials/transfers`，body 为 `{"attachmentIds":[普通ID,签名ID]}`；2. 同集合 POST `/materials/download-zip`；3. 请求 scoped `/attachments/{签名ID}/download` 和 `/preview`；4. 请求 `/api/mail/mailbox/attachments/{签名ID}/download`；5. 在测试 API 客户端POST `/api/expert-contacts/{contactId}/ai-analysis`，body 为同一 attachmentIds 集合。
- 预期结果：均 400/BAD_REQUEST，提示“所选文件包含邮件数字签名，不属于专家材料”；没有 ZIP/图片/PDF/AI 结果输出；普通 ID transfer 状态不因失败请求改变。正常未就绪 PDF 单独下载仍 409。
- 覆盖：O2、保持权限和原件、I-3、IP-2。

### 01-A-4：来源回退和只读

来源：[expert-material-filter-01-read.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-01-read.md)

- 前置条件：仅在隔离测试库：旧版本检查一封未匹配的仅签名信，记录 processing ID=P（其签名直接 owner=P）；另检查已匹配的 PDF 信，记录该 PDF 的 transfer ID=T。用下方“人工 fixture SQL”把该 transfer 桥接到P，并记录更新前值。另向两个测试收信账号投递相同 Message-ID 的测试信以构造 legacy 歧义。
- 操作步骤：1. GET `/api/mail/mailbox/INBOUND_PROCESSING/{id}/attachments`；2. 打开消息列表；3. 对歧义 legacy processing 重复同操作；4. 在数据库审计日志中检查这次 GET 区间，比较原件哈希。
- 预期结果：第一个 processing 附件列表=[]、attachmentCount=0，不出现 bridge 普通附件；歧义来源仍为空；没有业务 DML、IMAP FETCH，原件哈希不变。
- 覆盖：必须保持来源/只读、I-2/I-4、IP-1/IP-2。

### 01-A-5：显式历史补建

来源：[expert-material-filter-01-read.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-01-read.md)

- 前置条件：旧版检查未匹配来信（PDF+p7s），记录 processing ID=P、两附件 ID、可访问测试专家 ID=C；尚未绑定所以无 document。仅在隔离测试库执行下方“人工 fixture SQL”设置 processing 的 expert_contact_id=C，不调用后台绑定，以保留缺文档状态。
- 操作步骤：1. POST `/api/expert-contacts/{contactId}/materials/reconcile`，`{"dryRun":true}`；2. `{"dryRun":false}` 执行两次；3. 查看材料页和原始 attachment 行。
- 预期结果：dryRun 仅有 PDF ID；第一次 createdCount=1，第二次=0；签名原始 attachment 仍在但未建 document，材料列表只有 PDF；不改变 owner、审核状态或其他联系人。
- 覆盖：O2、I-5、IP-3。

### 02-A-1：仅签名与签名+简历

来源：[expert-material-filter-02-ingress.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-02-ingress.md)

- 前置条件：隔离测试邮箱、已发首信的测试专家、operatorStatus=REPLIED；全局自动发送关闭。准备 smime.p7s（可使用合成字节，因为本规则只判 metadata）和正常 CV.pdf。
- 操作步骤：1. 发正文 fixture-neutral-01 + p7s 并检查回复；2. 查看材料页和运营状态；3. 发同类正文但不同 Message-ID 的 p7s+CV.pdf，再检查回复；4. 获取 CV 并下载。
- 预期结果：第一封材料0、不推进 MATERIALS_RECEIVED；第二封材料1，既有材料推进保留，下载文件哈希与输入一致；邮件正文可读，无二进制签名文本。
- 覆盖：O1、正常材料回归、I-1/I-2/I-5、IP-4。

### 02-A-2：关闭与人工分支

来源：[expert-material-filter-02-ingress.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-02-ingress.md)

- 前置条件：三个独立测试专家均已发首信、状态 REPLIED；分别全局关闭、专家关闭、会话 MANUAL_HANDOFF。使用不同 UID/Message-ID 的签名邮件。
- 操作步骤：1. 每位专家收到仅签名邮件；2. 检查回复；3. 查看材料与状态；4. 用正常图片+签名复测。
- 预期结果：仅签名均为0材料，运营状态不因附件推进；图片+签名各1份；关闭/人工设置不被开启，不产生自动发送。
- 覆盖：O1、通信策略保持、I-2/I-5、IP-4。

### 02-A-3：事务桥接与重复收取

来源：[expert-material-filter-02-ingress.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-02-ingress.md)

- 前置条件：测试邮箱收到 metadata-only 模式的签名+PDF；另使用测试 fixture 注入“有效 PDF 已处理但 transfer 登记缺失”的失败场景。所有数据隔离。
- 操作步骤：1. 检查混合信两次；2. 看邮件确认和材料数；3. 用失败 fixture 运行同一检查流程；4. 修复 fixture 后重试。
- 预期结果：混合信只登记/桥接PDF1份，不因签名缺 transfer 失败，第二次无重复；故意缺 PDF transfer 时操作失败且不确认该信、不推进其成功游标，重试成功后仍1份。
- 覆盖：O2、事务/UID保持、I-2/I-3/I-5、IP-4/IP-8。

### 02-A-4：待匹配与无首信

来源：[expert-material-filter-02-ingress.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-02-ingress.md)

- 前置条件：测试邮件来自未匹配邮箱和已知但未发首信的专家，各附签名+普通PDF；另保留旧版本写入的 processing-owner p7s+PDF，尚无 document。
- 操作步骤：1. 新邮件检查回复；2. 在后台绑定未匹配信到测试专家；3. 对旧邮件执行绑定/材料补建两次；4. 查看资料。
- 预期结果：新邮件各只登记PDF；旧 p7s 原行保留但不补 document；每组普通PDF最多1份；来源归属不移动、审核PENDING_REVIEW不因过滤被批准。
- 覆盖：O2、I-3、IP-3/IP-4。

### 02-A-5：手动上传

来源：[expert-material-filter-02-ingress.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-02-ingress.md)

- 前置条件：测试专家可访问；记录上传前材料数量与服务器测试目录文件清单；准备 p7s、MIME 为 signature 的 bin、certificate.png、0B notes.txt。
- 操作步骤：1. UI 上传 p7s；2. API multipart 上传 bin 并指定 signature MIME；3. 上传图片和空文本；4. 下载这两份正常文件。
- 预期结果：前两次400/BAD_REQUEST、“邮件数字签名不属于专家材料”，无新增数据库行和原件；后两次201/STORED/PENDING_REVIEW，材料增加2、图片哈希一致、文本0字节；运营状态不被上传修改。
- 覆盖：O2、正常图片/0B保持、I-4/I-5、IP-5。

### 02-A-6：DMARC 与 Drive

来源：[expert-material-filter-02-ingress.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-02-ingress.md)

- 前置条件：隔离测试环境已有可工作的 DMARC 报表 fixture 和受控 Drive 测试文件链接；账号网络设置按原测试要求准备，不使用真实专家数据。
- 操作步骤：1. 输入报表邮件（合法报表附件+签名）；2. 检查回复；3. 输入普通专家信（签名+受控 Drive 文件链接）；4. 查看材料来源并获取文件。
- 预期结果：DMARC 仍走报表处理，purpose=DMARC、attachment_id为空，不产生专家材料；Drive 信只登记1个外链材料，partPath 为原 gdrive: 前缀；没有把链接提前交给 DMARC 解析。
- 覆盖：N-3、I-2/I-5、IP-8。

### 02-A-7：来源定位与正文

来源：[expert-material-filter-02-ingress.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-02-ingress.md)

- 前置条件：用本地测试邮箱导入合成 MIME：multipart/signed 内包含正文和CV，签名为独立叶；另一个 mixed 邮件原part顺序为正文1、签名2、PDF3。测试 UID 与账号已记录。
- 操作步骤：1. metadataOnly 模式检查回复；2. 获取PDF3；3. 使用隔离的 legacy 模式重复同结构新邮件；4. 对照收件箱原始正文和附件。
- 预期结果：两模式正文保留、签名不出现在正文/材料；PDF仍按partPath=3读取正确内容；签名流不读取的证据来自对应协议IT；UID/账号/来源未变。
- 覆盖：正文/来源保持、I-1/I-2/I-5、IP-4/IP-8。

### 03-A-1：旧邮箱与任务列表

来源：[expert-material-filter-03-projections.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-03-projections.md)

- 前置条件：隔离测试环境旧版登记两封测试专家来信：第一封只有p7s，第二封PDF+p7s；有对应 mail_record 与 processing，关联原测试收信任务。另有其他账号/专家的正常PDF来信。记录查询条件与邮件ID。
- 操作步骤：1. 部署三个子计划；2. 打开旧收发件箱普通列表；3. 按同专家查看；4. 打开对应任务邮件记录；5. 打开两封邮件详情；6. 切换账号筛选。
- 预期结果：第一封 hasAttachment=false、详情附件0；第二封 true、详情仅PDF1份；各入口数量标记一致，邮件本身仍在，账号筛选不会串出另一专家的文件。
- 覆盖：O1、查询范围/普通材料保持、I-1、IP-6。

### 03-A-2：自动回复预览

来源：[expert-material-filter-03-projections.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-03-projections.md)

- 前置条件：旧版创建同正文的三条 processing-owner 测试来信：仅p7s、p7s+PDF、无附件；均在可预览状态，全局发送关闭。
- 操作步骤：1. 分别打开自动回复预览；2. 比较预览响应中的 attachmentIntentIgnored、intentCode、autoAction、previewKind；3. 查看发送记录。
- 预期结果：布尔值分别false/true/false；同正文分类/动作保持一致；没有新增已发邮件、transfer或材料文档。
- 覆盖：O1、预览只读保持、I-2、IP-6。

### 03-A-3：历史状态差异不自动回退

来源：[expert-material-filter-03-projections.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-03-projections.md)

- 前置条件：隔离测试环境旧版的一位专家仅有已确认来信和p7s、无邀请；DB 状态已自动推进 MATERIALS_RECEIVED；不存在 CHANGE_OPERATOR_STATUS 人工覆盖日志。记录 DB/ES 状态。
- 操作步骤：1. 调用 POST `/api/experts/reconcile-operator-status`；2. 查看该专家样本；3. 刷新专家页面；4. 再次对账。
- 预期结果：样本 expectedStatus=REPLIED、dbStatus=MATERIALS_RECEIVED、category=DB_VS_EXPECTED；两次报告一致；数据库/ES原状态不变，“已回复材料”持久标签可以保留；材料页始终0份。
- 覆盖：O2、历史状态保持、I-3、IP-7。

### 03-A-4：人工和高里程碑保护

来源：[expert-material-filter-03-projections.md](/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master/docs/plans/2026-11-05/expert-material-filter-03-projections.md)

- 前置条件：四位隔离测试专家：①仅签名且通过后台人工设置 MATERIALS_RECEIVED（产生审计日志）；②已发邀请成功且仅签名；③仅签名但人工终态COMPLETED；④普通PDF+签名且DB=MATERIALS_RECEIVED。准备原状态快照。
- 操作步骤：1. 调用同一只读对账API；2. 查看四人的分类/期望状态；3. 刷新页面并比对原状态快照。
- 预期结果：①humanOverride计数增加且不进dbVsExpected；②expected=INVITED；③不因expected比较计入DB异常；④expected=MATERIALS_RECEIVED；四人DB/ES均不因本次对账改变。
- 覆盖：O2、人工/终态/普通材料保持、I-3、IP-7。

## Human Sign-off

- Decision: PENDING
- Boundary: e73f902a06e45f42722e39682e20a8742ba9d388
- Accepted governing master identity: PENDING (344158c4a2d30f05e7141544a7d4de64d3b33435752f322651ca861ec0f801fd)
- Reporter: —
- Timestamp: —
- Note: —

全部强制项完成后，由用户逐项给出 PASS/FAIL 与证据或说明，并明确接受上述代码边界及主计划身份。缺项维持 AWAITING_HUMAN_ACCEPTANCE；失败进入 MACHINE_BLOCKED。

## Human Results (append-only)

尚无人类结果。
