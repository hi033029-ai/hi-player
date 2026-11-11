// ==UserScript==
// @name         Hi Browser Page Skipper
// @namespace    hi.browser
// @version      3.0
// @description  Speeds up countdown/wait pages, hides ads, banners and fake buttons
// @match        *://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==
(function () {
  'use strict';
  // These page helpers must not alter XML, SVG, or server error documents.
  if (document.contentType && document.contentType.toLowerCase() !== 'text/html') return;
  if (window.top !== window || window.__orbitSkipper) return;
  window.__orbitSkipper = true;

  var OPTIONS = window.__hiSkipOptions || {};
  // Gecko delivers per-site settings through a one-use DOM attribute before
  // injecting this file into the page's main world.
  if (!window.__hiSkipOptions) {
    try {
      var optionsNode = document.documentElement;
      var encodedOptions = optionsNode && optionsNode.getAttribute('data-hi-skip-options');
      if (encodedOptions) {
        OPTIONS = JSON.parse(encodedOptions);
        optionsNode.removeAttribute('data-hi-skip-options');
      }
    } catch (e) {}
  }
  var SPEED = [1, 10, 30, 60].indexOf(OPTIONS.speed) >= 0 ? OPTIONS.speed : 30; // countdown speed-up factor on wait pages
  var SAFE = /(^|\.)(youtube|google|gstatic|github|gitlab|wikipedia|wikimedia|facebook|instagram|reddit|netflix|twitch|spotify|whatsapp|microsoft|live|office|apple|icloud|amazon|paypal|stripe|linkedin|stackoverflow|duckduckgo|bing|yahoo|x|twitter|tiktok|telegram|discord|anthropic|claude|openai|chatgpt)\./i;
  if (SAFE.test(location.hostname + '.')) return;

  var _st = window.setTimeout, _si = window.setInterval, _ct = window.clearTimeout, _ci = window.clearInterval;
  var _open = window.open;
  var NativeDate = window.Date, _dateNow = NativeDate.now;
  var _raf = window.requestAnimationFrame;
  var _perf = (window.performance && performance.now) ? performance.now.bind(performance) : null;
  var allowOpen = 0;
  function realNow() { return _dateNow.call(NativeDate); }

  // ---- Wait-page detection. Runs only from the throttled scan below (never
  // inside timers), bounds the text sampled from large pages, and never treats pages with
  // forms (sign-in, codes, checkout) as wait pages.
  var WAIT = /(please wait|wait(ing)? (for )?\d+\s*(s|sec|secs|seconds?)\b|(continue|redirect(ing)?|skip(ping)?|link (will be )?(ready|available|generated))( you)? in \d+|\d+\s*(s|sec|secs|seconds?)\s*(left|remaining)\b|(wait|continue|redirect|skip|link).{0,25}\b\d{1,2}:\d{2}\b|count ?down|generating (your |the )?link|get (the |your )?link|link is (being )?generat|skip (ad|in)\b)/i;
  var WAIT_CONTEXT = /\b(wait|continue|redirect|skip|link|download|file|destination|generat|ready)\b/i;
  var TIMER_WIDGET = /^(?:\d{1,3}|\d{1,2}:\d{2})(?:\s*(?:s|sec|secs|seconds?))?$/i;
  var isWaitPage = false;
  var decided = false;
  var checks = 0;

  function hasSensitiveStep() {
    if (document.querySelector('input[type=password],input[autocomplete="one-time-code"],input[autocomplete^="cc-" i],input[type=tel],input[name*="card" i],input[name*="otp" i],iframe[src*="captcha" i],iframe[src*="challenge" i],[class*="captcha" i],[id*="captcha" i],[class*="turnstile" i],[id*="turnstile" i]')) return true;
    var headings = document.querySelectorAll('h1,h2,[role="heading"]');
    var headingText = String(document.title || '');
    for (var h = 0; h < Math.min(headings.length, 10); h++) headingText += ' ' + (headings[h].innerText || headings[h].textContent || '');
    if (/(consent|checkout|payment|security check|verify.*human|sign in|log in|create account|privacy choices)/i.test(headingText)) return true;
    // A newsletter/search email field alone is not a reason to disable the
    // helper; an authentication form containing its own login/verification CTA is.
    var forms = document.forms || [];
    for (var i = 0; i < Math.min(forms.length, 30); i++) {
      var form = forms[i];
      if (form.querySelector('input[type=email]') && /(sign in|log in|verify|send (a )?(code|link)|continue with email)/i.test(form.innerText || form.textContent || '')) return true;
    }
    var dialogs = document.querySelectorAll('[role="dialog"],[aria-modal="true"]');
    for (var j = 0; j < Math.min(dialogs.length, 20); j++) {
      var dialog = dialogs[j];
      if (visible(dialog) && /(consent|accept (all )?cookies|cookie preferences|checkout|payment|security check|verify.*human|sign in|log in)/i.test(dialog.innerText || dialog.textContent || '')) return true;
    }
    return false;
  }
  function evaluateWait() {
    var b = document.body;
    if (!b) return false;
    if (hasSensitiveStep()) return false;
    var txt = b.innerText || '';
    if (txt.length > 18000) txt = txt.slice(0, 9000) + ' ' + txt.slice(-9000);
    if (WAIT.test(txt)) return true;
    if (!WAIT_CONTEXT.test(txt)) return false;
    // Some sites expose a bare timer widget (for example, "15" or "00:15")
    // without putting the word "seconds" beside it.
    var widgets = document.querySelectorAll('#countdown,[id*="countdown" i],[class*="countdown" i],[data-countdown],[data-timer],[aria-label*="countdown" i]');
    for (var i = 0; i < Math.min(widgets.length, 20); i++) {
      var widget = widgets[i];
      if (visible(widget) && TIMER_WIDGET.test((widget.innerText || widget.textContent || '').trim())) return true;
    }
    return false;
  }

  // ---- 1. Skip the time.
  // Every timer goes through a small registry so countdowns that started before
  // the page was recognised (the usual case: the countdown script runs before
  // its text is on screen) are re-scheduled at full speed the moment it is.
  var timers = {};
  var seq = 0x40000000;
  function scaled(delay, repeat) {
    if (!isWaitPage || typeof delay !== 'number' || !(delay > 0)) return delay;
    var min = repeat ? 90 : 300;
    if (delay < min || delay > 120000) return delay;
    return Math.max(repeat ? 5 : 10, delay / SPEED);
  }
  function fire(t) {
    try { t.fn.apply(window, t.args); } catch (e) { _st.call(window, function () { throw e; }, 0); }
  }
  function arm(t, delay) {
    var d = scaled(delay, t.repeat);
    t.fast = isWaitPage;
    t.due = realNow() + (typeof d === 'number' ? d : 0);
    if (t.repeat) t.real = _si.call(window, function () { fire(t); }, d);
    else t.real = _st.call(window, function () { delete timers[t.id]; fire(t); }, d);
  }
  function register(fn, delay, args, repeat) {
    var id = ++seq;
    var t = timers[id] = { id: id, fn: fn, args: args, delay: Number(delay) || 0, repeat: repeat, real: 0, fast: false, due: 0 };
    arm(t, t.delay);
    return id;
  }
  function accelerateExisting() {
    var now = realNow();
    Object.keys(timers).forEach(function (key) {
      var t = timers[key];
      if (!t || t.fast) return;
      if (t.repeat) { _ci.call(window, t.real); arm(t, t.delay); }
      else { _ct.call(window, t.real); arm(t, Math.max(0, t.due - now)); }
    });
  }
  function decideEarly(d) {
    if (!decided && typeof d === 'number' && d >= 90 && d <= 120000) {
      try { scanSoon(); } catch (e) {}
    }
  }
  function fastSetTimeout(fn, d) {
    decideEarly(d);
    if (typeof fn !== 'function') return _st.apply(window, arguments);
    return register(fn, d, [].slice.call(arguments, 2), false);
  }
  function fastSetInterval(fn, d) {
    decideEarly(d);
    if (typeof fn !== 'function') return _si.apply(window, arguments);
    return register(fn, d, [].slice.call(arguments, 2), true);
  }
  function clearAny(id) {
    var t = timers[id];
    if (t) {
      delete timers[id];
      if (t.repeat) _ci.call(window, t.real); else _ct.call(window, t.real);
      return;
    }
    _ct.call(window, id);
  }
  window.setTimeout = fastSetTimeout;
  window.setInterval = fastSetInterval;
  window.clearTimeout = clearAny;
  window.clearInterval = clearAny;

  // Countdowns that compute "seconds left" from the clock (Date.now(),
  // new Date(), performance.now(), requestAnimationFrame) run fast too.
  var warping = false, warpReal = 0, warpPerf = 0;
  function warpedNow() { var r = realNow(); return warping ? warpReal + (r - warpReal) * SPEED : r; }
  function startWarp() {
    if (warping) return;
    warpReal = realNow();
    warpPerf = _perf ? _perf() : 0;
    warping = true;
    try {
      NativeDate.now = warpedNow;
      window.Date = new Proxy(NativeDate, {
        construct: function (target, args, newTarget) {
          return args.length ? Reflect.construct(target, args, newTarget) : Reflect.construct(target, [warpedNow()], newTarget);
        },
        apply: function () { return new NativeDate(warpedNow()).toString(); }
      });
    } catch (e) {}
    if (_perf) {
      try {
        performance.now = function () { var p = _perf(); return warpPerf + (p - warpPerf) * SPEED; };
        if (_raf) window.requestAnimationFrame = function (cb) {
          return _raf.call(window, function () { cb(performance.now()); });
        };
      } catch (e) {}
    }
  }
  function restoreTiming() {
    var now = realNow();
    isWaitPage = false;
    warping = false;
    NativeDate.now = _dateNow;
    window.Date = NativeDate;
    if (_perf) { try { performance.now = _perf; } catch (e) {} }
    if (_raf) window.requestAnimationFrame = _raf;
    Object.keys(timers).forEach(function (key) {
      var t = timers[key];
      if (!t) return;
      if (t.repeat) { _ci.call(window, t.real); arm(t, t.delay); }
      else { _ct.call(window, t.real); arm(t, Math.max(0, t.due - now) * (t.fast ? SPEED : 1)); }
    });
  }
  function onWaitPageDetected() {
    accelerateExisting();
    startWarp();
  }

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
    if (OPTIONS.autoContinue === false || hasSensitiveStep()) return;
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
    if (stopped) return;
    stopped = true;
    if (observer) { try { observer.disconnect(); } catch (e) {} }
    // Not a wait page: new timers go straight to the browser again. Clearing
    // stays wrapped so ids handed out earlier can still be cancelled.
    if (!isWaitPage) {
      if (window.setTimeout === fastSetTimeout) window.setTimeout = _st;
      if (window.setInterval === fastSetInterval) window.setInterval = _si;
    }
  }

  function scan() {
    if (stopped) return;
    checks++;
    lastScan = realNow();
    if (document.body) decided = true;
    if (hasSensitiveStep()) {
      restoreTiming();
      stop();
      return;
    }
    var wasWaitPage = isWaitPage;
    // Once a page is recognised it stays a wait page (its text changes as it counts down).
    isWaitPage = wasWaitPage || evaluateWait();
    if (isWaitPage && !wasWaitPage) onWaitPageDetected();
    if (!isWaitPage) {
      // Big pages are never wait pages: stop watching them at once.
      if (checks > 480) stop();
      return;
    }
    styleWaitPage();
    unlockScroll();
    removeOverlays();
    handleButtons();
  }

  var pending = 0;
  var lastScan = 0;
  function schedule() {
    if (pending || stopped) return;
    pending = _st.call(window, function () { pending = 0; try { scan(); } catch (e) {} }, 150);
  }
  // Called from timer creation: scan right away when the page has text, at most every 150 ms.
  function scanSoon() {
    if (stopped || !document.body) { schedule(); return; }
    if (realNow() - lastScan >= 150) scan(); else schedule();
  }
  try {
    observer = new MutationObserver(schedule);
    observer.observe(document.documentElement, { childList: true, subtree: true, characterData: true });
  } catch (e) {}
  document.addEventListener('DOMContentLoaded', function () { try { scan(); } catch (e) {} });
  document.addEventListener('readystatechange', function () { if (document.readyState === 'interactive') schedule(); });
  window.addEventListener('load', schedule);
  _st.call(window, stop, 120000);
})();
