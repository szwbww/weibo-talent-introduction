"use strict";
const {test} = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const root = path.join(__dirname, "../../main/resources/static");

function node(label = "") {
    const attrs = new Map(), classes = new Set(), listeners = {};
    return {
        textContent: label, style: {}, hidden: false,
        classList: {contains: k => classes.has(k), toggle(k, on) { on ? classes.add(k) : classes.delete(k); }},
        setAttribute: (k, v) => attrs.set(k, v), getAttribute: k => attrs.get(k) ?? null,
        removeAttribute: k => attrs.delete(k),
        addEventListener(k, fn) { (listeners[k] ||= []).push(fn); },
        fire(k, event = {}) { (listeners[k] || []).forEach(fn => fn(event)); },
        getBoundingClientRect: () => ({right:68, top:120, height:38}),
        querySelector: () => ({textContent:label}), appendChild(child) { this.child = child; }
    };
}

test("sidebar defaults compact, toggles without navigation or persistence, and exposes keyboard tooltips", () => {
    const body = node(), sidebar = node(), nav = node(), toggle = node(), tab = node("收发件箱");
    nav.querySelectorAll = () => [tab];
    let navigations = 0;
    tab.addEventListener("click", () => navigations++);
    const document = Object.assign(node(), {
        body, createElement: () => node(), querySelector: () => sidebar,
        getElementById: id => ({consoleNavigation:nav, consoleSidebarToggle:toggle}[id])
    });
    const window = Object.assign(node(), {innerWidth:1440,innerHeight:900});
    vm.runInNewContext(fs.readFileSync(path.join(root,"console-shell.js"),"utf8"), {document,window});
    assert.equal(body.classList.contains('console-nav-collapsed'),true);
    assert.equal(toggle.getAttribute('aria-expanded'),'false');
    assert.equal(toggle.textContent,'');
    tab.setAttribute('aria-describedby','existing-description');
    tab.fire('focus');
    assert.equal(body.child.hidden,false);
    assert.equal(body.child.textContent,'收发件箱');
    document.fire('keydown',{key:'Escape'});
    assert.equal(body.child.hidden,true);
    assert.equal(tab.getAttribute('aria-describedby'),'existing-description');
    toggle.fire('click');
    assert.equal(body.classList.contains('console-nav-collapsed'),false);
    assert.equal(toggle.getAttribute('aria-expanded'),'true');
    assert.equal(navigations,0);
    tab.fire('click'); assert.equal(navigations,1);
    toggle.fire('click'); assert.equal(toggle.getAttribute('aria-expanded'),'false');
    window.innerWidth = 390; tab.fire('focus'); assert.equal(body.child.hidden,true);
});

test("real entry registers shell once and exposes icon-only control outside business navigation", () => {
    const html = fs.readFileSync(path.join(root,"index.html"),"utf8");
    assert.match(html, /<body class="console-ui console-nav-collapsed">/);
    const button = html.match(/<button id="consoleSidebarToggle"[\s\S]*?<\/button>/)[0];
    assert.ok(!/class="[^"]*nav-tab/.test(button));
    assert.equal(button.replace(/<[^>]+>/g, '').trim(),'');
    assert.match(button,/aria-controls="consoleNavigation"/);
    assert.match(html,/<nav class="nav-tabs" id="consoleNavigation"/);
    assert.equal((html.match(/data-view="/g)||[]).length,10);
    for (const file of ['console-shell.css','console-shell.js']) {
        assert.equal(html.split(`${file}?v=`).length - 1,1);
        assert.ok(fs.existsSync(path.join(root,file)));
    }
    assert.ok(!html.includes('preview-guard'));
});
