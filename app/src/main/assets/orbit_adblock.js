// Hi Browser ad shield (document-start, every frame). Complements the native
// request filter: hides ad slots and banners, removes invisible click-hijack
// links that open ad tabs, and fast-forwards ads inside common HTML5 players.
// It never reads page data and never sends anything anywhere.
(function () {
  'use strict';
  if (window.__hiAdShieldV1) return;
  window.__hiAdShieldV1 = true;
  var host = (location.hostname || '').toLowerCase();
  // YouTube has its own ad skipper; sign-in and payment pages are left alone.
  if (/(^|\.)(youtube|youtube-nocookie)\.com$/.test(host)) return;
  if (/(^|\.)(accounts\.google|paypal|stripe|checkout\.stripe|appleid\.apple|login\.microsoftonline|login\.live)\.(com|net)$/.test(host)) return;

  function siteOf(h) {
    var p = (h || '').split('.');
    if (p.length <= 2) return h;
    var keep = (p[p.length - 1].length === 2 && p[p.length - 2].length <= 3) ? 3 : 2;
    return p.slice(-keep).join('.');
  }
  var SITE = siteOf(host);
  function crossSite(url) {
    try {
      var u = new URL(url, location.href);
      if (u.protocol !== 'http:' && u.protocol !== 'https:') return false;
      return siteOf(u.hostname.toLowerCase()) !== SITE;
    } catch (e) { return false; }
  }

  // ---- 1. Ad slots and banners (specific names only, so page layouts survive).
  var HIDE = [
    'ins.adsbygoogle', '.adsbygoogle', '[id^="google_ads_iframe"]', '[id^="div-gpt-ad"]', '[id^="gpt-ad-"]',
    '[data-google-query-id]', '[data-ad-slot]', '[data-ad-unit]', '[data-adunit]', '[data-ad-client]',
    'iframe[src*="doubleclick.net"]', 'iframe[src*="googlesyndication.com"]', 'iframe[src*="amazon-adsystem.com"]',
    'iframe[src*="adsterra"]', 'iframe[src*="exoclick"]', 'iframe[src*="juicyads"]', 'iframe[src*="trafficjunky"]',
    'iframe[src*="popads"]', 'iframe[src*="propellerads"]', 'iframe[id^="aswift_"]',
    'a[href*="doubleclick.net/"]', 'a[href*="googleadservices.com/"]', 'a[href*="adsterra"]', 'a[href*="popads.net"]',
    'a[href*="exoclick.com"]', 'a[href*="clickadu"]', 'a[href*="propellerads"]',
    '.ad-banner', '.ad-container', '.ad-wrapper', '.ad-slot', '.ad-unit', '.ad-box', '.adbox', '.adsbox',
    '.ad-placeholder', '.ad-leaderboard', '.ad-sidebar', '.ad-footer', '.ad-header', '.ad-overlay', '.ad-popup',
    '.banner-ad', '.banner-ads', '.sticky-ad', '.anchor-ad', '.adhesion-ad', '.popup-ad', '.sponsored-ad',
    '.advertisement', '.advertising', '.advert', '.adsense', '.ezoic-ad', '.adthrive-ad', '.mv-ad-box',
    '#ad-banner', '#ad-container', '#adbox', '#ads-container', '#sticky-ad', '#banner-ad',
    '[aria-label="Advertisement" i]', '[aria-label="Ads" i]',
    '.taboola', '[id^="taboola-"]', '.trc_rbox_container', '.OUTBRAIN', '.ob-widget', '.ob-smartfeed-wrapper',
    '[id^="mgid_"]', '.mgbox', '[id^="rcjsload"]', '.revcontent', '[id^="adngin-"]', '[id^="ezoic-pub-ad"]',
    // Ads inside HTML5 players (JW Player, Video.js, Flowplayer, Plyr, IMA, VAST overlays).
    '.jw-flag-ads .jw-controls-backdrop', '.vjs-ad-playing .vjs-ad-overlay', '.ima-ad-container', '.videoAdUi',
    '.fp-ad', '.plyr__ads', '.vast-blocker', '.preroll-ad', '.video-ad-overlay'
  ].join(',') + '{display:none!important;visibility:hidden!important;pointer-events:none!important}';

  function addStyle(css) {
    try {
      var s = document.createElement('style');
      s.setAttribute('data-hi-adshield', '');
      s.textContent = css;
      (document.head || document.documentElement).appendChild(s);
      return true;
    } catch (e) { return false; }
  }
  if (!document.documentElement || !addStyle(HIDE)) {
    document.addEventListener('DOMContentLoaded', function () { addStyle(HIDE); });
  }

  // ---- 2. Click-hijack links: invisible full-page links to other sites that
  // turn the first tap anywhere into an ad tab.
  function hijackOverlay(el) {
    if (!el || el === document.body || el === document.documentElement || !el.getBoundingClientRect) return false;
    var r = el.getBoundingClientRect();
    if (r.width < innerWidth * 0.6 || r.height < innerHeight * 0.4) return false;
    var cs = getComputedStyle(el);
    if (cs.position !== 'fixed' && cs.position !== 'absolute') return false;
    var invisible = parseFloat(cs.opacity) < 0.1 ||
      (cs.backgroundColor === 'rgba(0, 0, 0, 0)' && cs.backgroundImage === 'none' &&
        !(el.textContent || '').trim() && !el.querySelector('img,video,canvas,svg,picture'));
    return invisible || (parseInt(cs.zIndex, 10) || 0) >= 2147480000;
  }
  function onTap(e) {
    if (!e.isTrusted) return;
    var t = e.target;
    var a = t && t.closest ? t.closest('a[href]') : null;
    if (!a || !crossSite(a.href)) return;
    if (hijackOverlay(a) || (t !== a && hijackOverlay(t))) {
      e.preventDefault();
      e.stopImmediatePropagation();
      try { a.style.setProperty('display', 'none', 'important'); } catch (err) {}
    }
  }
  document.addEventListener('click', onTap, true);
  document.addEventListener('auxclick', onTap, true);

  // Scripts that build a hidden link to another site and click it themselves.
  try {
    var nativeClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
      try {
        var target = (this.getAttribute('target') || '').toLowerCase();
        if (!this.isConnected && target === '_blank' && crossSite(this.href)) return;
      } catch (e) {}
      return nativeClick.apply(this, arguments);
    };
  } catch (e) {}

  // ---- 3. Standard-size ad frames from other sites (300x250, 728x90, ...).
  var AD_SIZES = [[300, 250], [728, 90], [320, 50], [320, 100], [160, 600], [300, 600], [970, 250], [970, 90], [468, 60], [336, 280], [300, 50], [300, 100]];
  function adSized(w, h) {
    for (var i = 0; i < AD_SIZES.length; i++) {
      if (Math.abs(AD_SIZES[i][0] - w) <= 2 && Math.abs(AD_SIZES[i][1] - h) <= 2) return true;
    }
    return false;
  }
  function sweepFrames() {
    var frames = document.getElementsByTagName('iframe');
    for (var i = 0; i < frames.length && i < 60; i++) {
      var f = frames[i];
      if (f.__hiAdChecked) continue;
      var src = f.getAttribute('src') || '';
      if (!src || /captcha|turnstile|challenges\.cloudflare|recaptcha|hcaptcha/i.test(src)) { f.__hiAdChecked = true; continue; }
      var w = f.offsetWidth, h = f.offsetHeight;
      if (!w || !h) continue;
      f.__hiAdChecked = true;
      if (crossSite(src) && adSized(w, h)) f.style.setProperty('display', 'none', 'important');
    }
  }

  // ---- 4. Ads inside HTML5 players: click "Skip ad", otherwise mute and jump to the end.
  var SKIP = '.jw-skip:not(.jw-skip-disabled),.videoAdUiSkipButton,.vast-skip-button,.vast-skip,.vjs-ad-skip,' +
    '.fp-ad-skip,.plyr__ads-skip,[class*="skip-ad" i],[class*="skipad" i],[id*="skip-ad" i],[aria-label="Skip Ad" i]';
  var AD_PLAYER = '.jw-flag-ads,.vjs-ad-playing,.fp-ad-playing,.plyr--ad,.ima-ad-container,[class*="preroll" i]';
  function skipPlayerAds() {
    var buttons = document.querySelectorAll(SKIP);
    for (var i = 0; i < buttons.length && i < 10; i++) {
      var b = buttons[i];
      if (b.offsetParent !== null && !b.disabled) { try { b.click(); } catch (e) {} }
    }
    var players = document.querySelectorAll(AD_PLAYER);
    for (var j = 0; j < players.length && j < 6; j++) {
      var v = players[j].tagName === 'VIDEO' ? players[j] : players[j].querySelector('video');
      if (!v || !isFinite(v.duration) || !(v.duration > 0) || v.duration > 180) continue;
      try { v.muted = true; } catch (e) {}
      try { v.playbackRate = 16; } catch (e) {}
      try { if (v.currentTime < v.duration - 0.3) v.currentTime = v.duration - 0.1; } catch (e) {}
    }
  }

  var pending = 0;
  var _st = window.setTimeout;
  function sweep() {
    pending = 0;
    try { sweepFrames(); } catch (e) {}
    try { if (document.getElementsByTagName('video').length) skipPlayerAds(); } catch (e) {}
  }
  function schedule() {
    if (!pending) pending = _st.call(window, sweep, 300);
  }
  function start() {
    try {
      new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
    } catch (e) {}
    schedule();
    // Players switch to ad mode without DOM insertions, so check while videos exist.
    var ticks = 0;
    var timer = window.setInterval.call(window, function () {
      ticks++;
      if (document.getElementsByTagName('video').length) { try { skipPlayerAds(); } catch (e) {} }
      if (ticks > 1800) window.clearInterval(timer);
    }, 1000);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
