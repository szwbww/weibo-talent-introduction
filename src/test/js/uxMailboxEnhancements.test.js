"use strict";

const { describe, it } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const shellCss = fs.readFileSync(path.join(ROOT, "console-shell.css"), "utf-8");

describe("Task 3: MailboxChat Resizer, Keyboard Triage, Jump-to-Reply & Pending Card Polish", () => {
    it("renders .mc-resizer inside aside.mc-experts without altering .mail-chat child structure", () => {
        assert.ok(chatSource.includes('class="mc-resizer" data-role="mc-resizer"'), "resizer element must exist inside aside.mc-experts");
        assert.match(shellCss, /\.mc-resizer\b/, "mc-resizer must be declared in console-shell.css");
        assert.match(shellCss, /var\(--mc-left-width,/, "mail-chat grid must consume --mc-left-width variable");
        assert.ok(chatSource.includes("mailbox-chat-left-width"), "left width preference key must be persisted");
    });

    it("adds jump-to-reply action alongside latest message button and marks pending person cards", () => {
        assert.ok(chatSource.includes('data-action="mc-jump-compose"'), "timeline head must expose jump-to-reply button");
        assert.ok(chatSource.includes('action === "mc-jump-compose"'), "click delegate must handle mc-jump-compose");
        assert.ok(chatSource.includes('data-has-pending='), "person cards must emit data-has-pending attribute");
        assert.match(shellCss, /\.mc-person\[data-has-pending=true\]/, "pending card visual accent must be styled in console-shell.css");
    });

    it("supports keyboard triage shortcuts (/, j/k, Ctrl/Cmd+Enter send) and host link dialog delegation", () => {
        assert.ok(chatSource.includes('event.key === "/"'), "slash shortcut to focus search must be handled");
        assert.ok(chatSource.includes('event.key === "j"') && chatSource.includes('event.key === "k"'), "j/k list navigation must be handled");
        assert.ok(chatSource.includes('mc-send-manual'), "Ctrl/Meta+Enter inside editor must trigger manual send");
        assert.ok(chatSource.includes('mcHostPromptLink'), "createLink must delegate to mcHostPromptLink when available");
    });
});
