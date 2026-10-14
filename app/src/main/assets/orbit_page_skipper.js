// ==UserScript==
// @name         Orbit Page Skipper
// @namespace    orbit.browser
// @version      1.0
// @description  Speeds up countdown/wait pages, hides ads, banners and fake buttons
// @match        *://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==
(function () {
  'use strict';
  if (window.top !== window || window.__orbitSkipper) return;
  window.__orbitSkipper = true;

  var SPEED = 20; // countdown speed-up factor on wait pages
  var SAFE = /(^|\.)(youtube|google|github|gitlab|wikipedia|facebook|instagram|reddit|netflix|twitch|spotify|whatsapp|microsoft|live|apple|amazon|paypal|linkedin|stackoverflow|duckduckgo|bing|anthropic|claude)\./i;
  if (SAFE.test(location.hostname + '.')) return;

  var _st = window.setTimeout, _si = window.setInterval, _open = window.open;
  var allowOpen = 0;

  // ---- Wait-page detection (small page that talks about waiting / seconds / link generation)
  var WAIT = /(please wait|wait \d+|\d+\s*(seconds?|secs?)\b|count ?down|generating (your |the )?link|get link|link is (being )?generat|continue in|skip (ad|in)|verify you are human|click (here )?to continue)/i;
  var cache = { t: 0, v: false };
  function isWait() {
    var now = Date.now();
    if (now - cache.t < 700) return cache.v;
    cache.t = now;
    var b = document.body, txt = b ? b.innerText : '';
    cache.v = !!b && txt.length < 6000 && WAIT.test(txt);
    return cache.v;
  }

  // ---- 1. Skip the time: accelerate timers while on a wait page
  window.setTimeout = function (fn, d) {
    var a = [].slice.call(arguments);
    if (typeof d === 'number' && d >= 900 && d <= 60000 && isWait()) a[1] = Math.max(10, d / SPEED);
    return _st.apply(window, a);
  };
  window.setInterval = function (fn, d) {
    var a = [].slice.call(arguments);
    if (typeof d === 'number' && d >= 500 && d <= 60000 && isWait()) a[1] = Math.max(10, d / SPEED);
    return _si.apply(window, a);
  };

  // Countdowns that pause when the tab loses focus keep running
  try {
    Object.defineProperty(document, 'hidden', { get: function () { return false; } });
    Object.defineProperty(document, 'visibilityState', { get: function () { return 'visible'; } });
    document.hasFocus = function () { return true; };
    window.addEventListener('blur', function (e) { e.stopImmediatePropagation(); }, true);
    document.addEventListener('visibilitychange', function (e) { e.stopImmediatePropagation(); }, true);
  } catch (e) {}

  // ---- 2. Pop-ups / pop-unders from fake buttons
  window.open = function () {
    if (isWait() && Date.now() > allowOpen) return null;
    return _open.apply(window, arguments);
  };

  // ---- 3. Ads and banners
  function addStyle(css) {
    try {
      var s = document.createElement('style');
      s.textContent = css;
      (document.head || document.documentElement).appendChild(s);
    } catch (e) {}
  }
  addStyle('ins.adsbygoogle,iframe[src*="doubleclick"],iframe[src*="googlesyndication"],iframe[src*="adsterra"],' +
    '[id^="google_ads"],[id^="div-gpt-ad"],[class*="adsbox"],[class*="ad-banner"],[class*="adsbygoogle"],' +
    'a[href*="doubleclick.net"],a[href*="adsterra"],a[href*="popads"],[id*="taboola"],[id*="outbrain"]{display:none!important}');

  var waitStyled = false;
  function styleWaitPage() {
    if (waitStyled) return;
    waitStyled = true;
    addStyle('[class*="banner"],[id*="banner"],[class*="advert"],[id*="advert"],[class*="sponsor"],' +
      'iframe:not([src*="captcha"]):not([src*="turnstile"]):not([src*="challenges.cloudflare"]){display:none!important}');
    addStyle('a[target="_blank"]:has(> img){display:none!important}'); // ad tiles / fake thumbnails
  }

  function unlockScroll() {
    [document.documentElement, document.body].forEach(function (el) {
      if (el) el.style.setProperty('overflow', 'auto', 'important');
    });
  }

  // Full-screen overlays (not captchas)
  function removeOverlays() {
    var els = document.querySelectorAll('div,section,aside,a,iframe'), n = Math.min(els.length, 400);
    for (var i = 0; i < n; i++) {
      var el = els[i], cs = getComputedStyle(el);
      if ((cs.position === 'fixed' || cs.position === 'absolute') && parseInt(cs.zIndex, 10) >= 999) {
        var r = el.getBoundingClientRect();
        if (r.width > innerWidth * 0.6 && r.height > innerHeight * 0.6 &&
            !el.querySelector('input,form,.g-recaptcha,.h-captcha,.cf-turnstile') &&
            !/captcha|turnstile/i.test(el.outerHTML.slice(0, 300))) el.remove();
      }
    }
  }

  // ---- 4. Fake buttons vs the real one
  var GOOD = /^\s*(get (the |your )?link|continue|click (here )?to continue|go to (the )?link|proceed|skip( ad)?|unlock( link)?|open link|continue to (the )?(link|destination|website))\s*[»>→]*\s*$/i;
  var BAD = /(install|play|watch|update|flash|scan|virus|vpn|prize|allow notifications|start now|claim)/i;

  function visible(el) {
    var r = el.getBoundingClientRect(), cs = getComputedStyle(el);
    return r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && cs.display !== 'none';
  }
  function sameSiteOrScript(el) {
    if (el.tagName !== 'A') return true;
    var h = el.getAttribute('href') || '';
    if (!h || h === '#' || /^javascript:/i.test(h)) return true;
    try { return new URL(h, location.href).hostname === location.hostname; } catch (e) { return false; }
  }
  function handleButtons() {
    var els = document.querySelectorAll('a,button,input[type=button],input[type=submit],div[role=button]');
    for (var i = 0; i < els.length; i++) {
      var el = els[i], txt = (el.innerText || el.value || '').trim();
      if (!txt || txt.length > 40) continue;
      if (GOOD.test(txt)) {
        if (!el.__orbitClicked && !el.disabled && visible(el) && sameSiteOrScript(el)) {
          el.__orbitClicked = true;
          allowOpen = Date.now() + 1500;
          el.click();
        }
      } else if (BAD.test(txt)) {
        el.style.setProperty('display', 'none', 'important');
      }
    }
  }

  function scan() {
    if (!isWait()) return;
    styleWaitPage();
    unlockScroll();
    removeOverlays();
    handleButtons();
  }

  var pending = 0;
  function schedule() {
    if (pending) return;
    pending = _st.call(window, function () { pending = 0; try { scan(); } catch (e) {} }, 400);
  }
  var obs = new MutationObserver(schedule);
  obs.observe(document.documentElement, { childList: true, subtree: true });
  document.addEventListener('DOMContentLoaded', schedule);
  _st.call(window, function () { obs.disconnect(); }, 120000);
})();
