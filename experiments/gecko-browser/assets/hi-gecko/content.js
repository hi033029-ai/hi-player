"use strict";
(() => {
  if (document.contentType && document.contentType.toLowerCase() !== "text/html") return;
  const send = value => browser.runtime.sendNativeMessage("hi_player", value).then(response =>
    typeof response === "string" ? JSON.parse(response) : response);
  const helperResult = ok => {
    if (window.top === window) send({kind:"helper-status",page:location.href,ok}).catch(() => {});
  };
  send({kind:"bootstrap"}).then(settings => {
    if (settings && settings.css) {
      const apply = () => {
        if (!document.documentElement) return;
        const style = document.createElement("style"); style.textContent = settings.css; document.documentElement.appendChild(style);
      };
      if (document.documentElement) apply(); else document.addEventListener("DOMContentLoaded",apply,{once:true});
    }
    browser.runtime.sendMessage({kind:"helpers",adBlock:!!(settings && settings.adBlock),pageSkipper:!!(settings && settings.pageSkipper),skipSpeed:settings && settings.skipSpeed,autoContinue:!(settings && settings.autoContinue === false)})
      .then(applied => helperResult(applied === true), () => helperResult(false));
  }).catch(() => helperResult(false));
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
  if (window.top === window) {
    let lastPlaying = false;
    let timer = 0;
    const reportPlayback = () => {
      const playing = Array.from(document.querySelectorAll("video")).some(video => !video.paused && !video.ended);
      if (playing !== lastPlaying) {
        lastPlaying = playing;
        send({kind:"video-playback", playing}).catch(() => {});
      }
      if (playing && !timer) timer = setInterval(reportPlayback, 1000);
      if (!playing && timer) { clearInterval(timer); timer = 0; }
    };
    for (const type of ["playing", "pause", "ended", "emptied"]) document.addEventListener(type, reportPlayback, true);
  }
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
