// ==UserScript==
// @name         Hi Browser YouTube Ad Skipper
// @namespace    hi.browser
// @version      3.1
// @description  Best-effort ad response cleanup and event-driven skipping of recognized YouTube ads
// @match        *://*.youtube.com/*
// @run-at       document-start
// @grant        none
// ==/UserScript==
(function () {
  'use strict';
  // Fallback callbacks can still execute in the outgoing document. Never
  // install player/JSON hooks on an unrelated site's document.
  var host = (location.hostname || '').toLowerCase();
  if (!/(^|\.)(youtube|youtube-nocookie)\.com$/.test(host)) return;
  if (window.__orbitYT) return;
  window.__orbitYT = true;

  // ---- 1. Strip ad data from player responses before the player reads them.
  // The ad video then never loads, so there is no ad to fast-forward and no
  // loading spinner between a skipped ad and the real video.
  var AD_KEYS = ['adPlacements', 'adSlots', 'playerAds', 'adBreakHeartbeatParams'];
  function strip(o, depth) {
    if (!o || typeof o !== 'object') return o;
    depth = depth || 0;
    try {
      if (Array.isArray(o)) {
        if (depth < 2) for (var j = 0; j < o.length && j < 8; j++) strip(o[j], depth + 1);
        return o;
      }
      for (var i = 0; i < AD_KEYS.length; i++) {
        var k = AD_KEYS[i];
        if (Object.prototype.hasOwnProperty.call(o, k)) {
          try { delete o[k]; } catch (e) { try { o[k] = undefined; } catch (e2) {} }
        }
      }
      if (depth < 2 && o.playerResponse && typeof o.playerResponse === 'object') strip(o.playerResponse, depth + 1);
    } catch (e) {}
    return o;
  }
  function hasAds(text) {
    return typeof text === 'string' &&
      (text.indexOf('"adPlacements"') >= 0 || text.indexOf('"playerAds"') >= 0 || text.indexOf('"adSlots"') >= 0);
  }
  function isPlayerUrl(url) {
    url = String(url || '');
    return url.indexOf('/youtubei/v1/player') >= 0 || url.indexOf('/watch') >= 0 || url.indexOf('/get_watch') >= 0;
  }

  var origParse = JSON.parse;
  try {
    var wrappedParse = function () {
      var result = origParse.apply(this, arguments);
      if (result && typeof result === 'object' &&
          (Array.isArray(result) || result.adPlacements || result.playerAds || result.adSlots || result.playerResponse)) {
        strip(result, 0);
      }
      return result;
    };
    wrappedParse.toString = function () { return origParse.toString(); };
    JSON.parse = wrappedParse;
  } catch (e) {}

  function cleanText(text) {
    if (!hasAds(text)) return text;
    try {
      var prefix = '';
      var body = text;
      if (body.indexOf(")]}'") === 0) {
        var nl = body.indexOf('\n');
        prefix = body.slice(0, nl + 1);
        body = body.slice(nl + 1);
      }
      return prefix + JSON.stringify(strip(origParse(body), 0));
    } catch (e) {
      return text;
    }
  }

  try {
    var origJson = Response.prototype.json;
    Response.prototype.json = function () {
      return origJson.apply(this, arguments).then(function (data) { return strip(data, 0); });
    };
    var origText = Response.prototype.text;
    Response.prototype.text = function () {
      var promise = origText.apply(this, arguments);
      if (!isPlayerUrl(this.url)) return promise;
      return promise.then(cleanText);
    };
  } catch (e) {}

  try {
    var xhrOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function (method, url) {
      try { this.__hiYtUrl = String(url || ''); } catch (e) {}
      return xhrOpen.apply(this, arguments);
    };
    var textDesc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText');
    var respDesc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'response');
    var cleanXhr = function (xhr, value) {
      if (typeof value !== 'string' || xhr.readyState !== 4 || !isPlayerUrl(xhr.__hiYtUrl)) return value;
      if (xhr.__hiYtRaw === value) return xhr.__hiYtClean;
      var cleaned = cleanText(value);
      xhr.__hiYtRaw = value;
      xhr.__hiYtClean = cleaned;
      return cleaned;
    };
    if (textDesc && textDesc.get) {
      Object.defineProperty(XMLHttpRequest.prototype, 'responseText', {
        configurable: true, enumerable: textDesc.enumerable,
        get: function () { return cleanXhr(this, textDesc.get.call(this)); }
      });
    }
    if (respDesc && respDesc.get) {
      Object.defineProperty(XMLHttpRequest.prototype, 'response', {
        configurable: true, enumerable: respDesc.enumerable,
        get: function () {
          var value = respDesc.get.call(this);
          if (typeof value === 'string') return cleanXhr(this, value);
          if (value && this.responseType === 'json' && isPlayerUrl(this.__hiYtUrl)) return strip(value, 0);
          return value;
        }
      });
    }
  } catch (e) {}

  // The first video's data is embedded in the page as a global variable.
  ['ytInitialPlayerResponse'].forEach(function (name) {
    try {
      var current = window[name];
      if (current) strip(current, 0);
      Object.defineProperty(window, name, {
        configurable: true,
        get: function () { return current; },
        set: function (value) { current = strip(value, 0); }
      });
    } catch (e) {}
  });

  // ---- 2. Hide feed/banner slots, NOT the player controls that contain Skip.
  // document-start may run before documentElement exists; retry on discovery.
  var styleInstalled = false;
  function ensureStyle() {
    if (styleInstalled || !(document.head || document.documentElement)) return;
    try {
      var style = document.createElement('style');
      style.textContent = '#masthead-ad,#player-ads,.ytp-ad-overlay-container,.ytp-ad-overlay-slot,' +
        'ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,ytd-banner-promo-renderer,' +
        'ytd-promoted-sparkles-web-renderer,ytd-promoted-video-renderer,ytd-display-ad-renderer,' +
        'ytd-companion-slot-renderer,ytm-promoted-sparkles-web-renderer,' +
        'ytm-promoted-sparkles-text-search-renderer,ytm-promoted-video-renderer,' +
        'ytm-companion-slot,ytm-companion-ad-renderer,ad-slot-renderer,ytm-ad-slot-renderer' +
        '{display:none!important}';
      (document.head || document.documentElement).appendChild(style);
      styleInstalled = true;
    } catch (e) {}
  }

  // ---- 3. React to player/ad-control changes immediately; a timer is only a
  // safety net. No 1-second wait for a new Skip button or SPA-replaced player.
  var PLAYER = '.html5-video-player,#movie_player,#player-container-id,#player';
  var SKIP = '.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,' +
    '.ytp-ad-skip-button-container button,.ytm-ad-skip-button-container button,' +
    'ytm-skip-ad-renderer button,ytm-ad-skip-button-renderer button,' +
    '.ytp-ad-skip-button-slot button,button[class*="skip-ad"],button[class*="ad-skip"]';
  var player = null;
  var adState = null;
  var queued = false;
  var timer = null;
  var playerObserver = null;
  var discoveryObserver = null;
  var lastSkip = null;
  var lastSkipAt = 0;

  function visible(element) {
    try {
      if (!element || element.disabled || element.getAttribute('aria-disabled') === 'true' ||
          !element.getClientRects().length) return false;
      var style = window.getComputedStyle(element);
      return style.display !== 'none' && style.visibility !== 'hidden' && style.visibility !== 'collapse';
    } catch (e) { return false; }
  }
  function visibleSkip(p) {
    var buttons = p.querySelectorAll(SKIP);
    for (var i = 0; i < buttons.length; i++) if (visible(buttons[i])) return buttons[i];
    return null;
  }
  function restoreVideo() {
    if (!adState) return;
    var state = adState;
    adState = null;
    try { state.video.muted = state.muted; } catch (e) {}
    try { state.video.playbackRate = state.rate; } catch (e) {}
    lastSkip = null;
    lastSkipAt = 0;
  }
  function queueCheck() {
    if (queued) return;
    queued = true;
    setTimeout(function () { queued = false; check(); }, 0);
  }
  function watch(p) {
    if (playerObserver) playerObserver.disconnect();
    if (!p || !playerObserver) return;
    try { playerObserver.observe(p, {
      attributes: true, attributeFilter: ['class', 'disabled', 'aria-disabled', 'style', 'hidden'],
      childList: true, subtree: true
    }); } catch (e) {}
  }
  function scheduleFallback() {
    if (timer !== null) clearTimeout(timer);
    timer = setTimeout(check, adState ? 120 : (document.hidden ? 2000 : 750));
  }
  function check() {
    ensureStyle();
    if (!player || !player.isConnected) {
      restoreVideo();
      player = document.querySelector(PLAYER);
      watch(player);
    }
    if (!player) { scheduleFallback(); return; }
    var p = player;
    var video = p.querySelector('video');
    if (adState && adState.video !== video) restoreVideo();
    var skip = visibleSkip(p);
    var showing = p.classList.contains('ad-showing') ||
      p.classList.contains('ad-interrupting') || !!p.querySelector('.ad-showing,.ad-interrupting') || !!skip;
    if (!showing || !video) {
      restoreVideo();
      scheduleFallback();
      return;
    }
    if (!adState) adState = { video: video, muted: video.muted, rate: video.playbackRate || 1 };
    // Try the real available control first. Disabled/countdown controls are not
    // treated as skippable, and repeated mutations cannot spam the same button.
    var now = Date.now();
    if (skip) {
      if (skip !== lastSkip || now - lastSkipAt >= 300) {
        lastSkip = skip;
        lastSkipAt = now;
        try { skip.click(); } catch (e) {}
      }
      // A click can synchronously switch to content while the old ad class
      // lingers. Never seek/mute in the same turn after invoking Skip.
      if (!(p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting')) &&
          !visibleSkip(p)) restoreVideo();
      scheduleFallback();
      return;
    }
    try { if (!video.muted) video.muted = true; } catch (e) {}
    try { if (video.playbackRate < 15.9) video.playbackRate = 16; } catch (e) {}
    // Only a recognized ad with a finite duration is seeked. Unknown/live
    // durations wait for a real control/metadata rather than skipping content.
    if (isFinite(video.duration) && video.duration > 0 && video.currentTime < video.duration - 0.3) {
      try { video.currentTime = Math.max(0, video.duration - 0.05); } catch (e) {}
    }
    scheduleFallback();
  }

  try {
    playerObserver = new MutationObserver(queueCheck);
    discoveryObserver = new MutationObserver(function () {
      // Cheap connectivity check once attached: do not scan the whole feed on
      // every mutation. The player observer handles controls below its root.
      if (!player || !player.isConnected || !styleInstalled) queueCheck();
    });
    discoveryObserver.observe(document, { childList: true, subtree: true });
  } catch (e) {}
  ['loadedmetadata', 'durationchange', 'playing', 'emptied', 'canplay', 'timeupdate'].forEach(function (name) {
    document.addEventListener(name, function (event) {
      if (event.target && event.target.tagName === 'VIDEO') queueCheck();
    }, true);
  });
  ['DOMContentLoaded', 'yt-navigate-finish', 'yt-page-data-updated', 'visibilitychange'].forEach(function (name) {
    document.addEventListener(name, queueCheck, true);
  });
  // Do not remove enforcement dialogs or force playback/reload on refusal:
  // that can hide a real site problem and strand the user on a blocked page.
  check();
})();
