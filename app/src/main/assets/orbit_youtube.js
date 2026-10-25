// ==UserScript==
// @name         Hi Browser YouTube Ad Skipper
// @namespace    hi.browser
// @version      1.2
// @description  Mutes, fast-forwards and skips YouTube ads, hides ad slots
// @match        *://*.youtube.com/*
// @run-at       document-start
// @grant        none
// ==/UserScript==
(function () {
  'use strict';
  if (window.top !== window || window.__orbitYT) return;
  window.__orbitYT = true;

  // ---- 2. Hide ad slots / banners
  function addStyle(css) {
    try {
      var s = document.createElement('style');
      s.textContent = css;
      (document.head || document.documentElement).appendChild(s);
    } catch (e) {}
  }
  addStyle(
    '#masthead-ad,#player-ads,.ytp-ad-overlay-container,.ytp-ad-overlay-slot,ytd-ad-slot-renderer,' +
    'ytd-in-feed-ad-layout-renderer,ytd-banner-promo-renderer,ytd-promoted-sparkles-web-renderer,' +
    'ytd-promoted-video-renderer,ytd-display-ad-renderer,ytd-companion-slot-renderer,' +
    'ytd-statement-banner-renderer,ytd-primetime-promo-renderer,ytm-promoted-sparkles-web-renderer,' +
    'ytm-promoted-video-renderer,ytm-companion-slot,ytm-companion-ad-renderer,ad-slot-renderer,' +
    'ytd-enforcement-message-view-model,tp-yt-iron-overlay-backdrop.opened{display:none!important}');
  addStyle('ytd-rich-item-renderer:has(ytd-ad-slot-renderer),ytm-rich-item-renderer:has(ad-slot-renderer),' +
    'ytm-item-section-renderer:has(ad-slot-renderer){display:none!important}');

  // ---- 3. Skip any ad that still plays, as soon as the player shows it
  var adState = null;
  var skipSelector = '.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,' +
    '.ytp-ad-skip-button-container button,button[class*="skip-ad"],button[class*="ad-skip"]';
  function check() {
    var p = document.querySelector('.html5-video-player');
    var v = (p && p.querySelector('video')) || document.querySelector('video');
    var showing = !!p && (p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting'));

    if (showing && v) {
      if (!adState) adState = { muted: v.muted, rate: v.playbackRate };
      v.muted = true;
      try { v.playbackRate = 16; } catch (e) {}
      if (isFinite(v.duration) && v.duration > 0) { try { v.currentTime = v.duration; } catch (e) {} }
    } else if (adState && v) {
      v.muted = adState.muted;
      try { v.playbackRate = adState.rate; } catch (e) {}
      adState = null;
    }

    var skip = document.querySelector(skipSelector);
    if (skip) skip.click();
    var close = document.querySelector('.ytp-ad-overlay-close-button');
    if (close) close.click();

    var warn = document.querySelector('ytd-enforcement-message-view-model');
    if (warn) {
      warn.remove();
      document.querySelectorAll('tp-yt-iron-overlay-backdrop').forEach(function (b) { b.remove(); });
      if (v && v.paused) { try { v.play(); } catch (e) {} }
    }
  }

  var pending = false;
  function schedule() {
    if (pending) return;
    pending = true;
    setTimeout(function () { pending = false; check(); }, 0);
  }
  try {
    new MutationObserver(schedule).observe(document.documentElement, {
      childList: true, subtree: true, attributes: true, attributeFilter: ['class']
    });
  } catch (e) {}
  ['loadedmetadata', 'durationchange', 'playing', 'timeupdate'].forEach(function (name) {
    document.addEventListener(name, schedule, true);
  });
  setInterval(check, 500);
})();
