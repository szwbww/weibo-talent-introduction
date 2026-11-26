#!/usr/bin/env python3
"""Read-only thread fetcher & optimistic-locked mailbox draft saver for Qingfei Expert Reply Bot.

Safety boundaries:
- Never calls any send/dispatch endpoint.
- Preserves existing ACTIVE draft attachments, meeting blocks, and pinned recipientEmail.
- Enforces CAS expectedVersion and surfaces 409 conflicts without blind overwrite.
"""

from __future__ import annotations

import argparse
import html
import http.cookiejar
import json
import os
import pathlib
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Dict, List, Optional


URL_PATTERN = re.compile(r"https?://[^\s<>\"']+")
SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[3]


def load_env_file(env_path: Optional[pathlib.Path] = None) -> Dict[str, str]:
    candidates = (
        [env_path]
        if env_path is not None
        else [
            SCRIPT_DIR / ".env.mailbot",
            REPO_ROOT / ".env.mailbot",
        ]
    )
    loaded: Dict[str, str] = {}
    for candidate in candidates:
        if candidate and candidate.is_file():
            for raw_line in candidate.read_text(encoding="utf-8").splitlines():
                line = raw_line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, value = line.split("=", 1)
                key = key.strip()
                value = value.strip().strip("'").strip('"')
                loaded[key] = value
            break
    for key in ("MAILBOT_BASE_URL", "MAILBOT_USERNAME", "MAILBOT_PASSWORD", "MAILBOT_TIMEOUT"):
        if os.environ.get(key):
            loaded[key] = os.environ[key]
    return loaded


def normalize_manual_text(text: str) -> str:
    """Mirror MailContentService.normalizeManualTextLineBreaks."""
    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    out: List[str] = []
    pending_blank = False
    for line in normalized.split("\n"):
        if all(ch in (" ", "\t") for ch in line):
            if out:
                pending_blank = True
        else:
            if out:
                out.append("\n\n" if pending_blank else "\n")
            out.append(line)
            pending_blank = False
    return "".join(out)


def plain_text_to_html(plain: str) -> str:
    """Render manual rich-text HTML matching mailbox-chat.js (<div> + <br><br> between paragraphs)
    so blank lines remain visible in both the rich-text editor and recipient email clients,
    while keeping bare URLs clickable as raw URLs."""
    cleaned = normalize_manual_text(plain)
    if not cleaned.strip():
        return ""
    paragraphs = re.split(r"\n\s*\n", cleaned)
    rendered_paragraphs: List[str] = []
    for paragraph in paragraphs:
        escaped = html.escape(paragraph.strip(), quote=True).replace("\n", "<br>")
        urls = sorted(set(URL_PATTERN.findall(paragraph)), key=len, reverse=True)
        for url in urls:
            escaped_url = html.escape(url, quote=True)
            escaped = escaped.replace(escaped_url, f'<a href="{escaped_url}">{escaped_url}</a>')
        rendered_paragraphs.append(escaped)
    return f"<div>{'<br><br>'.join(rendered_paragraphs)}</div>"


def _pick(source: Optional[Dict[str, Any]], keys: List[str]) -> Dict[str, Any]:
    if not isinstance(source, dict):
        return {}
    return {k: source[k] for k in keys if k in source and source[k] is not None}


