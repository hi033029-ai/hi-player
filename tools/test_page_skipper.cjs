"use strict";
// Deterministic fixtures for a future explicitly approved build/test run.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const source = fs.readFileSync(path.join(__dirname, "../app/src/main/assets/orbit_page_skipper.js"), "utf8");

function fixture({controls = [], text = "File Size 7.98 GB File Type video/x-matroska", sensitive = false, auto = true, overlays = []} = {}) {
  const listeners = {};
  const styles = [];
  const root = {appendChild(el) { styles.push(el.textContent || ""); }, style: {setProperty() {}}};
  const document = {
    contentType:"text/html", title:"Download", body:{innerText:text, style:{setProperty(){}}},
    documentElement:root, head:root, forms:[], readyState:"loading",
    createElement() { return {}; }, hasFocus() { return true; },
    addEventListener(type, fn) { listeners[type] = fn; },
    querySelector() { return sensitive ? {} : null; },
    querySelectorAll(selector) {
      if (selector === 'a,button,input[type=button],[role=button]') return controls;
      if (selector === 'div,section,aside,a,iframe') return overlays;
      return [];
    }
  };
  const window = {
    __hiSkipOptions:{speed:1, autoContinue:auto}, Date, setTimeout(){return 1;},
    setInterval(){return 1;}, clearTimeout(){}, clearInterval(){}, open(){},
    addEventListener(){}, requestAnimationFrame(){}
  };
  window.top = window;
  const originalOpen = window.open;
  class MutationObserver { observe() {} disconnect() {} }
  vm.runInNewContext(source, {window,document,location:{hostname:"files.example",href:"https://files.example/file"},
    URL, MutationObserver, innerWidth:1000, innerHeight:1000,
    getComputedStyle:el => el.computed || ({visibility:"visible",display:"block"})});
  listeners.DOMContentLoaded();
  return {styles, window, originalOpen};
}
function control({label="Generate Direct Download Link", href="", ad=false, disabled=false, tag="BUTTON"} = {}) {
  return {tagName:tag, innerText:label, disabled, clicks:0,
    getAttribute(name) { if (name === "href") return href; return null; },
    closest() { return ad ? {} : null; },
    getBoundingClientRect(){return {width:120,height:40};},
    click(){this.clicks++;}};
}
let c = control(); fixture({controls:[c]}); assert.equal(c.clicks,1);
c = control(); fixture({controls:[c],text:"Generate Direct Download Link"}); assert.equal(c.clicks,0);
c = control({ad:true}); fixture({controls:[c]}); assert.equal(c.clicks,0);
c = control({disabled:true}); fixture({controls:[c]}); assert.equal(c.clicks,0);
c = control({tag:"A",href:"https://ads.example/download"}); fixture({controls:[c]}); assert.equal(c.clicks,0);
c = control(); let d = control(); fixture({controls:[c,d]}); assert.equal(c.clicks+d.clicks,0);
c = control(); fixture({controls:[c],sensitive:true}); assert.equal(c.clicks,0);
c = control(); fixture({controls:[c],auto:false}); assert.equal(c.clicks,0);
function overlay(id) {
  return {id, className:"", outerHTML:`<div id="${id}">`, removed:false,
    computed:{position:"fixed", zIndex:"2000"}, getAttribute(){return "";},
    getBoundingClientRect(){return {width:900,height:900};}, querySelector(){return null;},
    remove(){this.removed = true;}};
}
const realDialog = overlay("download-dialog"), adOverlay = overlay("ad-overlay");
const wait = fixture({text:"Please wait 10 seconds for download link", overlays:[realDialog, adOverlay]});
assert.equal(wait.window.open, wait.originalOpen, "page helper must not override browser popup policy");
assert.equal(realDialog.removed, false, "legitimate next-step dialogs must survive");
assert.equal(adOverlay.removed, true, "explicitly labelled ad overlays remain removable");
assert(!wait.styles.some(css => css.includes('iframe:not(')), "do not blanket-hide iframe widgets");
assert(!wait.styles.some(css => css.includes('a[target="_blank"]:has(> img)')), "do not blanket-hide linked image buttons");
console.log("Download-link generator fixtures passed.");
