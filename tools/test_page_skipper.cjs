"use strict";
// Deterministic fixtures for a future explicitly approved build/test run.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const source = fs.readFileSync(path.join(__dirname, "../app/src/main/assets/orbit_page_skipper.js"), "utf8");

function fixture({controls = [], text = "File Size 7.98 GB File Type video/x-matroska", sensitive = false, auto = true} = {}) {
  const listeners = {};
  const root = {appendChild() {}, style: {setProperty() {}}};
  const document = {
    contentType:"text/html", title:"Download", body:{innerText:text, style:{setProperty(){}}},
    documentElement:root, head:root, forms:[], readyState:"loading",
    createElement() { return {}; }, hasFocus() { return true; },
    addEventListener(type, fn) { listeners[type] = fn; },
    querySelector() { return sensitive ? {} : null; },
    querySelectorAll(selector) {
      if (selector === 'a,button,input[type=button],[role=button]') return controls;
      return [];
    }
  };
  const window = {
    __hiSkipOptions:{speed:1, autoContinue:auto}, Date, setTimeout(){return 1;},
    setInterval(){return 1;}, clearTimeout(){}, clearInterval(){}, open(){},
    addEventListener(){}, requestAnimationFrame(){}
  };
  window.top = window;
  class MutationObserver { observe() {} disconnect() {} }
  vm.runInNewContext(source, {window,document,location:{hostname:"files.example",href:"https://files.example/file"},
    URL, MutationObserver, getComputedStyle:() => ({visibility:"visible",display:"block"})});
  listeners.DOMContentLoaded();
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
console.log("Download-link generator fixtures passed.");
