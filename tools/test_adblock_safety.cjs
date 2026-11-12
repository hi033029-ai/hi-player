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
function link(href, invisible, large = true) {
  const result = {href, textContent:invisible ? "" : "Download", hidden:false,
    computed:{position:"fixed", opacity:invisible ? "0" : "1", backgroundColor:"rgb(255, 255, 255)", backgroundImage:"none"},
    getBoundingClientRect(){return {width:large ? 900 : 100, height:large ? 900 : 50};},
    querySelector(){return null;}};
  result.style = {setProperty(name, value){ if (name === "display" && value === "none") result.hidden = true; }};
  return result;
}
const invisibleAd = link("https://ads.example/", true);
const legitimate = link("https://download.example/file", false);
const sameSite = link("https://files.example/next", true);
const small = link("https://other.example/", true, false);
const links = [invisibleAd, legitimate, sameSite, small];
const root = {appendChild(node){styles.push(node.textContent);}};
const document = {contentType:"text/html", readyState:"complete", documentElement:root, head:root,
  createElement(){return {setAttribute(){}};}, addEventListener(){},
  getElementsByTagName(tag){return tag === "iframe" ? [frame] : [];}, querySelectorAll(selector){return selector === "a[href]" ? links : [];}};
const window = {setTimeout(fn){fn(); return 1;}, setInterval(){return 1;}, clearInterval(){}};
class MutationObserver { observe() {} }
vm.runInNewContext(source, {window, document, location:{hostname:"files.example",href:"https://files.example/"},
  HTMLAnchorElement:Anchor, MutationObserver, URL, innerWidth:1000, innerHeight:1000,
  getComputedStyle:element => element.computed});
assert.equal(invisibleAd.hidden, true, "invisible cross-site hijack layers are removed before a tap");
assert.equal(legitimate.hidden, false, "visible download links stay available");
assert.equal(sameSite.hidden, false, "same-site controls are not treated as hijacks");
assert.equal(small.hidden, false, "small cross-site elements are not treated as full-page hijacks");
assert.equal(Anchor.prototype.click, originalClick, "legitimate scripted anchors must retain native click");
new Anchor().click();
assert.equal(clicked, true);
assert(styles.some(css => css.includes('iframe[src*="doubleclick.net"]')), "specific ad filters remain active");
console.log("Ad-shield compatibility fixtures passed.");
