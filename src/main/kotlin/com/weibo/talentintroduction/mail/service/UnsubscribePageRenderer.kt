package com.weibo.talentintroduction.mail.service

import com.weibo.talentintroduction.config.UnsubscribeProperties
import java.net.URI
import org.springframework.stereotype.Service

@Service
class UnsubscribePageRenderer(
    private val properties: UnsubscribeProperties
) {
    fun confirmPage(token: String, email: String): String {
        val bodyHtml = """
<p class="qf-eyebrow">Unsubscribe</p>
<h1 class="qf-title">Stop receiving emails from ${properties.brandName}?</h1>
<p class="qf-text">We&#39;ll remove this address from all future outreach. You can still reach us any time by replying to a previous message.</p>
<p class="qf-pill">${maskEmail(email)}</p>
<div class="qf-actions">
  <form method="post" action="unsubscribe/confirm" style="margin:0">
    <input type="hidden" name="token" value="${escapeHtml(token)}">
    <button type="submit" class="qf-btn qf-btn-primary">Confirm unsubscribe</button>
  </form>
  <a class="qf-btn qf-btn-ghost" href="${properties.siteUrl}">Keep me subscribed</a>
</div>
""".trimIndent()
        return renderShell(bodyHtml)
    }

    fun successPage(): String {
        val bodyHtml = """
<p class="qf-check">&#10003;</p>
<h1 class="qf-title">You&#39;ve been unsubscribed</h1>
<p class="qf-text">This address won&#39;t receive further outreach from us. Changes take effect immediately.</p>
<a class="qf-link" href="${properties.siteUrl}">Visit ${siteHost()} &#8594;</a>
""".trimIndent()
        return renderShell(bodyHtml)
    }

    private fun renderShell(bodyHtml: String): String {
        val brandBlock = if (properties.brandLogoUrl.isBlank()) {
            """<span class="qf-wordmark">${properties.brandName}</span>"""
        } else {
            """<img class="qf-logo" src="${properties.brandLogoUrl}" alt="${properties.brandName}">"""
        }
        val footer = buildString {
            if (properties.footerLine1.isNotBlank()) append(escapeHtml(properties.footerLine1))
            if (properties.footerLine2.isNotBlank()) {
                if (isNotEmpty()) append("<br>")
                append(escapeHtml(properties.footerLine2))
            }
        }
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>Unsubscribe</title>
$STYLE_BLOCK
</head>
<body>
<div class="qf-wrap">
  <header class="qf-head">
    $brandBlock
    <span class="qf-headnote">Email preferences</span>
  </header>
  <main class="qf-main">
    $bodyHtml
  </main>
  <footer class="qf-foot">
    $footer
  </footer>
</div>
</body>
</html>
""".trimIndent()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun maskEmail(email: String): String {
        val at = email.lastIndexOf('@')
        if (at < 0) return "•••"
        val local = email.substring(0, at)
        val domain = email.substring(at + 1)
        if (domain.isEmpty()) return "•••"
        return if (local.length <= 1 || local.contains('@')) "•••@" + domain
        else local[0] + "•••@" + domain
    }

    private fun siteHost(): String {
        val url = properties.siteUrl
        return try {
            URI(url).host ?: url
        } catch (_: Exception) {
            url
        }
    }

    companion object {
        private const val STYLE_BLOCK = """<style>
*{box-sizing:border-box}
:root{--color-bg:#05070f;--color-panel:#0c1322;--color-surface:#101a2e;--color-text:#eaf0ff;--color-muted:#93a3c4;--color-muted-strong:#c3cee2;--color-cyan:#3b82f6;--color-ink:#f7fbff;--border-subtle:rgba(255,255,255,0.08);--border-strong:rgba(255,255,255,0.14);--gradient-brand:linear-gradient(100deg,#60a5fa,#3b82f6 55%,#6366f1);--gradient-panel:linear-gradient(180deg,#101a2e,#0c1322);--shadow-card:0 24px 80px rgba(0,0,0,0.42);--shadow-glow:0 0 34px rgba(59,130,246,0.26);--radius-pill:999px;--font-sans:ui-sans-serif,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;--font-mono:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,monospace}
body{margin:0;min-width:320px;color:var(--color-text);font-family:var(--font-sans);line-height:1.6;-webkit-font-smoothing:antialiased;background-color:#05070f;background-image:radial-gradient(1100px 720px at 82% -8%,rgba(59,130,246,0.20),transparent 58%),radial-gradient(900px 620px at 12% 4%,rgba(99,102,241,0.12),transparent 60%),radial-gradient(1.4px 1.4px at 24% 18%,rgba(255,255,255,0.85),transparent 60%),radial-gradient(1px 1px at 68% 32%,rgba(255,255,255,0.60),transparent 60%),radial-gradient(1.5px 1.5px at 44% 62%,rgba(191,219,254,0.55),transparent 60%),radial-gradient(1px 1px at 86% 74%,rgba(255,255,255,0.55),transparent 60%),radial-gradient(1px 1px at 14% 82%,rgba(255,255,255,0.45),transparent 60%),radial-gradient(1.2px 1.2px at 58% 88%,rgba(255,255,255,0.50),transparent 60%);background-repeat:no-repeat,no-repeat,repeat,repeat,repeat,repeat,repeat,repeat;background-size:auto,auto,320px 320px,260px 260px,300px 300px,240px 240px,280px 280px,220px 220px;background-attachment:fixed}
h1,p{margin:0}
a{color:inherit;text-decoration:none}
.qf-wrap{width:min(560px,calc(100% - 48px));margin-inline:auto}
.qf-head{min-height:60px;display:flex;align-items:center;justify-content:space-between;gap:24px;border-bottom:1px solid var(--border-subtle)}
.qf-logo{width:auto;height:34px;display:block}
.qf-wordmark{font-size:16px;font-weight:700;color:var(--color-text)}
.qf-headnote{font-size:12px;font-weight:600;color:var(--color-muted)}
.qf-main{padding:72px 0;text-align:center}
.qf-eyebrow{color:var(--color-cyan);font:700 11px/1 var(--font-mono);letter-spacing:0.14em;text-transform:uppercase}
.qf-title{margin-top:12px;font-size:clamp(28px,3vw,38px);line-height:1.14;letter-spacing:-0.025em}
.qf-text{margin:9px auto 0;max-width:460px;font-size:14px;color:var(--color-muted-strong)}
.qf-pill{display:inline-flex;align-items:center;gap:8px;margin-top:24px;padding:9px 18px;border:1px solid var(--border-subtle);border-radius:var(--radius-pill);background:var(--gradient-panel);box-shadow:var(--shadow-card);font-family:var(--font-mono);font-size:13px;color:var(--color-muted-strong)}
.qf-actions{display:flex;gap:12px;justify-content:center;flex-wrap:wrap;margin-top:32px}
.qf-btn{display:inline-flex;min-height:38px;align-items:center;justify-content:center;gap:8px;padding:9px 16px;border:1px solid transparent;border-radius:7px;font-family:inherit;font-size:13px;font-weight:700;line-height:1;cursor:pointer;transition:transform 180ms ease,border-color 180ms ease,box-shadow 180ms ease,background-color 180ms ease}
.qf-btn:hover{transform:translateY(-1px)}
.qf-btn:focus-visible{outline:3px solid rgba(59,130,246,0.52);outline-offset:3px}
.qf-btn-primary{background:var(--gradient-brand);color:var(--color-ink);box-shadow:var(--shadow-glow)}
.qf-btn-ghost{border-color:var(--border-strong);background:rgba(255,255,255,0.03);color:var(--color-text)}
.qf-btn-ghost:hover{border-color:rgba(59,130,246,0.45);background:rgba(59,130,246,0.06)}
.qf-check{width:46px;height:46px;margin:0 0 4px;border-radius:50%;border:1px solid var(--border-subtle);background:rgba(59,130,246,0.14);color:var(--color-cyan);display:inline-flex;align-items:center;justify-content:center;font-size:22px;line-height:1}
.qf-link{margin-top:24px;display:inline-block;font-size:14px;color:var(--color-muted)}
.qf-link:hover{color:var(--color-cyan)}
.qf-foot{padding:42px 0 24px;border-top:1px solid var(--border-subtle);text-align:center;color:var(--color-muted);font-size:12px}
@media (max-width:480px){.qf-wrap{width:min(560px,calc(100% - 36px))}.qf-main{padding:52px 0}.qf-actions{flex-direction:column}.qf-btn{width:100%}}
</style>"""
    }
}
