// ==UserScript==
// @name         Hi Browser YouTube Ad Skipper
// @namespace    hi.browser
// @version      2.0
// @description  Removes YouTube ads before they load, hides ad slots, skips any ad that still plays
// @match        *://*.youtube.com/*
// @run-at       document-start
// @grant        none
// ==/UserScript==
(function () {
  'use strict';
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

  // ---- 2. Hide ad slots / banners
  function addStyle(css) {
    try {
      var s = document.createElement('style');
      s.textContent = css;
      (document.head || document.documentElement).appendChild(s);
    } catch (e) {}
  }
  addStyle(
    '#masthead-ad,#player-ads,.video-ads,.ytp-ad-module,.ytp-ad-overlay-container,.ytp-ad-overlay-slot,ytd-ad-slot-renderer,' +
    'ytd-in-feed-ad-layout-renderer,ytd-banner-promo-renderer,ytd-promoted-sparkles-web-renderer,' +
    'ytd-promoted-video-renderer,ytd-display-ad-renderer,ytd-companion-slot-renderer,ytd-player-legacy-desktop-watch-ads-renderer,' +
    'ytd-statement-banner-renderer,ytd-primetime-promo-renderer,ytm-promoted-sparkles-web-renderer,ytm-promoted-sparkles-text-search-renderer,' +
    'ytm-promoted-video-renderer,ytm-companion-slot,ytm-companion-ad-renderer,ad-slot-renderer,ytm-ad-slot-renderer,' +
    'ytd-enforcement-message-view-model,tp-yt-iron-overlay-backdrop.opened{display:none!important}');
  addStyle('ytd-rich-item-renderer:has(ytd-ad-slot-renderer),ytm-rich-item-renderer:has(ad-slot-renderer),' +
    'ytm-item-section-renderer:has(ad-slot-renderer),ytm-item-section-renderer:has(ytm-ad-slot-renderer){display:none!important}');

  // ---- 3. Skip any ad that still gets through. Only the player's own class
  // list is observed (not the whole page), so the feed scrolls smoothly.
  var skipSelector = '.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,' +
    '.ytp-ad-skip-button-container button,button[class*="skip-ad"],button[class*="ad-skip"]';
  var player = null;
  var observed = null;
  var adState = null;
  var observer = null;
  try { observer = new MutationObserver(function () { check(); }); } catch (e) {}

  function watch(p) {
    if (!observer || observed === p) return;
    try {
      observer.disconnect();
      observer.observe(p, { attributes: true, attributeFilter: ['class'] });
      observed = p;
    } catch (e) {}
  }

  function check() {
    if (!player || !player.isConnected) {
      player = document.querySelector('.html5-video-player');
      if (!player) return;
      watch(player);
    }
    var p = player;
    var v = p.querySelector('video');
    var showing = p.classList.contains('ad-showing') || p.classList.contains('ad-interrupting');
    if (showing && v) {
      var skip = p.querySelector(skipSelector) || document.querySelector(skipSelector);
      if (skip) { try { skip.click(); } catch (e) {} }
      if (!adState) adState = { muted: v.muted, rate: v.playbackRate || 1 };
      v.muted = true;
      try { v.playbackRate = 16; } catch (e) {}
      if (isFinite(v.duration) && v.duration > 0 && v.currentTime < v.duration - 0.3) {
        try { v.currentTime = v.duration - 0.1; } catch (e) {}
      }
    } else if (adState && v) {
      v.muted = adState.muted;
      try { v.playbackRate = adState.rate; } catch (e) {}
      adState = null;
    }
    var close = p.querySelector('.ytp-ad-overlay-close-button');
    if (close) { try { close.click(); } catch (e) {} }
    var warn = document.querySelector('ytd-enforcement-message-view-model');
    if (warn) {
      warn.remove();
      document.querySelectorAll('tp-yt-iron-overlay-backdrop').forEach(function (b) { b.remove(); });
      if (v && v.paused) { try { v.play(); } catch (e) {} }
    }
  }

  document.addEventListener('loadedmetadata', check, true);
  document.addEventListener('playing', check, true);
  setInterval(check, 1000);
})();
