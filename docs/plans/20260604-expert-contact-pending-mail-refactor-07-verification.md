# Phase 7：全链路复验和交付清单

> 目标：确认专家联系页和待处理邮件页重构完整满足需求，且没有破坏现有自动收信、手动发信、层级同步和页面脚本。

## 1. 静态检查

```bash
git status --short
rg -n "operatorStatus|operator_status|operator_action_log|OperatorAction|manual-rich-reply|qa-reply|documents" src/main/kotlin src/main/resources/static src/main/resources/db/migration
node --check src/main/resources/static/app.js
```

要求：

- `app.js` 语法检查通过。
- 新迁移命名版本不冲突。
- 没有重复定义同名 DTO/enum。

## 2. 后端测试

默认用 JDK 11：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

如果失败，必须区分：

- 编译失败。
- 单测断言失败。
- 环境问题，例如 githook 写 `.git/hooks` 被拒绝。

不要只写“测试失败”，要贴关键错误。

## 3. 数据库迁移审查

检查所有新增迁移：

```bash
ls src/main/resources/db/migration | sort
sed -n '1,240p' src/main/resources/db/migration/Vxx__add_operator_status_and_action_log.sql
```

验收点：

- SQL 文件是完整迁移，不是局部片段。
- `expert_contact.operator_status` 默认值正确。
- 历史数据回填逻辑不会把已完成误判为完成；`COMPLETED` 只人工进入。
- `operator_action_log` 有必要索引。
- 外键不会引用不存在的表。

## 4. API 手动验证建议

如果本地服务可启动，建议使用浏览器或 curl 验证。

### 4.1 操作日志查询

```http
GET /api/operator-action-logs?pageSize=20&pageOffset=0
```

应该返回：

```json
{
  "records": [],
  "totalCount": 0
}
```

或已有日志。

### 4.2 专家状态变更

```http
POST /api/expert-contacts/{contactId}/operator-status
Content-Type: application/json

{
  "operatorStatus": "REPLIED",
  "operatorName": "verification",
  "note": "verification"
}
```

然后查：

```http
GET /api/operator-action-logs?expertContactId={contactId}&actionType=CHANGE_OPERATOR_STATUS
```

### 4.3 专家层级变更

```http
POST /api/expert-contacts/{contactId}/index-level
Content-Type: application/json

{
  "targetLevel": "APPLICATION",
  "operatorName": "verification",
  "note": "verification"
}
```

如果 ES 未配置导致失败，记录真实错误；不要伪造成功。

### 4.4 待处理邮件标记已处理

```http
POST /api/mail/unmatched-inbound/{id}/mark-resolved
Content-Type: application/json

{
  "operatorName": "verification",
  "note": "verification"
}
```

然后查列表：

```http
GET /api/mail/unmatched-inbound
```

该记录不应再出现。

## 5. 自动收信单测场景

必须确认测试覆盖：

- 第 1 封回复不进有效层。
- 第 2 封回复不进有效层。
- 第 3 封回复进有效层。
- 附件来信进有效层。
- 自动回复暂停时不发送邮件，但保存附件并可晋级。
- 已 `COMPLETED` 的运营状态不被自动覆盖。

如果没有真实 IMAP/SMTP 环境，允许用 mock 单测验证 `AutoMailReplyService.processSingle(...)`。

## 6. 文件浏览安全验证

必须确认测试覆盖：

- 正常下载。
- 正常预览 PDF/image/text。
- Office 文件不可预览但可下载。
- 请求其他专家的附件被拒绝。
- `storagePath` 路径穿越被拒绝。

## 7. 前端人工验收

启动服务后在浏览器验证。

### 7.1 专家联系页面

必须逐项确认：

- 顶部仍有原始、筛选、有效三层。
- 状态筛选只显示：
  - 未联系
  - 已联系
  - 已回复
  - 已回复材料
  - 已邀约
  - 已完成
- 自动回复/人工回复切换按钮仍存在。
- 手动变更专家状态下拉框可用。
- 手动变更专家层级下拉框可用。
- 选择专家后可看到上传资料文件。
- 文件可下载。
- PDF/image/text 可在线浏览。
- 操作日志区域显示状态变更、层级变更、回复模式切换。

### 7.2 待处理邮件页面

必须逐项确认：

- 点击记录能查看未识别邮件内容。
- 已关联专家的记录可以变更专家状态。
- 已关联专家的记录可以变更专家层级。
- QA 邮件回复下拉框有数据。
- 点击发送 QA 邮件能保存发信记录并写日志。
- 富文本框可以输入内容并发送人工回复。
- 标记已处理后列表不再显示该记录。
- 操作日志能看到：
  - 变更专家层级。
  - 发送 QA 邮件。
  - 人工回复邮件。
  - 标记已处理。

## 8. 回归关注点

重点检查不要破坏旧功能：

- 邮箱账号管理页。
- QA 规则页。
- 邮件监控页。
- 专家联系邮件时间线。
- 会议排期功能。
- 自动回复/人工回复切换。
- 未匹配邮件绑定专家。
- `node --check` 不能只跑新增代码，必须跑完整 `app.js`。

## 9. 交付说明模板

实现 agent 完成后请按这个格式汇报：

```text
已完成：
- Phase 1 ...
- Phase 2 ...

关键文件：
- src/main/resources/db/migration/Vxx__...
- src/main/kotlin/...
- src/main/resources/static/app.js

验证：
- node --check src/main/resources/static/app.js: 通过
- JAVA_HOME=... mvn test: 通过

未完成/阻塞：
- 无
```

如果有阻塞，必须写具体错误和复现命令。

## 10. 最终验收标准

最终必须同时满足：

- 专家联系页需求全部满足。
- 待处理邮件页需求全部满足。
- 所有人工操作写日志，并可查询。
- 自动回复次数和附件晋级规则有测试。
- 附件下载/预览有安全校验。
- 没有局部 SQL 交付。
