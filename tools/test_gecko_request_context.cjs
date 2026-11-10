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
  console.log("Gecko request-context fixtures passed.");
})().catch(error => { console.error(error); process.exitCode = 1; });
