"use strict";
// Decisions use the existing local filter engine; request headers are never copied.
browser.webRequest.onBeforeRequest.addListener(details =>
  browser.runtime.sendNativeMessage("hi_player", {
    kind: "request", url: details.url,
    page: details.documentUrl || details.originUrl || "",
    main: details.type === "main_frame"
  }).then(result => ({cancel: result && result.blocked === true}), () => ({cancel: true})),
  {urls: ["http://*/*", "https://*/*"]}, ["blocking"]);

browser.runtime.onMessage.addListener(async (message, sender) => {
  if (sender.id !== browser.runtime.id || !sender.tab || message.kind !== "helpers") return false;
  const files = [];
  if (message.adBlock === true) files.push("orbit_adblock.js");
  if (message.pageSkipper === true) files.push("orbit_page_skipper.js");
  if (!files.length) return true;
  try {
    await browser.scripting.executeScript({target:{tabId:sender.tab.id,frameIds:[sender.frameId]},files,world:"MAIN",injectImmediately:true});
    return true;
  } catch (_) { return false; }
});
