# 会议视频（04）1 GiB 链路验证与回滚手册

适用版本：包含 `V154__create_expert_meeting_video.sql` 与
`ExpertMeetingVideoService`/`ExpertMeetingVideoController`（子计划 04）的构建。
本文只讲运维/发布动作：怎么核对、怎么记录、怎么回滚、怎么处理失败与孤儿文件；
业务契约以 `docs/plans/2026-10-11/contact-profile-04-video-api.md` 为准。

> 纪律：**不得上传真实敏感会议视频做测试**。验收文件用合成的合法 MP4
> （对小 MP4 追加 ISO-BMFF `free` box 补齐到目标字节数，先确认仍能播放），
> 并记录本地 SHA-256。1 GiB 档必须在磁盘预留 ≥ 3 GiB 的环境执行。

## 1. 发布前检查（只读）

```bash
# 1. 迁移版本与历史（应停在 V154，且没有 failed 行）
mysql -h"$DB_HOST" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" \
  -e "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

# 2. 新表结构（10 列 + 唯一索引 + 列表索引，无外键）
mysql -h"$DB_HOST" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" \
  -e "SHOW CREATE TABLE expert_meeting_video\G"

# 3. 存储根目录与剩余空间（根 = mail-attachment-storage.base-path/meeting-videos）
df -h /opt/talent/uploads/mail-attachments
ls -la /opt/talent/uploads/mail-attachments/meeting-videos 2>/dev/null | head
# 4. 旧上传限额未被改动（multipart 仍是 100MB/101MB；手动材料 100 MiB、回复附件 10 MiB）
grep -n -A3 "multipart:" src/main/resources/application.yml
```

期望：`V154` 成功；`expert_meeting_video` 为 InnoDB/utf8mb4，`id` 为 ascii CHAR(36) 主键，
唯一索引 `uk_expert_meeting_video_name(expert_key, display_filename)`、
列表索引 `idx_expert_meeting_video_list(expert_key, uploaded_at, id)`，外键 0 条；
`multipart` 仍是 `max-file-size: 100MB` / `max-request-size: 101MB`。

视频接口是**原始字节流**（`Content-Type: application/octet-stream`），不经过 multipart
解析器，因此 1 GiB 视频不受上述 100MB 限制影响；旧手动材料与回复附件的限额也不放宽。

## 2. 上传前基线（每条上传前记录，便于事后归因）

```bash
df -k /opt/talent/uploads/mail-attachments | tail -1            # 可用空间（KiB）
ps -o rss= -p "$(pgrep -f 'weibo-talent-introduction' | head -1)"   # 应用 RSS（KiB）
mysql -h"$DB_HOST" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" \
  -e "SELECT COUNT(*) AS rows_total FROM expert_meeting_video;
      SELECT expert_key, COUNT(*) FROM expert_meeting_video GROUP BY expert_key;"
find /opt/talent/uploads/mail-attachments/meeting-videos -name '.tmp-*' | wc -l   # 临时文件必须为 0
```

## 3. 验收步骤（浏览器或脚本均可；必须带登录会话 Cookie）

1. **1 GiB 上传**：`nameStem=企业对接会`，`expectedSize=1073741824`，
   `Content-Type: application/octet-stream`（原始字节，不是 FormData）。
2. `GET /api/experts/meeting-videos?expertId=<id>&page=0&size=20` → 该条 `byteLength=1073741824`。
3. 下载并核对 SHA-256 与本文件一致；预览应返回 `Accept-Ranges: bytes`。
4. **拖动（Range）**：`Range: bytes=0-1023` → `206` + `Content-Range: bytes 0-1023/1073741824`；
   末尾 `bytes=-16` → `206`；`bytes=1073741824-` → `416` + `Content-Range: bytes */1073741824`。
5. **超限**：`expectedSize=1073741825` 或真实 1 GiB+1 字节 → `413 VIDEO_TOO_LARGE`，
   列表不得新增条目，磁盘不得新增文件。
6. **取消/断流**：上传中途断网 → 列表里**看不到**该条（未提交即无记录）；
   若列表里能看到 1 条，说明服务端已提交成功，**不要自动重传**，先核对列表与下载。

curl 参考（会话 Cookie 由浏览器登录后取得）：

```bash
curl -i -X POST "https://<host>/api/experts/meeting-videos?expertId=<id>&level=RAW&originalName=%E4%BC%81%E4%B8%9A%E5%AF%B9%E6%8E%A5%E4%BC%9A.mp4&nameStem=%E4%BC%81%E4%B8%9A%E5%AF%B9%E6%8E%A5%E4%BC%9A&expectedSize=1073741824" \
  -H "Content-Type: application/octet-stream" -H "Cookie: JSESSIONID=<sid>" --data-binary @企业对接会.mp4
curl -i "https://<host>/api/experts/meeting-videos/<videoId>/preview?expertId=<id>" \
  -H "Range: bytes=0-1023" -H "Cookie: JSESSIONID=<sid>"
```

## 4. 结果记录（发布验收必须逐项填写，未执行写 NOT RUN）