def sanitize_context(raw_ctx: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    """Keep only fields accepted by MailReplyDraftContextCodec (FAIL_ON_UNKNOWN_PROPERTIES=true)."""
    ctx = raw_ctx if isinstance(raw_ctx, dict) else {}
    raw_att = ctx.get("outboundAttachmentDraft")
    att_revision = 0
    att_items: List[Dict[str, Any]] = []
    if isinstance(raw_att, dict):
        att_revision = int(raw_att.get("revision") or 0)
        for item in raw_att.get("items") or []:
            if not isinstance(item, dict):
                continue
            state = str(item.get("state") or "failed")
            if state == "uploading":
                state = "failed"
            att_items.append(
                {
                    "key": str(item.get("key") or ""),
                    "state": state,
                    "id": None if item.get("id") is None else str(item["id"]),
                    "filename": str(item.get("filename") or ""),
                    "contentType": str(item.get("contentType") or ""),
                    "byteLength": int(item.get("byteLength") or 0),
                    "sha256": str(item.get("sha256") or ""),
                    "error": (
                        "上传未完成，请重新选择文件"
                        if item.get("state") == "uploading"
                        else item.get("error")
                    ),
                }
            )

    raw_meeting = ctx.get("meeting")
    saved_meeting: Optional[Dict[str, Any]] = None
    if isinstance(raw_meeting, dict):
        saved_meeting = _pick(raw_meeting, ["blockHtml", "blockText", "state", "revision"])
        saved_meeting["input"] = (
            _pick(
                raw_meeting.get("input"),
                ["zoneId", "startLocal", "endLocal", "zoomUrl", "generatedAt"],
            )
            if isinstance(raw_meeting.get("input"), dict)
            else None
        )
        if isinstance(raw_meeting.get("preview"), dict):
            preview_src = raw_meeting["preview"]
            preview = _pick(
                preview_src,
                [
                    "htmlBody",
                    "textBody",
                    "startUtc",
                    "endUtc",
                    "meetingTime",
                    "chinaTime",
                    "durationMinutes",
                ],
            )
            preview["attachment"] = (
                _pick(
                    preview_src.get("attachment"),
                    [
                        "filename",
                        "contentType",
                        "icsText",
                        "byteLength",
                        "sha256",
                        "semanticSha256",
                    ],
                )
                if isinstance(preview_src.get("attachment"), dict)
                else None
            )
            saved_meeting["preview"] = preview
        else:
            saved_meeting["preview"] = None

    anchor = ctx.get("followUpAnchorMailRecordId")
    return {
        "schemaVersion": 1,
        "recipientEmail": ctx.get("recipientEmail"),
        # Manual bot drafts are freeform text/HTML; clear stale QA binding to prevent send mismatch.
        "qa": None,
        "requestId": ctx.get("requestId"),
        "meeting": saved_meeting,
        "meetingAccountCode": ctx.get("meetingAccountCode") or "",
        "followUpAnchorMailRecordId": int(anchor) if anchor is not None else None,
        "outboundAttachmentDraft": {
            "revision": att_revision,
            "items": att_items,
        },
    }


def infer_draft_target(conversation: Dict[str, Any], messages: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Match mailbox-chat.js target selection (lines 6250-6265) so UI opens the saved draft."""
    contact_id = int(conversation["contactId"])
    latest_inbound = conversation.get("latestInbound")
    if not latest_inbound:
        newest_inbound = None
        for msg in messages:
            if msg.get("source") == "INBOUND_PROCESSING":
                if newest_inbound is None or str(msg.get("eventAt", "")) > str(newest_inbound.get("eventAt", "")):
                    newest_inbound = msg
        if newest_inbound is not None:
            latest_inbound = {
                "processingId": int(newest_inbound["id"]),
                "accountCode": str(newest_inbound.get("accountCode") or ""),
            }

    if isinstance(latest_inbound, dict) and latest_inbound.get("processingId") is not None:
        return {
            "contactId": contact_id,
            "kind": "INBOUND",
            "processingId": int(latest_inbound["processingId"]),
            "accountScope": str(latest_inbound.get("accountCode") or ""),
        }

    if int(conversation.get("sentCount") or 0) > 0:
        return {
            "contactId": contact_id,
            "kind": "OUTBOUND",
            "processingId": 0,
            "accountScope": "",
        }

    raise ValueError(f"联系人 {contact_id} 既无入站记录也无已发出站记录，暂不支持创建回复草稿")


def build_save_payload(
    current_detail: Optional[Dict[str, Any]],
    subject: str,
    plain_text: str,
) -> Dict[str, Any]:
    detail = current_detail or {}
    version = int(detail.get("version") or 0)
    state = detail.get("state")
    reopen = state in ("SENT", "DISCARDED")
    raw_content = detail.get("content") if state == "ACTIVE" else None
    raw_ctx = raw_content.get("context") if isinstance(raw_content, dict) else None

    normalized_text = normalize_manual_text(plain_text)
    rendered_html = plain_text_to_html(normalized_text)
    clean_subject = subject.strip()
    if not clean_subject:
        clean_subject = (
            str(raw_content.get("subject") or "").strip()
            if isinstance(raw_content, dict)
            else ""
        ) or "Re:"

    return {
        "expectedVersion": version,
        "reopen": reopen,
        "content": {
            "subject": clean_subject,
            "html": rendered_html,
            "text": normalized_text,
            "context": sanitize_context(raw_ctx),
        },
    }


def select_best_conversation(items: List[Dict[str, Any]], query: str) -> Dict[str, Any]:
    if not items:
        raise LookupError(f"未找到匹配 '{query}' 的专家会话")
    needle = query.strip().lower()
    for item in items:
        primary = str(item.get("email") or "").strip().lower()
        all_emails = [str(e).strip().lower() for e in (item.get("emails") or [])]
        if needle == primary or needle in all_emails:
            return item
    for item in items:
        if needle == str(item.get("name") or "").strip().lower():
            return item
    return items[0]


def draft_target_query(target: Dict[str, Any]) -> str:
    params = urllib.parse.urlencode(
        {
            "contactId": int(target["contactId"]),
            "kind": str(target["kind"]),
            "processingId": int(target.get("processingId") or 0),
            "accountScope": str(target.get("accountScope") or ""),
        }
    )
    return f"/api/mail/mailbox/drafts/target?{params}"


class MailboxApiClient:
    def __init__(self, base_url: str, username: str, password: str, timeout: int = 20):
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.timeout = timeout
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))

    def request_json(self, method: str, path: str, body: Optional[Dict[str, Any]] = None) -> Any:
        url = f"{self.base_url}{path}"
        data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
        headers = {"Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json; charset=utf-8"
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with self.opener.open(req, timeout=self.timeout) as resp:
                raw = resp.read().decode("utf-8")
                return json.loads(raw) if raw.strip() else None
        except urllib.error.HTTPError as exc:
            err_body = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {exc.code} {method} {path}: {err_body}") from exc

    def login(self) -> Dict[str, Any]:
        return self.request_json(
            "POST",
            "/api/auth/login",
            {"username": self.username, "password": self.password},
        )


def _require_client(env_file: Optional[str]) -> MailboxApiClient:
    cfg = load_env_file(pathlib.Path(env_file) if env_file else None)
    base_url = cfg.get("MAILBOT_BASE_URL", "").strip()
    username = cfg.get("MAILBOT_USERNAME", "").strip()
    password = cfg.get("MAILBOT_PASSWORD", "").strip()
    timeout = int(cfg.get("MAILBOT_TIMEOUT", "20"))
    if not base_url or not username or not password:
        raise SystemExit(
            "缺少后台连接配置。请复制 "
            f"{SCRIPT_DIR / '.env.mailbot.example'} 为 {SCRIPT_DIR / '.env.mailbot'} "
            "并填写 MAILBOT_BASE_URL、MAILBOT_USERNAME、MAILBOT_PASSWORD。"
        )
    client = MailboxApiClient(base_url=base_url, username=username, password=password, timeout=timeout)
    client.login()
    return client


def cmd_fetch_thread(args: argparse.Namespace) -> None:
    client = _require_client(args.env_file)
    encoded_q = urllib.parse.urlencode({"q": args.query.strip(), "page": 0, "size": 20})
    conv_resp = client.request_json("GET", f"/api/mail/mailbox/conversations?{encoded_q}")
    items = conv_resp.get("items") or []
    chosen = select_best_conversation(items, args.query)
    contact_id = int(chosen["contactId"])

    msg_resp = client.request_json(
        "GET",
        f"/api/mail/mailbox/conversations/{contact_id}/messages?limit={int(args.limit)}",
    )
    messages = msg_resp.get("items") or []
    # Sort chronologically (oldest -> newest) for clear reading
    messages_sorted = sorted(messages, key=lambda m: str(m.get("eventAt") or ""))

    draft_target = None
    existing_draft = None
    try:
        draft_target = infer_draft_target(chosen, messages_sorted)
        existing_draft = client.request_json("GET", draft_target_query(draft_target))
    except Exception as exc:  # noqa: BLE001
        existing_draft = {"warning": str(exc)}

    result = {
        "matchedCandidateCount": len(items),
        "conversation": {
            "contactId": contact_id,
            "name": chosen.get("name"),
            "email": chosen.get("email"),
            "emails": chosen.get("emails") or [],
            "orcid": chosen.get("orcid"),
            "accountCodes": chosen.get("accountCodes") or [],
            "receivedCount": chosen.get("receivedCount"),
            "sentCount": chosen.get("sentCount"),
            "pendingCount": chosen.get("pendingCount"),
            "waitingReply": chosen.get("waitingReply"),
            "operatorStatus": chosen.get("operatorStatus"),
            "latestInbound": chosen.get("latestInbound"),
        },
        "recommendedDraftTarget": draft_target,
        "currentSystemDraft": existing_draft,
        "messages": [
            {
                "id": m.get("id"),
                "source": m.get("source"),
                "direction": m.get("direction"),
                "accountCode": m.get("accountCode"),
                "eventAt": m.get("eventAt"),
                "subject": m.get("subject"),
                "sendStatus": m.get("sendStatus"),
                "processStatus": m.get("processStatus"),
                "inboundAttachmentNames": m.get("firstAttachmentNames") or [],
                "outboundAttachmentNames": [
                    a.get("filename") for a in (m.get("outboundAttachments") or [])
                ],
                "calendarAttachment": (m.get("calendarAttachment") or {}).get("filename"),
                "body": m.get("cleanedBody") or m.get("body") or "",
            }
            for m in messages_sorted
        ],
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))


def cmd_save_draft(args: argparse.Namespace) -> None:
    body_text = pathlib.Path(args.body_file).read_text(encoding="utf-8")
    if not body_text.strip():
        raise SystemExit("草稿正文不能为空")

    target = {
        "contactId": int(args.contact_id),
        "kind": args.kind,
        "processingId": int(args.processing_id),
        "accountScope": args.account_scope.strip(),
    }
    if target["kind"] == "INBOUND" and (target["processingId"] <= 0 or not target["accountScope"]):
        raise SystemExit("INBOUND 草稿必须提供有效的 --processing-id (>0) 与 --account-scope")
    if target["kind"] == "OUTBOUND" and target["processingId"] != 0:
        raise SystemExit("OUTBOUND 草稿的 --processing-id 必须为 0")

    client = _require_client(args.env_file)
    target_path = draft_target_query(target)
    current_detail = client.request_json("GET", target_path)
    payload = build_save_payload(
        current_detail=current_detail,
        subject=args.subject,
        plain_text=body_text,
    )
    saved = client.request_json("PUT", target_path, payload)
    summary = {
        "status": "SAVED",
        "draftId": saved.get("id"),
        "version": saved.get("version"),
        "state": saved.get("state"),
        "target": saved.get("target"),
        "expertName": saved.get("expertName"),
        "expertEmail": saved.get("expertEmail"),
        "subject": (saved.get("content") or {}).get("subject"),
        "preservedAttachmentCount": len(
            (((saved.get("content") or {}).get("context") or {}).get("outboundAttachmentDraft") or {}).get("items")
            or []
        ),
        "updatedAt": saved.get("updatedAt"),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Qingfei Expert Mailbox Thread Reader & Draft Saver")
    parser.add_argument("--env-file", help="Optional path to .env.mailbot")
    sub = parser.add_subparsers(dest="command", required=True)

    p_fetch = sub.add_parser("fetch-thread", help="Read expert conversation thread & current draft state")
    p_fetch.add_argument("--query", required=True, help="Expert email or full name")
    p_fetch.add_argument("--limit", type=int, default=50, help="Max messages to load (default: 50)")
    p_fetch.set_defaults(func=cmd_fetch_thread)

    p_save = sub.add_parser("save-draft", help="Save reply draft to mailbox after explicit user confirmation")
    p_save.add_argument("--contact-id", type=int, required=True, help="Target contactId")
    p_save.add_argument("--kind", choices=["INBOUND", "OUTBOUND"], required=True, help="Draft target kind")
    p_save.add_argument("--processing-id", type=int, default=0, help="inbound_mail_processing.id (0 for OUTBOUND)")
    p_save.add_argument("--account-scope", default="", help="Sender accountCode (required for INBOUND)")
    p_save.add_argument("--subject", required=True, help="Draft email subject")
    p_save.add_argument("--body-file", required=True, help="Path to plain-text draft file")
    p_save.set_defaults(func=cmd_save_draft)
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
