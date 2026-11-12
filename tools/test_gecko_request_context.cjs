"use strict";
// Source coverage for the next explicitly approved validation run; not run by source-only commits.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
let beforeRequest;
let lastMessage;
const browser = {
  runtime: {
    id: "fixture",
    sendNativeMessage: async (_app, message) => { lastMessage = message; return JSON.stringify({blocked:false}); },
    onMessage: {addListener() {}}
  },
  webRequest: {
    onBeforeRequest: {addListener(listener) { beforeRequest = listener; }},
    onHeadersReceived: {addListener() {}}
  },
  scripting: {executeScript: async () => []},
  tabs: {sendMessage: async () => {}}
};
vm.runInNewContext(fs.readFileSync(path.join(__dirname, "../experiments/gecko-browser/assets/hi-gecko/background.js"), "utf8"), {browser});
async function check(details, expected) {
  const response = await beforeRequest(details);
  assert.equal(response.cancel, false);
  assert.equal(lastMessage.page, expected);
}
(async () => {
  await check({type:"script",url:"https://ads.example/track.js",documentUrl:"https://frame.example/embed",frameAncestors:[{frameId:7,url:"https://middle.example/"},{frameId:0,url:"https://site.example/page"}]}, "https://site.example/page");
  await check({type:"main_frame",url:"https://destination.example/",documentUrl:"https://opener.example/"}, "https://destination.example/");
  await check({type:"image",url:"https://cdn.example/image",documentUrl:"https://site.example/page"}, "https://site.example/page");
  await check({type:"script",url:"https://cdn.example/script",originUrl:"https://origin.example/"}, "https://origin.example/");
  await check({type:"image",url:"https://cdn.example/image",frameAncestors:[{frameId:0,url:"about:blank"}],documentUrl:"https://site.example/page"}, "https://site.example/page");
  // A site handler must run before the extension's fallback navigation.
  let clickListener, clickCapture, lastNative;
  const win = {addEventListener(type, fn, capture) { if (type === "click") { clickListener = fn; clickCapture = capture; } }};
  win.top = win;
  const doc = {contentType:"text/html", documentElement:{appendChild() {}}, addEventListener() {}, querySelectorAll() { return []; }, createElement() { return {}; }};
  const runtime = {
    sendNativeMessage: async (_app, message) => { lastNative = message; return JSON.stringify(message.kind === "bootstrap" ? {popupBlock:true} : {ok:true}); },
    sendMessage: async () => true, connectNative: () => ({onMessage:{addListener() {}}, postMessage() {}}),
    onMessage: {addListener() {}}
  };
  const location = {href:"https://site.example/generator"};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, "../experiments/gecko-browser/assets/hi-gecko/content.js"), "utf8"),
    {window:win, document:doc, location, browser:{runtime}, HTMLMediaElement:class {}});
  await Promise.resolve(); await Promise.resolve();
  assert.equal(clickCapture, false, "website handlers should run first");
  const click = (href, alreadyHandled = false) => {
    let prevented = false;
    const anchor = {href, target:"_blank", hasAttribute() { return false; }};
    clickListener({defaultPrevented:alreadyHandled, isTrusted:true, button:0, target:{closest() { return anchor; }},
      preventDefault() { prevented = true; }});
    return prevented;
  };
  assert.equal(click("https://site.example/generator#"), false, "placeholder must be left to page scripts");
  assert.equal(click("https://other.example/", true), false, "handled clicks must not be overridden");
  assert.equal(click("https://other.example/"), true, "unhandled external links get same-tab fallback");
  assert.equal(lastNative.kind, "user-link");
  assert.equal(lastNative.url, "https://other.example/");
  console.log("Gecko request-context fixtures passed.");
})().catch(error => { console.error(error); process.exitCode = 1; });