| 项目 | 命令 / 观测点 | 实测 | 结论 |
|---|---|---|---|
| 上传时长与速度 | 步骤 3.1 前后时间差与日志 | | |
| 可用磁盘 前→后 | 第 2 节 `df -k`（1 GiB 上传后应减少约文件大小） | | |
| 应用 RSS 前→后 | 第 2 节 `ps -o rss=`（不得随文件大小线性增长） | | |
| DB 行数 前→后 | 第 2 节 `COUNT(*)`（应 +1） | | |
| 失败文件清理 | 步骤 3.5/3.6 后 `find ... -name '.tmp-*'`（应为 0）+ 目录文件数 | | |
| Range 头 | 步骤 3.4 的 `Content-Range`/状态码 | | |
| 旧上传限额 | 100 MiB 手动材料、10 MiB 回复附件仍被拒 | | |
| 1 GiB+1 | `413`（或容器断连且列表无记录），列表无新增 | | |

本机（开发环境）自动化大文件档观测值（`-DvideoLargeIt=true`，真实 Tomcat 9 + 生成式字节流，
fixed-length/chunked 两种模式，**开发机 loopback，不是生产验收**）：

| 档位 | 观测值 |
|---|---|
| 100 MiB+1（fixed-length） | 201，磁盘 104857601 字节；169 ms |
| 1 GiB（chunked） | 201，磁盘 1073741824 字节；1465 ms（约 698 MiB/s）；JVM used heap 149 MiB → 162 MiB（+13 MiB，未整段驻留内存） |
| 1 GiB+1（声明超限 / 真实流超限） | 413 VIDEO_TOO_LARGE，或服务端按计划提前断连（未读到响应）；两者都不落库、不留文件 |
| Range | `bytes=0-1023`→206+`bytes 0-1023/1073741824`；`bytes=536870912-536870931`→206；`bytes=-16`→206；`bytes=1073741824-`→416+`bytes */1073741824`；分片字节与磁盘同区间逐字节相等 |

生产验收必须在生产/测试环境按第 4 节重新填写（磁盘、RSS、DB 行数、真实网络速率）。

## 5. 失败与未知结果的处理

- **提交前的失败**（校验/容量/额度/断流）：无数据库行、临时文件已删除，客户端可**从头重试**。
- **提交后的断连**：列表里能看到成功条目即已提交；看不到就是没提交。前端不得把
  “请求未返回”当成成功或失败，页面必须提示“请检查列表”。
- **413/507/429**：分别为超限、空间不足、同实例并发占用；409（同名/旧版本）需要用户改名或刷新。
- **服务端硬崩溃**：只可能留下 `.tmp-<UUID>` 或已移动但未落库的 `<UUID>` 孤儿文件，
  见下一节核对方法。**不承诺自动清理**。

## 6. 孤儿文件核对（禁止盲删）

```bash
# 6.1 列出存储目录里的文件
ls -l /opt/talent/uploads/mail-attachments/meeting-videos

# 6.2 与元数据逐一对账：目录里有文件但表里没有对应 id 的才是孤儿
mysql -h"$DB_HOST" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" \
  -e "SELECT id FROM expert_meeting_video;" > /tmp/video_ids.txt
for f in /opt/talent/uploads/mail-attachments/meeting-videos/*; do
  id=$(basename "$f")
  grep -qx "$id" /tmp/video_ids.txt || echo "ORPHAN: $f"
done
```

处理规则：

1. 先确认**没有正在进行的上传**（同一实例最多 2 个并发上传；正常处理期只会出现 `.tmp-*`）。
2. `.tmp-*` 且创建时间早于本次发布维护窗口 → 可删除。
3. 无 `.tmp-` 前缀的 `<UUID>` 且表中无该 id：人工确认后再删除；**禁止**批量 `find -delete`。
4. 表中有 id 但目录缺文件：**不要删行**，先按第 4 节排查（磁盘满/人为清理），
   需要时通知研发；播放会返回 404/500 固定文案，不泄露路径。

## 7. 回滚

1. **前端**：恢复上一版静态资源（`index.html`/`app.js`/`styles.css` 与同组缓存键一起回退）。
2. **后端应用**：停止接收新上传（入口切回上一版 WAR）。
3. **数据库**：Flyway 前向兼容，**不回滚、不 DROP `expert_meeting_video`**，不删除
   `/opt/talent/uploads/mail-attachments/meeting-videos` 下的用户文件（用户数据保留）。
4. **上传中的请求**：先等其完成或中断，再重启；跨重启不承诺续传，用户可检查列表后从头重传。
5. 回滚后再次上线时，`V154` 已执行过则跳过，schema 与数据保持。

## 8. 本手册覆盖不到（明确范围外）

分片/断点续传、转码、封面、对象存储、CDN、视频删除、定时清理平台、企业附件；
编解码兼容性由浏览器决定，服务端只保证字节完整与 Range 语义，不声称验证了可播放性。

Range 行为全部交给 Spring 5.3 的 Resource/ResourceRegion 转换器（不自制解析器）：
合法单段 206，起点越界 416。5.3 的 416 分支仍会写出资源体，因此客户端看到 416 后应立刻停止读取；
大文件下这可能浪费一次连接带宽，属框架行为，不通过自制范围解析器绕过。
