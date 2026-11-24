"use strict";
(() => {
  if (document.contentType && document.contentType.toLowerCase() !== "text/html") return;
  const send = value => browser.runtime.sendNativeMessage("hi_player", value).then(response =>
    typeof response === "string" ? JSON.parse(response) : response);
  const helperResult = ok => {
    if (window.top === window) send({kind:"helper-status",page:location.href,ok}).catch(() => {});
  };
  const mobileVideoSite = /(^|\.)tiktok\.com$/i.test(location.hostname || "");
  send({kind:"bootstrap"}).then(settings => {
    if (!mobileVideoSite && settings && settings.css) {
      const apply = () => {
        if (!document.documentElement) return;
        const style = document.createElement("style"); style.textContent = settings.css; document.documentElement.appendChild(style);
      };
      if (document.documentElement) apply(); else document.addEventListener("DOMContentLoaded",apply,{once:true});
    }
    browser.runtime.sendMessage({kind:"helpers",adBlock:!mobileVideoSite && !!(settings && settings.adBlock),pageSkipper:!mobileVideoSite && !!(settings && settings.pageSkipper),skipSpeed:settings && settings.skipSpeed,autoContinue:!(settings && settings.autoContinue === false)})
      .then(applied => helperResult(applied === true), () => helperResult(false));
  }).catch(() => helperResult(false));
  let refreshModes = () => {};
  if (window.top === window) {
    const port = browser.runtime.connectNative("hi_player");
    let generate = null, bar = null, timer = 0, expiry = 0, attempts = 0;
    function endGenerate() {
      clearTimeout(timer); timer = 0; clearTimeout(expiry);
      bar?.remove(); bar = null; generate = null;
    }
    window.addEventListener("pagehide", endGenerate);
    document.addEventListener("visibilitychange", () => { if (document.hidden) endGenerate(); });
    document.addEventListener("click", event => {
      if (!event.isTrusted || event.button !== 0) return;
      if (bar && bar.contains(event.target)) { endGenerate(); return; }
      endGenerate();
      const button = event.target.closest?.('a,button,[role="button"],input[type="submit"],input[type="button"]');
      if (!button) return;
      const label = [button.getAttribute("aria-label"), button.textContent, button.value].filter(Boolean).join(" ").slice(0, 240);
      if (/generate(?: direct)? (?:download )?link/i.test(label) && !button.closest("form")) {
        generate = button; attempts = 0;
        expiry = setTimeout(endGenerate, 15000);
      }
      if (button.hasAttribute("download") || /\b(download|generate(?: direct)? link|get (?:download )?link)\b/i.test(label)) {
        port.postMessage({kind:"download-click", page:location.href});
      }
    }, true);
    // Keep the page's own player and session alive, including its buffered media.
    const mediaElement = () => {
      const media = Array.from(document.querySelectorAll("video,audio"));
      return media.find(item => !item.paused && !item.ended) ||
        media.find(item => item.readyState >= 2 && item.currentTime > 0);
    };
    const shuffleButton = () => Array.from(document.querySelectorAll(
      'button[aria-pressed][aria-label*="shuffle" i], [role="button"][aria-pressed][aria-label*="shuffle" i]'
    )).find(button => !button.disabled && button.getAttribute("aria-disabled") !== "true" && button.getClientRects().length);
    let lastModes = "";
    const reportModes = (force = false) => {
      const media = mediaElement();
      const shuffle = media && shuffleButton();
      const state = {kind: "media-modes", page: location.href, canRepeat: !!media && Number.isFinite(media.duration),
        repeat: !!media && media.loop, canShuffle: !!shuffle, shuffle: !!shuffle && shuffle.getAttribute("aria-pressed") === "true"};
      const serialized = JSON.stringify(state);
      if (force || lastModes !== serialized) {
        lastModes = serialized;
        port.postMessage(state);
      }
    };
    refreshModes = reportModes;
    let restoreMini = () => {};
    const enterMini = () => {
      restoreMini();
      const video = mediaElement();
      if (!(video instanceof HTMLVideoElement)) return;
      const hadControls = video.controls;
      const style = document.createElement("style");
      video.setAttribute("data-hi-mini", "");
      video.controls = true;
      const ownsPopover = !video.hasAttribute("popover") && typeof video.showPopover === "function";
      if (ownsPopover) {
        video.setAttribute("popover", "manual");
        try { video.showPopover(); } catch (_) { video.removeAttribute("popover"); }
      }
      style.textContent = 'video[data-hi-mini]{position:fixed!important;inset:auto 8px 72px auto!important;width:min(75vw,420px)!important;height:auto!important;max-height:45vh!important;aspect-ratio:16/9;object-fit:contain!important;transform:none!important;margin:0!important;padding:0!important;background:black;z-index:2147483647!important;pointer-events:auto!important}';
      document.documentElement.appendChild(style);
      restoreMini = () => {
        style.remove();
        video.removeAttribute("data-hi-mini");
        if (ownsPopover) video.removeAttribute("popover");
        video.controls = hadControls;
        restoreMini = () => {};
      };
    };
    window.addEventListener("pagehide", () => restoreMini());
    let pipStyle;
    const exitPip = () => {
      if (pipStyle) pipStyle.remove();
      pipStyle = null;
      document.querySelectorAll("[data-hi-pip-video],[data-hi-pip-parent]").forEach(element => {
        element.removeAttribute("data-hi-pip-video");
        element.removeAttribute("data-hi-pip-parent");
      });
    };
    const enterPip = () => {
      exitPip();
      restoreMini();
      const video = mediaElement();
      if (!(video instanceof HTMLVideoElement)) return;
      video.setAttribute("data-hi-pip-video", "");
      for (let parent = video.parentElement; parent; parent = parent.parentElement) parent.setAttribute("data-hi-pip-parent", "");
      pipStyle = document.createElement("style");
      pipStyle.textContent = `html,body{background:black!important}
        body *{visibility:hidden!important}
        [data-hi-pip-parent]{overflow:visible!important;transform:none!important;contain:none!important;clip-path:none!important;filter:none!important;perspective:none!important;will-change:auto!important;content-visibility:visible!important}
        video[data-hi-pip-video]{visibility:visible!important;position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;
        margin:0!important;padding:0!important;border:0!important;box-sizing:border-box!important;transform:none!important;translate:none!important;scale:none!important;min-width:0!important;min-height:0!important;max-width:none!important;max-height:none!important;object-fit:contain!important;object-position:50% 50%!important;z-index:2147483647!important}`;
      document.documentElement.appendChild(pipStyle);
    };
    let pinch = null;
    let zoomStyle;
    const distance = touches => Math.hypot(touches[1].clientX - touches[0].clientX, touches[1].clientY - touches[0].clientY);
    const resetZoom = () => {
      pinch = null;
      if (zoomStyle) zoomStyle.remove();
      zoomStyle = null;
      document.querySelectorAll("[data-hi-zoom]").forEach(video => video.removeAttribute("data-hi-zoom"));
    };
    document.addEventListener("fullscreenchange", resetZoom);
    document.addEventListener("touchstart", event => {
      const root = document.fullscreenElement;
      if (!root || event.touches.length !== 2) return;
      const video = root.matches("video") ? root : root.querySelector("video");
      if (!video) return;
      const box = video.getBoundingClientRect();
      if (!Array.from(event.touches).every(t => t.clientX >= box.left && t.clientX <= box.right && t.clientY >= box.top && t.clientY <= box.bottom)) return;
      pinch = {video, start:distance(event.touches)};
      event.preventDefault();
    }, {capture:true, passive:false});
    document.addEventListener("touchmove", event => {
      if (!pinch || event.touches.length !== 2) return;
      event.preventDefault();
      const ratio = distance(event.touches) / pinch.start;
      if (ratio > 1.18 || ratio < 0.85) {
        if (!zoomStyle) {
          zoomStyle = document.createElement("style");
          zoomStyle.textContent = 'video[data-hi-zoom="fill"]{object-fit:cover!important}video[data-hi-zoom="fit"]{object-fit:contain!important}';
          document.documentElement.appendChild(zoomStyle);
        }
        pinch.video.setAttribute("data-hi-zoom", ratio > 1 ? "fill" : "fit");
      }
    }, {capture:true, passive:false});
    for (const type of ["touchend", "touchcancel"]) document.addEventListener(type, () => { pinch = null; }, true);
    window.addEventListener("pagehide", () => { exitPip(); resetZoom(); });
    for (const type of ["playing","loadedmetadata","durationchange","ended","emptied","pause"])
      document.addEventListener(type, () => reportModes(), true);
    port.onMessage.addListener(message => {
      if (message.command === "generate-finished") { endGenerate(); return; }
      if (message.command === "retry-generate") {
        if (!generate?.isConnected || document.hidden || attempts >= 3) { endGenerate(); return; }
        if (timer) return;
        if (!bar) {
          bar = document.createElement("button");
          bar.type = "button";
          bar.style.cssText = "position:fixed;bottom:calc(88px + env(safe-area-inset-bottom));left:0;width:100%;z-index:2147483647;background:#15202a;color:white;padding:8px;border:0";
          bar.textContent = "Retrying download link · Cancel ";
          bar.append(document.createElement("progress"));
          document.documentElement.append(bar);
        }
        attempts++;
        timer = setTimeout(() => {
          timer = 0;
          if (generate?.isConnected && !generate.disabled && !document.hidden) generate.click();
          else endGenerate();
        }, attempts * 300);
        return;
      }
      if (message.command === "mini-enter") { enterMini(); return; }
      if (message.command === "mini-exit") { restoreMini(); return; }
      if (message.command === "pip-enter") { enterPip(); return; }
      if (message.command === "pip-exit") { exitPip(); return; }
      if (message.command === "report-modes") { reportModes(true); return; }
      if (message.command === "repeat-one") {
        const media = mediaElement();
        if (media && Number.isFinite(media.duration)) media.loop = !media.loop;
        reportModes(true);
        return;
      }
      if (message.command === "shuffle") {
        const button = shuffleButton();
        if (button) button.click();
        requestAnimationFrame(() => reportModes(true));
        return;
      }
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
      refreshModes();
      const playing = Array.from(document.querySelectorAll("video")).some(video => !video.paused && !video.ended);
      if (playing !== lastPlaying) {
        lastPlaying = playing;
        send({kind:"video-playback", page:location.href, playing}).catch(() => {});
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
