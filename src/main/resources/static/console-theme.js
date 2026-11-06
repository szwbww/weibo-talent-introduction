(function () {
    "use strict";
    const cookieName = "talent_console_theme";
    const root = document.documentElement;
    let theme = "light";
    try {
        const preference = document.cookie.split(";")
            .map(part => part.trim()).find(part => part.startsWith(cookieName + "="));
        if (preference === cookieName + "=dark") theme = "dark";
    } catch (_) {
        // Restricted cookie access must not prevent the console from loading.
    }
    // This script runs synchronously in head, before styles and the first paint.
    root.setAttribute("data-console-theme", theme);

    function bindToggle() {
        const button = document.getElementById("consoleThemeToggle");
        if (!button) return;
        function updateButton() {
            const label = theme === "dark" ? "切换到白色主题" : "切换到黑色主题";
            button.setAttribute("aria-label", label);
            button.setAttribute("title", label);
            button.setAttribute("aria-pressed", String(theme === "dark"));
        }
        updateButton();
        button.addEventListener("click", () => {
            theme = theme === "dark" ? "light" : "dark";
            root.setAttribute("data-console-theme", theme);
            updateButton();
            try {
                document.cookie = cookieName + "=" + theme + "; Path=/; Max-Age=31536000; SameSite=Lax"
                    + (window.location.protocol === "https:" ? "; Secure" : "");
            } catch (_) {
                // Keep the current-page choice usable when storage is blocked.
            }
        });
    }
    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", bindToggle, { once: true });
    } else {
        bindToggle();
    }
})();
