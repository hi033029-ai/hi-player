// Deterministic source fixtures for the next approved validation run.
// Run: node tools/test_youtube_skipper.cjs (not a real YouTube/WebView test).
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(require('node:path').join(__dirname,
  '../app/src/main/assets/orbit_youtube.js'), 'utf8');

function fixture({ ad = false, duration = 300, mobile = false, host = 'm.youtube.com', initialResponse = undefined } = {}) {
  const observers = [], tasks = new Map(), events = new Map();
  let taskId = 0;
  const video = { tagName: 'VIDEO', muted: false, playbackRate: 1.25, duration, currentTime: 2 };
  function makePlayer(v, showing) {
    return { video: v, ad: showing, isConnected: true, buttons: [],
      classList: { contains(name) { return name === 'ad-showing' && this.owner.ad; }, owner: null },
      querySelector(selector) { return selector === 'video' ? this.video : null; },
      querySelectorAll(selector) { return mobile && !selector.includes('ytm-skip-ad-renderer') ? [] : this.buttons; } };
  }
  function connectedPlayer(v, showing) {
    const p = makePlayer(v, showing); p.classList.owner = p; return p;
  }
  let player = connectedPlayer(video, ad);
  const document = { hidden: false, head: { appendChild() {} }, documentElement: {},
    createElement() { return {}; }, querySelector(selector) {
      return mobile && !selector.includes('#player-container-id') ? null : player;
    },
    addEventListener(name, callback) { if (!events.has(name)) events.set(name, []); events.get(name).push(callback); } };
  const context = { document, location: { hostname: host }, ytInitialPlayerResponse: initialResponse, JSON: { parse: JSON.parse, stringify: JSON.stringify }, Date, console,
    MutationObserver: class {
      constructor(callback) { this.callback = callback; observers.push(this); }
      observe(target, options) { this.target = target; this.options = options; }
      disconnect() { this.target = null; }
    },
    setTimeout(callback, delay) { const id = ++taskId; tasks.set(id, { callback, delay }); return id; },
    clearTimeout(id) { tasks.delete(id); },
    getComputedStyle(element) { return { display: 'block', visibility: element.hidden ? 'hidden' : 'visible' }; } };
  context.window = context;
  vm.runInNewContext(source, context);
  function flush() {
    for (let i = 0; i < 20; i++) {
      const immediate = [...tasks].find(([, t]) => t.delay === 0);
      if (!immediate) return;
      tasks.delete(immediate[0]); immediate[1].callback();
    }
    throw new Error('Immediate callback loop');
  }
  function mutation(target) { observers.filter(o => o.target === target).forEach(o => o.callback([])); flush(); }
  return { video, document, parse: text => context.JSON.parse(text),
    get initialResponse() { return context.ytInitialPlayerResponse; }, get player() { return player; }, mutation,
    replace() {
      player.isConnected = false;
      const next = { tagName: 'VIDEO', muted: false, playbackRate: 1, duration: 600, currentTime: 10 };
      player = connectedPlayer(next, false); mutation(document); return next;
    }, event(name) { for (const cb of events.get(name) || []) cb({ target: video }); flush(); } };
}

{
  const f = fixture();
  assert.equal(f.video.currentTime, 2); assert.equal(f.video.muted, false); assert.equal(f.video.playbackRate, 1.25);
}
{
  const f = fixture({ ad: true, duration: 30 });
  assert.ok(f.video.currentTime > 29); assert.equal(f.video.muted, true);
  f.player.ad = false; f.mutation(f.player);
  assert.equal(f.video.muted, false); assert.equal(f.video.playbackRate, 1.25);
}
{
  const f = fixture({ ad: true, duration: 30 }); let clicks = 0;
  const skip = { disabled: false, visible: true, getAttribute() { return null; },
    getClientRects() { return this.visible ? [{}] : []; },
    click() { clicks++; this.visible = false; f.player.ad = false; f.video.currentTime = 0; } };
  f.player.buttons = [skip]; f.mutation(f.player);
  assert.equal(clicks, 1, 'Inserted Skip must be handled without waiting for a timer');
  assert.equal(f.video.currentTime, 0, 'Do not seek newly resumed content after Skip');
  assert.equal(f.video.muted, false); assert.equal(f.video.playbackRate, 1.25);
}
{
  const f = fixture({ ad: true, duration: Infinity });
  assert.equal(f.video.currentTime, 2, 'Unknown duration cannot be seeked');
  f.video.duration = 20; f.event('durationchange'); assert.ok(f.video.currentTime > 19);
}
{
  const f = fixture({ ad: true, duration: 30 });
  const next = f.replace();
  assert.equal(f.video.muted, false); assert.equal(f.video.playbackRate, 1.25);
  assert.equal(next.currentTime, 10); assert.equal(next.playbackRate, 1);
}
{
  const f = fixture({ ad: true, duration: 30 }); let clicks = 0;
  f.player.buttons = [{ disabled: true, getAttribute() { return null; }, getClientRects() { return [{}]; }, click() { clicks++; } }];
  f.mutation(f.player); assert.equal(clicks, 0, 'Disabled countdown controls are not skippable');
}

{
  const f = fixture({ ad: true, duration: Infinity, mobile: true }); let clicks = 0;
  f.player.buttons = [{ disabled: false, getAttribute() { return null; },
    getClientRects() { return [{}]; }, click() { clicks++; } }];
  f.mutation(f.player);
  f.mutation(f.player);
  assert.equal(clicks, 1, 'Mobile Skip is discovered immediately, without duplicate mutation clicks');
  assert.equal(f.video.currentTime, 2, 'Pending Skip must not seek content during its click cooldown');
}
{
  const f = fixture({ mobile: true }); let clicks = 0;
  f.player.buttons = [{ hidden: true, disabled: false, getAttribute() { return null; },
    getClientRects() { return [{}]; }, click() { clicks++; } }];
  f.mutation(f.player);
  assert.equal(clicks, 0, 'CSS-hidden Skip is not an available ad control');
  assert.equal(f.video.currentTime, 2, 'Ordinary mobile content must not be seeked');
}
{
  const original = { videoDetails: { videoId: 'kept' }, adPlacements: [{}],
    playerResponse: { playerAds: [{}], streamingData: { formats: [] } } };
  const f = fixture({ initialResponse: original });
  assert.equal(f.initialResponse.videoDetails.videoId, 'kept', 'Existing player response must be retained');
  assert.equal(f.initialResponse.adPlacements, undefined);
  assert.equal(f.initialResponse.playerResponse.playerAds, undefined);
  assert.ok(f.initialResponse.playerResponse.streamingData);
  const parsed = f.parse('{"adSlots":[{}],"videoDetails":{"videoId":"normal"}}');
  assert.equal(parsed.adSlots, undefined); assert.equal(parsed.videoDetails.videoId, 'normal');
}
{
  const f = fixture({ host: 'youtube.com.example.org', ad: true, duration: 30 });
  assert.equal(f.video.currentTime, 2, 'Outgoing/unrelated documents must not receive YouTube hooks');
  assert.equal(f.video.muted, false);
}
console.log('YouTube skipper deterministic fixtures passed (not live-site validation).');
