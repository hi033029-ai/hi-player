"use strict";
// Only local decisions and media addresses; cookies and authorization headers are not copied.
browser.webRequest.onBeforeRequest.addListener(details =>
  browser.runtime.sendNativeMessage("hi_player", {kind:"request",url:details.url,page:details.documentUrl || details.originUrl || "",main:details.type === "main_frame"})
    .then(result => ({cancel:result && result.blocked === true}), () => ({cancel:true})),
  {urls:["http://*/*","https://*/*"]}, ["blocking"]);
browser.runtime.onMessage.addListener(async (message, sender) => {
  if (sender.id !== browser.runtime.id || !sender.tab || message.kind !== "helpers") return false;
  const files = [];
  if (message.adBlock === true) files.push("orbit_adblock.js");
  if (message.pageSkipper === true) files.push("orbit_page_skipper.js");
  if (message.youtube === true) files.push("orbit_youtube.js");
  if (!files.length) return true;
  try {
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
