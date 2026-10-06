
(() => {
  const KEY = 'jinka:sleepTimerEndAt';

  function readEndAt() {
    try {
      const v = Number(localStorage.getItem(KEY));
      return Number.isFinite(v) && v > 0 ? v : null;
    } catch (_) { return null; }
  }

  function writeEndAt(v) {
    try {
      if (v) localStorage.setItem(KEY, String(v));
      else localStorage.removeItem(KEY);
    } catch (_) {  }
  }

  const listeners = new Set();

  function remaining() {
    const end = readEndAt();
    return end ? Math.max(0, Math.ceil((end - Date.now()) / 1000)) : 0;
  }

  function isActive() { return !!readEndAt(); }

  function notify() {
    const r = remaining();
    listeners.forEach((fn) => {
      try { fn(r, isActive()); } catch (e) { console.error('[Jinka] sleep-timer listener:', e); }
    });
  }

  function subscribe(fn) {
    listeners.add(fn);
    fn(remaining(), isActive());
    return () => listeners.delete(fn);
  }

  function start(mins) {
    const m = Number(mins);
    if (!Number.isFinite(m) || m <= 0) return false;
    writeEndAt(Date.now() + Math.round(m * 60000));
    notify();
    return true;
  }

  function cancel() {
    const had = isActive();
    writeEndAt(null);
    notify();
    return had;
  }

  function stopPlayback() {
    try {
      if (window.JinkaPlayer && typeof window.JinkaPlayer.pause === 'function') {
        window.JinkaPlayer.pause();
      }
    } catch (_) {  }
  }

  setInterval(() => {
    const end = readEndAt();
    if (!end) return;
    if (Date.now() >= end) {
      cancel();            // clear first -> fires exactly once across tabs
      stopPlayback();
      window.dispatchEvent(new CustomEvent('jinka:sleepElapsed'));
    } else {
      notify();
    }
  }, 1000);

  window.JinkaSleepTimer = { start, cancel, remaining, isActive, subscribe };
})();
