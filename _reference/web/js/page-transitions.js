

(() => {
  const listeners = new Set();

  function state() {
    try {
      return JSON.parse(window.AndroidYT.getPlayerState() || '{}');
    } catch (_) {
      return {};
    }
  }

  function notify() {
    const s = state();
    listeners.forEach(fn => {
      try { fn(s); } catch (e) { console.error('[Jinka] player listener:', e); }
    });
  }

  window.onPersistentPlayerEvent = (json, type) => {
    let s = {};
    try { s = typeof json === 'string' ? JSON.parse(json) : json || {}; } catch (_) {}
    notify();
    if (type === 'ended') dispatchAudio('ended');
    else if (type === 'error') dispatchAudio('error');
    else {
      dispatchAudio('timeupdate');
      if (s.loading === false && s.duration > 0) dispatchAudio('loadedmetadata');
      dispatchAudio(s.paused ? 'pause' : 'play');
    }
  };

  const audioListeners = {};
  const audio = {
    get src() { return state().url || ''; },
    set src(url) {
      if (url) window.AndroidYT.playerLoad(url, '', '', '', false, '');
    },
    get currentTime() { return Number(state().position || 0); },
    set currentTime(v) { window.AndroidYT.playerSeek(Number(v) || 0); },
    get duration() { return Number(state().duration || 0); },
    get paused() { return state().paused !== false; },
    play() { window.AndroidYT.playerPlay(); return Promise.resolve(); },
    pause() { window.AndroidYT.playerPause(); },
    load() {},
    addEventListener(type, fn) {
      (audioListeners[type] ||= new Set()).add(fn);
    },
    removeEventListener(type, fn) {
      audioListeners[type]?.delete(fn);
    }
  };

  function dispatchAudio(type) {
    (audioListeners[type] || []).forEach(fn => {
      try { fn(); } catch (e) { console.error('[Jinka] audio event:', e); }
    });
  }

  window.JinkaAudio = audio;

  window.JinkaPlayer = {
    getState: state,
    subscribe(fn) {
      listeners.add(fn);
      fn(state());
      return () => listeners.delete(fn);
    },
    load(track, autoplay) {
      if (!track || !track.url) return;
      const s = state();
      if (s.url === track.url) {
        if (autoplay && s.paused) window.AndroidYT.playerPlay();
        else if (!autoplay && !s.paused) window.AndroidYT.playerPause();
        notify();
        return;
      }
      window.AndroidYT.playerLoad(
        track.url,
        track.title || '',
        track.artist || '',
        track.thumbnailHighUrl || track.thumbnailUrl || track.thumbnailFallbackUrl || '',
        !!autoplay,
        track.thumbnailFallbackUrl || ''
      );
    },
    play() { window.AndroidYT.playerPlay(); },
    pause() { window.AndroidYT.playerPause(); },
    seek(seconds) { window.AndroidYT.playerSeek(Math.max(0, Number(seconds) || 0)); },
    isPlaying() { return !state().paused; }
  };
})();
