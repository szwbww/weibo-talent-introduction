"""Read-only source audit. Run from repository root; outputs only this evidence dir."""
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path.cwd()
OUT = Path(__file__).resolve().parent
BASE = "src/main/kotlin/com/weibo/talentintroduction/"
SECTIONS = {
    "generator": (BASE + "mail/service/MeetingConfirmationService.kt", [(42, 191), (330, 437), (529, 570), (586, 688)]),
    "models": (BASE + "mail/service/MeetingConfirmationModels.kt", [(15, 90), (119, 200)]),
    "controller": (BASE + "mail/controller/MeetingConfirmationController.kt", [(1, 65)]),
    "country-source": (BASE + "mail/service/ExpertContactLocationCatalog.kt", [(1, 80)]),
    "send-rebuild": (BASE + "mail/service/PendingMailOperationService.kt", [(584, 640), (688, 698), (728, 740)]),
    "save": (BASE + "mail/service/ManualReplySendAttemptService.kt", [(155, 174), (340, 424), (432, 500)]),
    "timeline": (BASE + "mail/service/MailboxConversationService.kt", [(391, 412)]),
    "download": (BASE + "mail/controller/CalendarAttachmentController.kt", [(40, 96)]),
    "smtp": (BASE + "mail/service/SmtpMailDeliveryService.kt", [(95, 119)]),
    "calendar": (BASE + "campaign/service/MeetingCalendarService.kt", [(48, 102)]),
    "frontend": ("src/main/resources/static/meeting-confirmation.js", [(104, 123), (258, 294), (403, 497), (524, 610), (792, 957), (1099, 1116), (1290, 1328), (1375, 1420)]),
    "host": ("src/main/resources/static/mailbox-chat.js", [(3834, 3858), (7134, 7160), (7418, 7448)]),
    "styles": ("src/main/resources/static/meeting-confirmation.css", [(1, 106)]),
    "shared-clock": ("src/main/resources/static/world-clock.js", [(1, 14)]),
    "generator-script": ("scripts/generate_meeting_timezone_catalog.py", [(85, 189)]),
    "schema-calendar": ("src/main/resources/db/migration/V123__add_mail_record_calendar_attachment.sql", [(1, 20)]),
    "schema-schedule": ("src/main/resources/db/migration/V125__create_meeting_calendar_event.sql", [(1, 30)]),
}
lines = ["# Code baseline", "", "HEAD: " + subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(), "", "Line numbers refer to this snapshot; implementation must recheck working-tree changes.", ""]
hashes = {}
for title, (path, ranges) in SECTIONS.items():
    p = ROOT / path
    raw = p.read_bytes()
    hashes[path] = hashlib.sha256(raw).hexdigest()
    source = raw.decode().splitlines()
    lines += [f"## {title}: {path}", ""]
    for start, end in ranges:
        lines += ["```text"] + [f"{i}: {source[i-1]}" for i in range(start, min(end, len(source)) + 1)] + ["```", ""]
(OUT / "code-baseline.md").write_text("\n".join(lines))
(OUT / "source-hashes.json").write_text(json.dumps(hashes, indent=2) + "\n")

commands = [
    ["rg", "-n", "calendarAttachmentJson|calendar_attachment_json", "src/main"],
    ["rg", "-n", "calendarAttachment|snapshot.filename|calendar.filename", BASE + "mail/service/SmtpMailDeliveryService.kt", BASE + "mail/controller/CalendarAttachmentController.kt", BASE + "mail/service/MailboxConversationService.kt"],
    ["rg", "-n", "time-zones|timeZones\\(|MeetingTimeZoneOption|filterZones", "src/main", "src/test"],
    ["rg", "-n", "MeetingConfirmationService\\(", "src/main", "src/test"],
    ["rg", "-n", "buildCalendarFilename|semanticSha256|zoneLabel", BASE + "mail/service/MeetingConfirmationService.kt"],
    ["rg", "-n", "meeting_calendar_event|repository\\.|Repository\\.", BASE + "campaign/service/MeetingCalendarService.kt", BASE + "campaign/repository/MeetingCalendarEventRepository.kt"],
    ["rg", "-n", "fun |repository\\.", BASE + "campaign/service/MeetingCalendarService.kt"],
    ["rg", "-n", "contact-country-timezones|meeting-timezones-zh.properties", "src/main", "scripts"],
    ["rg", "-n", "meeting-zone-field|meeting-zone-control|meeting-zone-options|meeting-form", "src/main/resources/static", "src/test/js/meetingConfirmationStyle.test.js"],
    ["rg", "-n", "filename|Türkiye Time|China Standard Time|Kolkata Time", "src/test/kotlin/com/weibo/talentintroduction/mail/service/MeetingConfirmationServiceTest.kt"],
    ["rg", "-n", "\\?v=", "src/main/resources/static/index.html"],
]
html = (ROOT / "src/main/resources/static/index.html").read_text()
key = re.search(r'styles.css\?v=([^"\s]+)', html)[1]
commands.append(["rg", "-n", "-F", key, "src/test"])
receipts = ["# Grep receipts", "", "exit=1 means no matches; not command failure.", ""]
for args in commands:
    r = subprocess.run(args, text=True, capture_output=True)
    receipts += ["```sh", " ".join(args), "```", f"exit={r.returncode}", "```text", r.stdout.rstrip(), r.stderr.rstrip(), "```", ""]
(OUT / "grep-receipts.md").write_text("\n".join(receipts))
print(json.dumps({"source_files": len(hashes), "grep_commands": len(commands), "asset_key": key}))
