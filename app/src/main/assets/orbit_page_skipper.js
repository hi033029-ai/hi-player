// ==UserScript==
// @name         Hi Browser Page Skipper
// @namespace    hi.browser
// @version      2.0
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
  var SAFE = /(^|\.)(youtube|google|gstatic|github|gitlab|wikipedia|wikimedia|facebook|instagram|reddit|netflix|twitch|spotify|whatsapp|microsoft|live|office|apple|icloud|amazon|paypal|stripe|linkedin|stackoverflow|duckduckgo|bing|yahoo|x|twitter|tiktok|telegram|discord|anthropic|claude|openai|chatgpt)\./i;
  if (SAFE.test(location.hostname + '.')) return;

  var _st = window.setTimeout, _si = window.setInterval, _open = window.open;
  var allowOpen = 0;

  // ---- Wait-page detection. Runs only from the throttled scan below (never
  // inside timers), skips big pages cheaply, and never treats pages with
  // forms (sign-in, codes, checkout) as wait pages.
  var WAIT = /(please wait|wait(ing)? (for )?\d+\s*(s|sec|secs|seconds?)\b|(continue|redirect(ing)?|skip(ping)?|link (will be )?(ready|available|generated))( you)? in \d+|\d+\s*(s|sec|secs|seconds?) (left|remaining)|count ?down|generating (your |the )?link|get (the |your )?link|link is (being )?generat|skip (ad|in)\b|verify you are human)/i;
  var isWaitPage = false;
  var decided = false;
  var checks = 0;

  function hasUserForm() {
    return !!document.querySelector('input[type=password],input[autocomplete="one-time-code"],input[type=email],input[type=tel],input[type=number],textarea,select,input[name*="card" i],input[name*="otp" i]');
  }
  function evaluateWait() {
    var b = document.body;
    if (!b) return false;
    if (document.getElementsByTagName('*').length > 2500) return false;
    if (hasUserForm()) return false;
    var txt = b.innerText || '';
    return txt.length < 6000 && WAIT.test(txt);
  }

  // ---- 1. Skip the time: accelerate timers while on a wait page
  // A countdown started by an inline script before the first scan triggers one
  // early check, so its timer is already sped up.
  function decideEarly(d) {
    if (!decided && document.body && typeof d === 'number' && d >= 900 && d <= 60000) {
      try { scan(); } catch (e) {}
    }
  }
  window.setTimeout = function (fn, d) {
    decideEarly(d);
    if (isWaitPage && typeof d === 'number' && d >= 900 && d <= 60000) {
      var a = [].slice.call(arguments);
      a[1] = Math.max(10, d / SPEED);
      return _st.apply(window, a);
    }
    return _st.apply(window, arguments);
  };
  window.setInterval = function (fn, d) {
    decideEarly(d);
    if (isWaitPage && typeof d === 'number' && d >= 500 && d <= 60000) {
      var a = [].slice.call(arguments);
      a[1] = Math.max(10, d / SPEED);
      return _si.apply(window, a);
    }
    return _si.apply(window, arguments);
  };

  // Countdowns that pause when the tab loses focus keep running (wait pages only).
  try {
    var hiddenDesc = Object.getOwnPropertyDescriptor(Document.prototype, 'hidden');
    var stateDesc = Object.getOwnPropertyDescriptor(Document.prototype, 'visibilityState');
    var hasFocus = document.hasFocus;
    if (hiddenDesc && hiddenDesc.get) Object.defineProperty(document, 'hidden', {
      configurable: true, get: function () { return isWaitPage ? false : hiddenDesc.get.call(document); }
    });
    if (stateDesc && stateDesc.get) Object.defineProperty(document, 'visibilityState', {
      configurable: true, get: function () { return isWaitPage ? 'visible' : stateDesc.get.call(document); }
    });
    document.hasFocus = function () { return isWaitPage ? true : hasFocus.call(document); };
    window.addEventListener('blur', function (e) { if (isWaitPage) e.stopImmediatePropagation(); }, true);
    document.addEventListener('visibilitychange', function (e) { if (isWaitPage) e.stopImmediatePropagation(); }, true);
  } catch (e) {}

  // ---- 2. Pop-ups / pop-unders from fake buttons
  window.open = function () {
    if (isWaitPage && Date.now() > allowOpen) return null;
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
      } else if (BAD.test(txt) && !sameSiteOrScript(el)) {
        // Fake "Play / Install / Update" buttons lead off-site; real controls don't.
        el.style.setProperty('display', 'none', 'important');
      }
    }
  }

  var observer = null;
  var stopped = false;
  function stop() {
    stopped = true;
    if (observer) { try { observer.disconnect(); } catch (e) {} }
  }

  function scan() {
    if (stopped) return;
    checks++;
    if (document.body) decided = true;
    isWaitPage = evaluateWait();
    if (!isWaitPage) {
      // Big pages are never wait pages: stop watching them at once.
      if (document.getElementsByTagName('*').length > 2500 || checks > 40) stop();
      return;
    }
    styleWaitPage();
    unlockScroll();
    removeOverlays();
    handleButtons();
  }

  var pending = 0;
  function schedule() {
    if (pending || stopped) return;
    pending = _st.call(window, function () { pending = 0; try { scan(); } catch (e) {} }, 500);
  }
  try {
    observer = new MutationObserver(schedule);
    observer.observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}
  document.addEventListener('DOMContentLoaded', function () { try { scan(); } catch (e) {} });
  window.addEventListener('load', schedule);
  _st.call(window, stop, 60000);
})();
