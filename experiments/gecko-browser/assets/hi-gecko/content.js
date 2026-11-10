"use strict";
(() => {
  if (document.contentType && document.contentType.toLowerCase() !== "text/html") return;
  const send = value => browser.runtime.sendNativeMessage("hi_player", value).then(response =>
    typeof response === "string" ? JSON.parse(response) : response);
  send({kind:"bootstrap"}).then(settings => {
    if (settings && settings.css) {
      const apply = () => {
        if (!document.documentElement) return;
        const style = document.createElement("style"); style.textContent = settings.css; document.documentElement.appendChild(style);
      };
      if (document.documentElement) apply(); else document.addEventListener("DOMContentLoaded",apply,{once:true});
    }
    browser.runtime.sendMessage({kind:"helpers",adBlock:!!(settings && settings.adBlock),pageSkipper:!!(settings && settings.pageSkipper),youtube:!!(settings && settings.youtube),skipSpeed:settings && settings.skipSpeed,autoContinue:!(settings && settings.autoContinue === false)}).catch(() => {});
  }).catch(() => {});
  if (window.top === window) {
    const port = browser.runtime.connectNative("hi_player");
    port.onMessage.addListener(message => {
      if (message.command !== "reader") return;
      const root = document.querySelector("article,main,[role=main]") || document.body;
      port.postMessage({kind:"reader",page:location.href,text:root ? (root.innerText || "").slice(0,150000) : ""});
    });
  }
  browser.runtime.onMessage.addListener(message => {
    if (message.kind === "network-media" && message.page.split("#")[0] === location.href.split("#")[0]) send({kind:"media",url:message.url,mime:message.mime,title:document.title || ""}).catch(() => {});
  });
  const report = element => {
    if (!(element instanceof HTMLMediaElement)) return;
    const url = element.currentSrc || element.src;
    if (!/^https?:\/\//i.test(url || "")) return;
    send({kind:"media",url,mime:element.type || "",title:document.title || ""}).catch(() => {});
  };
  document.addEventListener("loadedmetadata",event => report(event.target),true);
  document.addEventListener("play",event => report(event.target),true);
  document.addEventListener("DOMContentLoaded",() => document.querySelectorAll("video,audio").forEach(report),{once:true});
})();
