"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const script = fs.readFileSync(path.join(__dirname, "../app/src/main/assets/orbit_page_skipper.js"), "utf8");

function control(text, attributes = {}, type = "") {
  return {
    innerText: text,
    tagName: attributes.href ? "A" : "BUTTON",
    type,
    clicks: 0,
    click() { this.clicks++; },
    getAttribute(name) { return attributes[name] ?? null; },
    hasAttribute(name) { return Object.hasOwn(attributes, name); },
    getBoundingClientRect() { return { width: 40, height: 40 }; },
  };
}

function loadPage(text, controls, options = {}) {
  const documentEvents = new Map();
  const delays = [];
  const open = () => "opened by browser";
  const context = {
    URL,
    __hiSkipOptions: options,
    location: new URL("https://files.example/wait"),
    document: {
      title: "Download",
      body: { innerText: text },
      documentElement: {},
      querySelector: () => null,
      querySelectorAll: selector => selector.startsWith("a,button,") ? controls : [],
      addEventListener: (name, listener) => documentEvents.set(name, listener),
    },
    open,
    addEventListener() {},
    getComputedStyle: () => ({ visibility: "visible", display: "block" }),
    setTimeout: (_fn, delay) => delays.push(delay),
    setInterval: (_fn, delay) => delays.push(delay),
    clearTimeout() {},
    clearInterval() {},
  };
  context.window = context.top = context;
  vm.createContext(context);
  vm.runInContext("window.Date = Date", context);
  vm.runInContext(script, context);
  documentEvents.get("DOMContentLoaded")();
  assert.equal(context.open, open);
  return { context, delays };
}

const generate = control("Generate Direct Download Link");
const getLink = control("Get your link");
const download = control("Continue", { href: "/file", download: "file.bin" });
const popup = control("Open link", { href: "/next", target: "_blank" });
const submit = control("Continue", {}, "submit");
const next = control("Continue", { href: "/next" });
const { context, delays } = loadPage("Please wait 15 seconds", [generate, getLink, download, popup, submit, next]);
for (const element of [generate, getLink, download, popup, submit]) assert.equal(element.clicks, 0);
assert.equal(next.clicks, 1);
context.setInterval(() => {}, 900);
assert.equal(delays.at(-1), 30);
assert.equal(context.open("/result"), "opened by browser");

const file = loadPage("Get your link", [control("Get your link")]);
file.context.setInterval(() => {}, 900);
assert.equal(file.delays.at(-1), 900);

const manual = control("Continue");
loadPage("Please wait 15 seconds", [manual], { autoContinue: false });
assert.equal(manual.clicks, 0);
