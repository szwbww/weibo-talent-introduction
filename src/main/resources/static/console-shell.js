(function (global) {
    "use strict";
    const pending = new Map();
    const animations = new WeakMap();
    function cancel(container) {
        const active = pending.get(container);
        if (active) active.finish(false);
    }
    function begin(container, content, label) {
        if (!container) return () => {};
        cancel(container);
        if (content && animations.has(content)) animations.get(content).cancel();
        const doc = container.ownerDocument;
        const overlay = doc.createElement("div");
        overlay.className = "console-loading";
        overlay.setAttribute("role", "status");
        overlay.setAttribute("aria-live", "polite");
        const indicator = doc.createElement("span");
        indicator.className = "console-loading-indicator";
        indicator.setAttribute("aria-hidden", "true");
        const text = doc.createElement("span");
        text.textContent = label || "正在加载…";
        overlay.appendChild(indicator);
        overlay.appendChild(text);
        const blocked = (content ? [content] : Array.from(container.children))
            .map(node => ({ node, inert: node.inert }));
        const previousBusy = container.getAttribute("aria-busy");
        container.classList.add("console-load-host");
        container.setAttribute("aria-busy", "true");
        blocked.forEach(({ node }) => { node.inert = true; });
        const active = {};
        pending.set(container, active);
        const timer = setTimeout(() => {
            if (pending.get(container) === active) container.appendChild(overlay);
        }, 120);
        active.finish = (animate = true) => {
            if (pending.get(container) !== active) return;
            clearTimeout(timer);
            pending.delete(container);
            if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
            if (previousBusy === null) container.removeAttribute("aria-busy");
            else container.setAttribute("aria-busy", previousBusy);
            blocked.forEach(({ node, inert }) => { node.inert = inert; });
            const reduced = global.matchMedia && global.matchMedia("(prefers-reduced-motion: reduce)").matches;
            if (animate && content && content.isConnected !== false && typeof content.animate === "function" && !reduced) {
                animations.set(content, content.animate([
                    { opacity: .25, transform: "translateY(4px)" },
                    { opacity: 1, transform: "translateY(0)" }
                ], { duration: 180, easing: "cubic-bezier(.22,1,.36,1)" }));
            }
        };
        return active.finish;
    }
    global.ConsoleMotion = { begin, cancel, cancelAll() { Array.from(pending.keys()).forEach(cancel); } };
})(typeof window !== "undefined" ? window : globalThis);

(function () {
    "use strict";
    const sidebar = document.querySelector(".topnav");
    const navigation = document.getElementById("consoleNavigation");
    const toggle = document.getElementById("consoleSidebarToggle");
    if (!sidebar || !navigation || !toggle) return;

    const tooltip = document.createElement("div");
    tooltip.id = "consoleNavTooltip";
    tooltip.className = "console-nav-tooltip";
    tooltip.setAttribute("role", "tooltip");
    tooltip.hidden = true;
    document.body.appendChild(tooltip);
    let tooltipTarget = null;

    function hideTooltip() {
        tooltip.hidden = true;
        if (tooltipTarget) {
            const ids = (tooltipTarget.getAttribute("aria-describedby") || "").split(/\s+/)
                .filter(id => id && id !== tooltip.id);
            if (ids.length) tooltipTarget.setAttribute("aria-describedby", ids.join(" "));
            else tooltipTarget.removeAttribute("aria-describedby");
        }
        tooltipTarget = null;
    }

    function showTooltip(button) {
        hideTooltip();
        if (!document.body.classList.contains("console-nav-collapsed") || window.innerWidth <= 760) return;
        const rect = button.getBoundingClientRect();
        tooltip.textContent = button.getAttribute("aria-label");
        tooltip.style.left = `${sidebar.getBoundingClientRect().right + 10}px`;
        tooltip.style.top = `${Math.min(window.innerHeight - 24, Math.max(24, rect.top + rect.height / 2))}px`;
        tooltip.hidden = false;
        tooltipTarget = button;
        button.setAttribute("aria-describedby", `${button.getAttribute("aria-describedby") || ""} ${tooltip.id}`.trim());
    }

    navigation.querySelectorAll(".nav-tab").forEach(button => {
        const label = button.querySelector("span:not(.nav-badge):not(.task-center-pill)");
        button.setAttribute("aria-label", label ? label.textContent.trim() : button.textContent.trim());
        button.addEventListener("pointerenter", () => showTooltip(button));
        button.addEventListener("focus", () => showTooltip(button));
        ["pointerleave", "blur", "click"].forEach(event => button.addEventListener(event, hideTooltip));
    });

    function setCollapsed(collapsed) {
        document.body.classList.toggle("console-nav-collapsed", collapsed);
        toggle.setAttribute("aria-expanded", String(!collapsed));
        toggle.setAttribute("aria-label", collapsed ? "展开侧栏" : "收起侧栏");
        toggle.title = toggle.getAttribute("aria-label");
        hideTooltip();
    }
    toggle.addEventListener("click", () => setCollapsed(!document.body.classList.contains("console-nav-collapsed")));
    sidebar.addEventListener("scroll", hideTooltip, { passive: true });
    window.addEventListener("resize", hideTooltip);
    document.addEventListener("keydown", event => { if (event.key === "Escape") hideTooltip(); });
    // Presentation only: a fresh load always starts compact, with no stored preference.
    setCollapsed(true);
})();
