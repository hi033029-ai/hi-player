"use strict";
// Regression fixtures, to be run with the next explicitly approved build.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const source = fs.readFileSync(path.join(__dirname, "../app/src/main/assets/orbit_adblock.js"), "utf8");
const styles = [];
let clicked = false;
function Anchor() {}
Anchor.prototype.click = function () { clicked = true; };
const originalClick = Anchor.prototype.click;
const frame = {offsetWidth:300, offsetHeight:250, style:{setProperty(){ throw Error("legitimate frame hidden"); }},
  getAttribute(name){return name === "src" ? "https://widget.example/form" : "";}};
const root = {appendChild(node){styles.push(node.textContent);}};
const document = {contentType:"text/html", readyState:"complete", documentElement:root, head:root,
  createElement(){return {setAttribute(){}};}, addEventListener(){},
  getElementsByTagName(tag){return tag === "iframe" ? [frame] : [];}, querySelectorAll(){return [];}};
const window = {setTimeout(fn){fn(); return 1;}, setInterval(){return 1;}, clearInterval(){}};
class MutationObserver { observe() {} }
vm.runInNewContext(source, {window, document, location:{hostname:"files.example",href:"https://files.example/"},
  HTMLAnchorElement:Anchor, MutationObserver, URL, innerWidth:1000, innerHeight:1000});
assert.equal(Anchor.prototype.click, originalClick, "legitimate scripted anchors must retain native click");
new Anchor().click();
assert.equal(clicked, true);
assert(styles.some(css => css.includes('iframe[src*="doubleclick.net"]')), "specific ad filters remain active");
console.log("Ad-shield compatibility fixtures passed.");
