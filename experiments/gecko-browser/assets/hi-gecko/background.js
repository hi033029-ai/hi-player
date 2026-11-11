"use strict";
// Only URL decisions and media addresses are shared; cookies and auth headers are not copied.
const sendNative = message => browser.runtime.sendNativeMessage("hi_player", message).then(response =>
  typeof response === "string" ? JSON.parse(response) : response);
const requestPage = details => {
  if (details.type === "main_frame") return details.url;
  const top = (details.frameAncestors || []).find(frame => frame.frameId === 0);
  return (top && /^https?:\/\//i.test(top.url || "") && top.url) || details.documentUrl || details.originUrl || "";
};
browser.webRequest.onBeforeRequest.addListener(details =>
  sendNative({kind:"request",url:details.url,page:requestPage(details),main:details.type === "main_frame"})
    .then(result => ({cancel:result && result.blocked === true}), () => ({cancel:true})),
  {urls:["http://*/*","https://*/*"]},["blocking"]);
browser.runtime.onMessage.addListener(async (message, sender) => {
  if (sender.id !== browser.runtime.id || !sender.tab || message.kind !== "helpers") return false;
  const files = [];
  if (message.adBlock === true) files.push("orbit_adblock.js");
  if (message.pageSkipper === true) files.push("orbit_page_skipper.js");
  if (message.youtube === true) files.push("orbit_youtube.js");
  try {
    if (message.pageSkipper !== true) await browser.scripting.executeScript({
      target:{tabId:sender.tab.id,frameIds:[sender.frameId]},world:"MAIN",injectImmediately:true,
      func: () => { if (window.__hiEarlyTimers) window.__hiEarlyTimers.disable(); }
    });
    if (!files.length) return true;
    if (message.pageSkipper === true) {
      const speed = [1,10,30,60].includes(message.skipSpeed) ? message.skipSpeed : 30;
      await browser.scripting.executeScript({
        target:{tabId:sender.tab.id,frameIds:[sender.frameId]},world:"MAIN",injectImmediately:true,
        func: options => { window.__hiSkipOptions = options; },
        args:[{speed,autoContinue:message.autoContinue !== false}]
      });
    }
    await browser.scripting.executeScript({target:{tabId:sender.tab.id,frameIds:[sender.frameId]},files,world:"MAIN",injectImmediately:true});
    return true;
  } catch (_) { return false; }
});
browser.webRequest.onHeadersReceived.addListener(details => {
  const header = (details.responseHeaders || []).find(item => (item.name || "").toLowerCase() === "content-type");
  const mime = header ? (header.value || "").split(";")[0].trim().toLowerCase() : "";
  if (!(mime.startsWith("video/") || mime.startsWith("audio/")) && !["application/vnd.apple.mpegurl","application/x-mpegurl","application/dash+xml"].includes(mime)) return;
  if (details.tabId < 0 || !details.documentUrl) return;
  browser.tabs.sendMessage(details.tabId,{kind:"network-media",url:details.url,mime,page:details.documentUrl},{frameId:details.frameId}).catch(() => {});
},{urls:["http://*/*","https://*/*"]},["responseHeaders"]);
