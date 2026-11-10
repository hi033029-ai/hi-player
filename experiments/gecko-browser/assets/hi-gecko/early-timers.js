"use strict";
(() => {
  if (window.top !== window || window.__hiEarlyTimers) return;
  const st = window.setTimeout, si = window.setInterval, ct = window.clearTimeout, ci = window.clearInterval;
  const now = Date.now.bind(Date), pending = new Map();
  let capture = true, adopted = false;
  const invoke = item => {
    if (!item.repeat) pending.delete(item.id);
    item.fn.apply(window, item.args);
  };
  function schedule(fn, delay, args, repeat) {
    const native = repeat ? si : st;
    if (!capture || typeof fn !== "function" || !Number.isFinite(delay) || delay < 90 || delay > 120000 || pending.size >= 256)
      return native.call(window, fn, delay, ...args);
    const item = {fn,args,repeat,delay,factor:1,due:now()+delay,id:0,real:0};
    item.id = item.real = native.call(window, () => invoke(item), delay);
    pending.set(item.id, item);
    return item.id;
  }
  function clear(id) {
    const item = pending.get(id);
    if (item) { pending.delete(id); (item.repeat ? ci : ct).call(window, item.real); }
    else ct.call(window,id);
  }
  const timeout = function(fn,delay,...args) { return schedule(fn,delay,args,false); };
  const interval = function(fn,delay,...args) { return schedule(fn,delay,args,true); };
  window.setTimeout = timeout; window.setInterval = interval;
  window.clearTimeout = clear; window.clearInterval = clear;
  const journal = {
    adopt() { adopted = true; capture = false; },
    scale(speed) {
      if (![1,10,30,60].includes(speed)) return;
      pending.forEach(item => {
        if (item.factor === speed) return;
        (item.repeat ? ci : ct).call(window,item.real);
        const remaining = item.repeat ? item.delay : Math.max(0,item.due-now())*item.factor;
        const delay = Math.max(item.repeat ? 5 : 10, remaining/speed);
        item.factor = speed; item.due = now()+delay;
        item.real = (item.repeat ? si : st).call(window, () => invoke(item), delay);
      });
    },
    disable() {
      capture = false;
      if (adopted) { journal.scale(1); return; }
      pending.clear();
      if (window.setTimeout === timeout) window.setTimeout = st;
      if (window.setInterval === interval) window.setInterval = si;
      if (window.clearTimeout === clear) window.clearTimeout = ct;
      if (window.clearInterval === clear) window.clearInterval = ci;
    }
  };
  Object.defineProperty(window,"__hiEarlyTimers",{value:journal,configurable:true});
  st.call(window, () => { if (!adopted) journal.disable(); },15000);
})();
