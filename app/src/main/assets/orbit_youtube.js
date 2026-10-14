// ==UserScript==
// @name         Hi Browser YouTube Ad Skipper
// @namespace    hi.browser
// @version      1.0
// @description  Strips ad data from YouTube player responses, skips leftover ads, hides ad slots
// @match        *://*.youtube.com/*
// @run-at       document-start
// @grant        none
// ==/UserScript==
(function () {
  'use strict';
  if (window.top !== window || window.__orbitYT) return;
  window.__orbitYT = true;

  // ---- 1. Remove ad data before the player reads it
  function strip(o) {
    if (!o || typeof o !== 'object') return o;
    if (o.adPlacements) delete o.adPlacements;
    if (o.playerAds) delete o.playerAds;
    if (o.adSlots) delete o.adSlots;
    if (o.playerResponse && typeof o.playerResponse === 'object') strip(o.playerResponse);
    if (o.response && typeof o.response === 'object' && o.response.adPlacements) strip(o.response);
    return o;
  }

  var _parse = JSON.parse;
  JSON.parse = function () {
    var r = _parse.apply(this, arguments);
    try { strip(r); } catch (e) {}
    return r;
  };

  try {
    var _json = Response.prototype.json;
    Response.prototype.json = function () {
      return _json.apply(this, arguments).then(function (r) { try { strip(r); } catch (e) {} return r; });
    };
  } catch (e) {}

  try {
    var _ipr;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function () { return _ipr; },
      set: function (v) { try { strip(v); } catch (e) {} _ipr = v; }
    });
  } catch (e) {}

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

  // ---- 3. Fallback: skip any ad that still plays
  var adState = null;
  function tick() {
    var p = document.querySelector('.html5-video-player');
    var v = document.querySelector('video.html5-main-video') || document.querySelector('video');
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

    var skip = document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-ad-skip-button-container button');
    if (skip) skip.click();
    var close = document.querySelector('.ytp-ad-overlay-close-button');
    if (close) close.click();

    // "Ad blockers are not allowed" pop-up: remove it and resume the video
    var warn = document.querySelector('ytd-enforcement-message-view-model');
    if (warn) {
      warn.remove();
      document.querySelectorAll('tp-yt-iron-overlay-backdrop').forEach(function (b) { b.remove(); });
      if (v && v.paused) { try { v.play(); } catch (e) {} }
    }
  }
  setInterval(tick, 250);
})();
