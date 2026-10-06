

if (window.__jinkaPlayerBooted) {
  if (typeof window.__jinkaRebindUI === 'function') window.__jinkaRebindUI();
} else {
  window.__jinkaPlayerBooted = true;

(function () {

  function getAud() {
    return (typeof window.JinkaAudio !== 'undefined' && window.JinkaAudio) ? window.JinkaAudio : null;
  }
  function getPlayer() {
    return (typeof window.JinkaPlayer !== 'undefined' && window.JinkaPlayer) ? window.JinkaPlayer : null;
  }
  function hasPlayer() {
    const p = getPlayer();
    return !!(p && typeof p.getState === 'function' && typeof p.load === 'function');
  }

  let aud = getAud();
  if (!aud) {
    console.error('[Jinka] window.JinkaAudio not available at init — player controls will no-op until a rebind finds it.');
  }

  let waveform = null;
  let waveformBars = [];
  let currentWaveformSeed = 'jinka-default';

  const WAVE_BAR_WIDTH = 3;
  const WAVE_BAR_GAP = 2;
  const WAVE_MIN_BARS = 24;
  const WAVE_MIN_H = 4;
  const WAVE_MAX_H = 34;

  function hashSeed(str) {
    let h = 2166136261 >>> 0;
    for (let i = 0; i < str.length; i++) {
      h ^= str.charCodeAt(i);
      h = Math.imul(h, 16777619);
    }
    return h >>> 0;
  }

  function mulberry32(seed) {
    let a = seed >>> 0;
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function generateWaveformHeights(seed, count) {
    const rand = mulberry32(hashSeed(seed));
    const waves = [1, 2, 3].map(() => ({
      freq: 1 + rand() * 5,
      phase: rand() * Math.PI * 2,
      amp: 0.4 + rand() * 0.6
    }));

    const heights = [];
    for (let i = 0; i < count; i++) {
      const x = (i / count) * Math.PI * 2;
      let v = 0;
      waves.forEach((w) => { v += Math.sin(x * w.freq + w.phase) * w.amp; });
      v = (v / waves.length + 1) / 2;
      v = v * 0.75 + rand() * 0.25;
      v = Math.max(0, Math.min(1, v));
      heights.push(Math.round(WAVE_MIN_H + v * (WAVE_MAX_H - WAVE_MIN_H)));
    }
    return heights;
  }

  function buildWaveform(seed) {
    if (!waveform) return;
    currentWaveformSeed = seed;

    const containerWidth = waveform.clientWidth || waveform.getBoundingClientRect().width || 320;
    const slot = WAVE_BAR_WIDTH + WAVE_BAR_GAP;
    const count = Math.max(WAVE_MIN_BARS, Math.floor((containerWidth + WAVE_BAR_GAP) / slot));

    const heights = generateWaveformHeights(seed, count);

    waveform.innerHTML = '';
    const frag = document.createDocumentFragment();
    heights.forEach((h) => {
      const bar = document.createElement('div');
      bar.className = 'waveform-bar';
      bar.style.height = h + 'px';
      frag.appendChild(bar);
    });
    waveform.appendChild(frag);
    waveformBars = Array.from(waveform.querySelectorAll('.waveform-bar'));
  }

  function seekFromClientX(clientX) {
    const a = getAud();
    if (!waveform || !a || !isFinite(a.duration) || a.duration <= 0) return;
    const rect = waveform.getBoundingClientRect();
    const fraction = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
    a.currentTime = fraction * a.duration;
    updateWaveformProgress(a.currentTime, a.duration);
    if (curTimeEl) curTimeEl.textContent = fmtTime(a.currentTime);
  }

  function updateWaveformProgress(currentTime, duration) {
    if (!waveformBars.length) return;
    const fraction = duration ? Math.min(1, Math.max(0, currentTime / duration)) : 0;
    const playedCount = Math.round(waveformBars.length * fraction);
    waveformBars.forEach((bar, i) => {
      if (i < playedCount) bar.classList.add('is-played');
      else bar.classList.remove('is-played');
    });
  }

  let waveformResizeTimer = null;
  window.addEventListener('resize', () => {
    clearTimeout(waveformResizeTimer);
    waveformResizeTimer = setTimeout(() => {
      buildWaveform(currentWaveformSeed);
      const a = getAud();
      if (a) updateWaveformProgress(a.currentTime, a.duration);
    }, 150);
  });

  let playToggle, iconPlay, iconPause;
  let favoriteToggle;
  let sheetBackdrop, lyricsSheet, lyricsList, lyricsToggle, lyricsClose, lyricsTrackTitle;
  let trackTitleEl, trackArtistEl, curTimeEl, durTimeEl, prevBtn, nextBtn, artworkImg;
  let repeatBtn;
  let navItems = [];

  function setPlayingUI(isPlaying) {
    if (!playToggle) return;
    playToggle.setAttribute('aria-pressed', String(isPlaying));
    playToggle.setAttribute('aria-label', isPlaying ? 'Pause' : 'Play');
    if (iconPlay && iconPause) {
      if (isPlaying) {
        iconPlay.setAttribute('hidden', '');
        iconPause.removeAttribute('hidden');
      } else {
        iconPlay.removeAttribute('hidden');
        iconPause.setAttribute('hidden', '');
      }
    }
  }

  function openSheet(sheet) {
    if (!sheet || !sheetBackdrop) return;
    sheetBackdrop.hidden = false;
    requestAnimationFrame(() => {
      sheetBackdrop.classList.add('is-open');
      sheet.classList.add('is-open');
    });
    sheet.setAttribute('aria-hidden', 'false');
  }

  function closeSheets() {
    if (lyricsSheet) {
      lyricsSheet.classList.remove('is-open');
      lyricsSheet.setAttribute('aria-hidden', 'true');
    }
    if (sheetBackdrop) {
      sheetBackdrop.classList.remove('is-open');
      setTimeout(() => { if (sheetBackdrop) sheetBackdrop.hidden = true; }, 250);
    }
    if (lyricsToggle) lyricsToggle.setAttribute('aria-pressed', 'false');
  }

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') closeSheets();
  });

  

  const THUMBNAIL_FIELDS = [
    'thumbnailHighUrl', 'thumbnail', 'thumb', 'cover', 'coverUrl',
    'artwork', 'image', 'art', 'thumbnailUrl'
  ];

  function normalizeArtworkText(value) {
    return String(value || '')
      .toLowerCase()
      .replace(/\([^)]*\)|\[[^\]]*\]/g, ' ')
      .replace(/official|audio|video|music|lyrics|visualizer|remix|version|full|hd|hq/g, ' ')
      .replace(/[^a-z0-9\u00c0-\u024f\u0980-\u09ff]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function artworkTokenScore(a, b) {
    const aa = normalizeArtworkText(a).split(' ').filter(Boolean);
    const bb = new Set(normalizeArtworkText(b).split(' ').filter(Boolean));
    if (!aa.length || !bb.size) return 0;
    let hits = 0;
    aa.forEach((x) => { if (bb.has(x)) hits++; });
    return hits / Math.max(aa.length, bb.size);
  }

  function getSoundCloudArtworkCandidates(url) {
    if (typeof url !== 'string' || !url.trim()) return [];
    const value = url.trim();
    const candidates = [];
    const add = (u) => { if (u && !candidates.includes(u)) candidates.push(u); };

    if (/-(large|t\d+x\d+|small|tiny)\.(jpg|jpeg|png|webp)(\?.*)?$/i.test(value)) {
      add(value.replace(/-(large|t\d+x\d+|small|tiny)(\.(?:jpg|jpeg|png|webp))(.*)$/i, '-t500x500$2$3'));
      add(value.replace(/-(large|t\d+x\d+|small|tiny)(\.(?:jpg|jpeg|png|webp))(.*)$/i, '-original$2$3'));
    }
    add(value);
    return candidates;
  }

  function findMatchedSoundCloudArtwork(track) {
    try {
      const raw = sessionStorage.getItem('jinka.search.results');
      if (!raw) return [];
      const results = JSON.parse(raw);
      if (!Array.isArray(results)) return [];

      const yt = results.find((r) => {
        if (!r || r.source === 'soundcloud') return false;
        if (track && track.url && r.playUrl && r.playUrl === track.url) return true;
        return normalizeArtworkText(r.title) === normalizeArtworkText(track && track.title) &&
          artworkTokenScore(r.artist, track && track.artist) >= 0.5;
      });

      if (!yt) return [];

      const ytTitle = normalizeArtworkText(yt.title);
      const ytArtist = normalizeArtworkText(yt.artist);
      const ytDuration = Number(yt.durationSeconds || 0);

      const scored = results
        .filter((r) => r && r.source === 'soundcloud' && r.thumbnailUrl)
        .map((r) => {
          const titleScore = artworkTokenScore(ytTitle, r.title);
          const artistScore = artworkTokenScore(ytArtist, r.artist);
          const scDuration = Number(r.durationSeconds || 0);
          const durationScore =
            ytDuration > 0 && scDuration > 0
              ? Math.max(0, 1 - Math.abs(ytDuration - scDuration) / Math.max(ytDuration, scDuration))
              : 0;
          const score = titleScore * 0.5 + artistScore * 0.35 + durationScore * 0.15;
          return { track: r, score, titleScore, artistScore, durationScore };
        })
        .sort((a, b) => b.score - a.score);

      const best = scored[0];
      if (!best) return [];

      const strongTextMatch =
        best.titleScore >= 0.65 &&
        best.artistScore >= 0.5 &&
        (best.durationScore >= 0.75 || !ytDuration || !best.track.durationSeconds);

      if (!strongTextMatch || best.score < 0.68) return [];

      return getSoundCloudArtworkCandidates(best.track.thumbnailUrl);
    } catch (_) {
      return [];
    }
  }

  function getTrackThumbnailCandidates(t) {
    const urls = [];
    const add = (value) => {
      if (typeof value === 'string' && value.trim() && !urls.includes(value.trim())) {
        urls.push(value.trim());
      }
    };
    if (t) {
      findMatchedSoundCloudArtwork(t).forEach(add);
      add(t.thumbnailHighUrl);
      add(t.thumbnailFallbackUrl);
      THUMBNAIL_FIELDS.slice(1).forEach((field) => add(t[field]));
    }
    return urls;
  }

  let artSanitizeCounter = 0;
  const artSanitizeCallbacks = {};

  window.onArtworkSanitized = function (requestId, resultUrl) {
    const cb = artSanitizeCallbacks[requestId];
    delete artSanitizeCallbacks[requestId];
    if (cb) cb(resultUrl);
  };

  function sanitizeArtworkUrl(url, callback) {
    if (!url || !/^https?:/i.test(url) ||
        typeof window.AndroidYT === 'undefined' || !window.AndroidYT ||
        typeof window.AndroidYT.sanitizeArtwork !== 'function') {
      callback(url);
      return;
    }
    artSanitizeCounter += 1;
    const requestId = 'art-' + Date.now() + '-' + artSanitizeCounter;
    artSanitizeCallbacks[requestId] = callback;
    try {
      window.AndroidYT.sanitizeArtwork(requestId, url);
    } catch (_) {
      delete artSanitizeCallbacks[requestId];
      callback(url);
    }
  }

  function setArtwork(t) {
    if (!artworkImg) return;
    const candidates = getTrackThumbnailCandidates(t);
    artworkImg.classList.remove('is-loaded');

    if (!candidates.length) {
      artworkImg.removeAttribute('src');
      return;
    }

    let candidateIndex = 0;
    const tryNext = () => {
      if (candidateIndex >= candidates.length) {
        artworkImg.removeAttribute('src');
        return;
      }
      const rawUrl = candidates[candidateIndex++];
      sanitizeArtworkUrl(rawUrl, (url) => {
        const probe = new Image();
        probe.onload = () => {
          if (probe.naturalWidth <= 120 || probe.naturalHeight <= 90) {
            tryNext();
            return;
          }
          artworkImg.onload = () => artworkImg.classList.add('is-loaded');
          artworkImg.onerror = () => {
            artworkImg.classList.remove('is-loaded');
            tryNext();
          };
          artworkImg.src = url;
        };
        probe.onerror = () => tryNext();
        probe.src = url;
      });
    };
    tryNext();
  }

  let PLAYER_TRACKS = [];
  let curIdx = 0;
  let lastPersistSave = 0;

  let activeTrack = null;

  function setActiveTrack(t) {
    activeTrack = t || null;
  }

  const LAST_TRACK_KEY = 'jinka.lastTrack.v1';

  function saveLastTrackState(track, position, duration, paused) {
    if (!track || !track.url) return;
    try {
      localStorage.setItem(LAST_TRACK_KEY, JSON.stringify({
        id: track.id,
        url: track.url,
        title: track.title || '',
        artist: track.artist || '',
        thumbnailUrl: track.thumbnailUrl || '',
        thumbnailHighUrl: track.thumbnailHighUrl || '',
        thumbnailFallbackUrl: track.thumbnailFallbackUrl || '',
        position: position || 0,
        duration: (isFinite(duration) && duration > 0) ? duration : 0,
        paused: paused !== false,
        savedAt: Date.now()
      }));
    } catch (_) {  }
    renderLastPlayedSection();
  }

  function loadLastTrackState() {
    try {
      const raw = localStorage.getItem(LAST_TRACK_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (_) { return null; }
  }

  const LISTEN_HISTORY_KEY = 'jinka.listenHistory.v1';

  function todayKey(d) {
    const date = d || new Date();
    return date.getFullYear() + '-' +
      String(date.getMonth() + 1).padStart(2, '0') + '-' +
      String(date.getDate()).padStart(2, '0');
  }

  function loadHistory() {
    try {
      const parsed = JSON.parse(localStorage.getItem(LISTEN_HISTORY_KEY));
      return (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) ? parsed : {};
    } catch (_) {
      return {};
    }
  }

  function saveHistory(history) {
    try { localStorage.setItem(LISTEN_HISTORY_KEY, JSON.stringify(history)); } catch (_) {}
  }

  function addListenedSeconds(sec) {
    if (!isFinite(sec) || sec <= 0) return;
    const history = loadHistory();
    const key = todayKey();
    history[key] = Math.round((history[key] || 0) + sec);
    saveHistory(history);
  }

  function getTodaySeconds() {
    return Math.floor(loadHistory()[todayKey()] || 0);
  }

  function getAvgLast4DaysMinutes() {
    const history = loadHistory();
    let totalSeconds = 0;
    for (let i = 0; i < 4; i++) {
      const d = new Date();
      d.setDate(d.getDate() - i);
      totalSeconds += Number(history[todayKey(d)]) || 0;
    }
    return Math.round(totalSeconds / 4 / 60);
  }

  let listenSegmentStart = null;

  function startListenTracking() {
    if (!getAud() || listenSegmentStart !== null) return;
    listenSegmentStart = Date.now();
  }

  function stopListenTracking() {
    if (listenSegmentStart === null) return;
    const elapsedSec = (Date.now() - listenSegmentStart) / 1000;
    listenSegmentStart = null;
    addListenedSeconds(elapsedSec);
  }

  function flushListenSegment() {
    if (listenSegmentStart === null) return;
    const elapsedSec = (Date.now() - listenSegmentStart) / 1000;
    listenSegmentStart = Date.now();
    addListenedSeconds(elapsedSec);
  }

  setInterval(() => {
    const a = getAud();
    if (a && !a.paused) flushListenSegment();
  }, 5000);

  window.addEventListener('beforeunload', stopListenTracking);

  const LAST_PLAYED_CONTAINER_ID = 'lastPlayedSection';

  let lastRenderedFingerprint = null;

  function ensureLastPlayedContainer() {
    return document.getElementById(LAST_PLAYED_CONTAINER_ID) || null;
  }

  function renderLastPlayedSection() {
    const container = ensureLastPlayedContainer();
    if (!container) return;

    const state = loadLastTrackState();

    if (!state || !state.url) {
      if (lastRenderedFingerprint !== null) {
        container.innerHTML = '';
        container.hidden = true;
        lastRenderedFingerprint = null;
      }
      return;
    }

    const identityFp = state.url + '|' + state.title + '|' + state.artist;

    if (identityFp === lastRenderedFingerprint && container.dataset.jinkaBound === '1') {
      const timeEl = container.querySelector('.last-played-time');
      if (timeEl) {
        timeEl.textContent = state.duration
          ? fmtTime(state.position) + ' / ' + fmtTime(state.duration)
          : fmtTime(state.position);
      }
      return;
    }
    lastRenderedFingerprint = identityFp;

    container.hidden = false;
    container.innerHTML = '';
    container.dataset.jinkaBound = '1';

    const wrap = document.createElement('div');
    wrap.className = 'last-played-card';

    const art = document.createElement('img');
    art.className = 'last-played-art';
    art.alt = '';
    const artSrc = state.thumbnailHighUrl || state.thumbnailUrl || state.thumbnailFallbackUrl || '';
    if (artSrc) art.src = artSrc;

    const info = document.createElement('div');
    info.className = 'last-played-info';

    const titleEl = document.createElement('p');
    titleEl.className = 'last-played-title';
    titleEl.textContent = state.title || 'Unknown title';

    const artistEl = document.createElement('p');
    artistEl.className = 'last-played-artist';
    artistEl.textContent = state.artist || '';

    const timeEl = document.createElement('p');
    timeEl.className = 'last-played-time';
    timeEl.textContent = state.duration
      ? fmtTime(state.position) + ' / ' + fmtTime(state.duration)
      : fmtTime(state.position);

    info.appendChild(titleEl);
    info.appendChild(artistEl);
    info.appendChild(timeEl);
    wrap.appendChild(art);
    wrap.appendChild(info);
    container.appendChild(wrap);

    wrap.addEventListener('click', () => {
      const a = getAud();
      const existingIdx = PLAYER_TRACKS.findIndex((t) => t.url === state.url);
      if (existingIdx >= 0) {
        loadTrack(existingIdx, true);
        if (a) {
          const seekOnce = () => {
            a.currentTime = state.position || 0;
            a.removeEventListener('loadedmetadata', seekOnce);
          };
          a.readyState >= 1 ? seekOnce() : a.addEventListener('loadedmetadata', seekOnce);
        }
      } else if (a && hasPlayer()) {
        const pseudoTrack = {
          id: state.id || state.url, title: state.title || 'Unknown title', artist: state.artist || '',
          thumbnailUrl: state.thumbnailUrl || '', thumbnailHighUrl: state.thumbnailHighUrl || '',
          thumbnailFallbackUrl: state.thumbnailFallbackUrl || '', url: state.url
        };
        curIdx = 0;
        setActiveTrack(pseudoTrack);
        if (trackTitleEl) trackTitleEl.textContent = pseudoTrack.title;
        if (trackArtistEl) trackArtistEl.textContent = pseudoTrack.artist;
        setArtwork(pseudoTrack);
        buildWaveform(String(pseudoTrack.id) + '|' + pseudoTrack.title + '|' + pseudoTrack.artist);
        getPlayer().load(pseudoTrack, true);
        const seekOnce = () => { a.currentTime = state.position || 0; a.removeEventListener('loadedmetadata', seekOnce); };
        a.addEventListener('loadedmetadata', seekOnce);
        fetchLyrics(pseudoTrack);
      }
    });
  }

  const REPEAT_STORE_KEY = 'jinka.repeatMode';
  const REPEAT_MODES = ['off', 'all', 'one'];
  let repeatMode = (() => {
    try {
      const saved = localStorage.getItem(REPEAT_STORE_KEY);
      return REPEAT_MODES.includes(saved) ? saved : 'off';
    } catch (_) { return 'off'; }
  })();

  function applyRepeatUI() {
    if (!repeatBtn) return;
    repeatBtn.dataset.mode = repeatMode;
    repeatBtn.setAttribute('aria-pressed', repeatMode !== 'off' ? 'true' : 'false');
    const label = repeatMode === 'one' ? 'Repeat one track'
      : repeatMode === 'all' ? 'Repeat all — tap to repeat one'
      : 'Repeat off — tap to repeat all';
    repeatBtn.setAttribute('aria-label', label);
  }

  function setRepeatMode(mode) {
    repeatMode = REPEAT_MODES.includes(mode) ? mode : 'off';
    try { localStorage.setItem(REPEAT_STORE_KEY, repeatMode); } catch (_) {  }
    applyRepeatUI();
  }

  let lyricsCache = {};
  let parsedLyrics = [];
  let activeLyricIdx = -1;

  function fmtTime(s) {
    if (!isFinite(s) || isNaN(s)) return '0:00';
    const m = Math.floor(s / 60);
    const sec = Math.floor(s % 60);
    return m + ':' + String(sec).padStart(2, '0');
  }

  const MEDIA_ERROR_NAMES = {
    1: 'MEDIA_ERR_ABORTED (fetch aborted)',
    2: 'MEDIA_ERR_NETWORK (network error while loading)',
    3: 'MEDIA_ERR_DECODE (browser could not decode this file)',
    4: 'MEDIA_ERR_SRC_NOT_SUPPORTED (URL invalid or format/codec not supported)'
  };

  function describeAudioError() {
    const a = getAud();
    if (!a || !a.error) return 'no MediaError present on the audio element';
    const code = a.error.code;
    return 'code ' + code + ' — ' + (MEDIA_ERROR_NAMES[code] || 'unknown') +
      (a.error.message ? ' (' + a.error.message + ')' : '');
  }

  function safePlay() {
    const a = getAud();
    if (!a) { console.error('[Jinka] safePlay(): no audio element available.'); return; }
    const playResult = a.play();
    if (playResult && typeof playResult.catch === 'function') {
      playResult.catch((err) => {
        console.error('[Jinka] play() rejected:', err.name + ':', err.message);
        console.error('[Jinka] audio.error at time of rejection:', describeAudioError());
        console.error('[Jinka] audio.src was:', a.src);
      });
    }
  }

  async function diagnoseAudioUrl(url) {
    console.log('[Jinka] [diagnostic] Fetching audio URL directly to inspect the real HTTP response:', url);
    try {
      const res = await fetch(url, { method: 'GET', cache: 'no-store' });
      console.log('[Jinka] [diagnostic] HTTP status:', res.status, res.statusText);
      console.log('[Jinka] [diagnostic] Content-Type:', res.headers.get('content-type'));
      console.log('[Jinka] [diagnostic] Content-Length:', res.headers.get('content-length'));

      const contentType = res.headers.get('content-type') || '';
      const looksLikeAudio = /^audio\//.test(contentType) || /^video\//.test(contentType) ||
        contentType === 'application/octet-stream';

      if (!res.ok || !looksLikeAudio) {
        try {
          const text = await res.clone().text();
          console.warn('[Jinka] [diagnostic] Response does not look like playable audio. Body snippet:',
            text.slice(0, 500));
        } catch (bodyErr) {
          console.warn('[Jinka] [diagnostic] Could not read response body:', bodyErr);
        }
      } else {
        console.log('[Jinka] [diagnostic] Response looks like real audio data — likely a genuine codec/decode issue, not a network/auth issue.');
      }
    } catch (fetchErr) {
      console.warn('[Jinka] [diagnostic] fetch() itself failed (likely network or CORS block):', fetchErr.name + ':', fetchErr.message);
    }
  }

  let loadTrackBusy = false;

  function loadTrack(idx, autoplay) {
    if (!PLAYER_TRACKS.length) return;
    const a = getAud();
    if (!a || !hasPlayer()) {
      console.error('[Jinka] loadTrack(): audio element or player API unavailable.');
      return;
    }
    if (loadTrackBusy) return; // FIX #6: prevent reentrant loadTrack calls
    loadTrackBusy = true;

    try {
      curIdx = (idx + PLAYER_TRACKS.length) % PLAYER_TRACKS.length;
      const t = PLAYER_TRACKS[curIdx];
      const player = getPlayer();
      const current = player.getState();
      const sameTrack = current.url === t.url;

      console.log('[Jinka] loadTrack(' + curIdx + ') — full track object:', t);

      lastPersistSave = Date.now();
      setActiveTrack(t);

      if (trackTitleEl) trackTitleEl.textContent = t.title || '';
      if (trackArtistEl) trackArtistEl.textContent = t.artist || '';
      setArtwork(t);
      buildWaveform(String(t.id != null ? t.id : curIdx) + '|' + (t.title || '') + '|' + (t.artist || ''));

      if (!sameTrack && t.url) {
        player.load(t, autoplay === true);
      } else if (sameTrack) {
        if (autoplay === true && current.paused) player.play();
        else if (autoplay === false && !current.paused) player.pause();
      }

      const stateNow = player.getState();
      if (curTimeEl) curTimeEl.textContent = fmtTime(stateNow.position);
      if (durTimeEl) durTimeEl.textContent = fmtTime(stateNow.duration);
      updateWaveformProgress(stateNow.position || 0, stateNow.duration || 0);

      parsedLyrics = [];
      activeLyricIdx = -1;
      if (lyricsTrackTitle) lyricsTrackTitle.textContent = t.title || 'Lyrics';

      if (!t.url) {
        console.error('[Jinka] Track has no playable url field:', t);
      }

      if (typeof fetchLyrics === 'function') fetchLyrics(t);

      if (lyricsSheet && lyricsSheet.classList.contains('is-open')) {
        renderLyricsPanel();
      }

      setPlayingUI(!stateNow.paused);
    } finally {
      loadTrackBusy = false;
    }
  }

  function nextTrack(forcePlay) {
    if (!PLAYER_TRACKS.length) return;
    const a = getAud();
    if (!a) return; // FIX #5
    const play = forcePlay !== undefined ? forcePlay : !a.paused;
    const nextIdx = (curIdx + 1) % PLAYER_TRACKS.length;
    loadTrack(nextIdx, play);
  }

  function prevTrack(forcePlay) {
    if (!PLAYER_TRACKS.length) return;
    const a = getAud();
    if (!a) return; // FIX #5
    const play = forcePlay !== undefined ? forcePlay : !a.paused;
    if (a.currentTime > 3) {
      a.currentTime = 0;
    } else {
      const prevIdx = (curIdx - 1 + PLAYER_TRACKS.length) % PLAYER_TRACKS.length;
      loadTrack(prevIdx, play);
    }
  }

  let audListenersBound = false;
  let handlingEnded = false; // reentrancy guard for 'ended', see FIX #6

  function bindAudListeners() {
    const a = getAud();
    if (!a || audListenersBound) return;
    audListenersBound = true;

    a.addEventListener('play', () => {
      setPlayingUI(true);
      startListenTracking();
    });
    a.addEventListener('pause', () => {
      setPlayingUI(false);
      stopListenTracking();
      saveLastTrackState(activeTrack, a.currentTime, a.duration, true);
    });

    a.addEventListener('loadedmetadata', () => {
      if (durTimeEl) durTimeEl.textContent = fmtTime(a.duration);
    });

    a.addEventListener('timeupdate', () => {
      if (curTimeEl) curTimeEl.textContent = fmtTime(a.currentTime);
      if (durTimeEl) durTimeEl.textContent = fmtTime(a.duration);
      updateWaveformProgress(a.currentTime, a.duration);
      updateSyncedLyrics();

      const now = Date.now();
      if (now - lastPersistSave > 4000) {
        lastPersistSave = now;
        saveLastTrackState(activeTrack, a.currentTime, a.duration, a.paused);
      }
    });

    document.addEventListener('visibilitychange', () => {
      const cur = getAud();
      if (document.hidden && cur) saveLastTrackState(activeTrack, cur.currentTime, cur.duration, cur.paused);
    });
    window.addEventListener('pagehide', () => {
      const cur = getAud();
      if (cur) saveLastTrackState(activeTrack, cur.currentTime, cur.duration, cur.paused);
    });

    a.addEventListener('ended', () => {
      if (handlingEnded || loadTrackBusy) return;
      handlingEnded = true;
      stopListenTracking();
      try {
        if (repeatMode === 'one') {
          const cur = getAud();
          if (cur) { cur.currentTime = 0; safePlay(); }
        } else if (curIdx < PLAYER_TRACKS.length - 1) {
          nextTrack(true);
        } else if (repeatMode === 'all') {
          loadTrack(0, true);
        } else {
          setPlayingUI(false);
        }
      } finally {
        handlingEnded = false;
      }
    });

    a.addEventListener('error', () => {
      console.error('[Jinka] Audio failed to load/play. audio.src:', a.src);
      console.error('[Jinka] MediaError:', describeAudioError());
      console.error('[Jinka] See the [diagnostic] logs above/below for the real HTTP status behind this MediaError.');
    });

    if (!a.paused) startListenTracking();
  }

  function hasBridge() {
    return typeof window.AndroidYT !== 'undefined' && window.AndroidYT !== null;
  }

  function nextHomeRequestId() {
    return 'home-' + Date.now();
  }

  function normalizeOfflineTrack(t) {
    return {
      id: t.id,
      title: t.title || 'Unknown title',
      artist: t.artist || '',
      thumbnailUrl: t.thumbnailUrl || '',
      thumbnailHighUrl: t.thumbnailHighUrl || '',
      thumbnailFallbackUrl: t.thumbnailFallbackUrl || '',
      url: t.localPath
    };
  }

  async function initPlayer() {
    if (!hasBridge()) {
      console.error('[Jinka] AndroidYT bridge unavailable — can\'t read the offline library.');
      showPlaylistError('Offline library unavailable');
      return;
    }
    window.AndroidYT.listOfflineTracks(nextHomeRequestId());
  }

  window.onOfflineTracksListed = function (requestId, jsonArrayString) {
    let parsed = [];
    try { parsed = JSON.parse(jsonArrayString); } catch (e) { parsed = []; }

    const rawList = Array.isArray(parsed) ? parsed : [];
    PLAYER_TRACKS = rawList
      .filter((t) => t && typeof t.localPath === 'string' && t.localPath.trim() !== '')
      .map(normalizeOfflineTrack);

    console.log('[Jinka] Loaded', PLAYER_TRACKS.length, 'offline track(s) for Home.');

    const a = getAud();
    if (!a || !hasPlayer()) {
      console.error('[Jinka] Player unavailable when offline tracks arrived — will not auto-load.');
      renderLastPlayedSection();
      return;
    }
    const player = getPlayer();
    const persistent = player.getState();
    let resumeUrl = persistent.url || null;
    let resumePosition = persistent.position || 0;
    let resumeMeta = null;

    if (!resumeUrl) {
      const last = loadLastTrackState();
      if (last && last.url) {
        resumeUrl = last.url;
        resumePosition = last.position || 0;
        resumeMeta = last;
      }
    }

    renderLastPlayedSection();

    if (!PLAYER_TRACKS.length && !resumeUrl) {
      showPlaylistError('No offline songs yet');
      return;
    }

    const existingIdx = resumeUrl ? PLAYER_TRACKS.findIndex((t) => t.url === resumeUrl) : -1;

      if (existingIdx >= 0) {
        loadTrack(existingIdx, persistent.url ? null : false);
      if (!persistent.url && resumePosition > 0) {
        const seekOnce = () => {
          a.currentTime = resumePosition;
          updateWaveformProgress(resumePosition, a.duration);
          if (curTimeEl) curTimeEl.textContent = fmtTime(resumePosition);
          a.removeEventListener('loadedmetadata', seekOnce);
        };
        a.readyState >= 1 ? seekOnce() : a.addEventListener('loadedmetadata', seekOnce);
      }
    } else if (resumeUrl) {
      curIdx = 0;
      const meta = resumeMeta || persistent;
      const pseudoTrack = {
        id: meta.id || resumeUrl, title: meta.title || 'Unknown title', artist: meta.artist || '',
        thumbnailUrl: meta.thumbnailUrl || '', thumbnailHighUrl: meta.thumbnailHighUrl || '',
        thumbnailFallbackUrl: meta.thumbnailFallbackUrl || '', url: resumeUrl
      };
      setActiveTrack(pseudoTrack);
      if (trackTitleEl) trackTitleEl.textContent = pseudoTrack.title;
      if (trackArtistEl) trackArtistEl.textContent = pseudoTrack.artist;
      setArtwork(pseudoTrack);
      buildWaveform(String(pseudoTrack.id) + '|' + pseudoTrack.title + '|' + pseudoTrack.artist);

      if (persistent.url) {
        if (curTimeEl) curTimeEl.textContent = fmtTime(persistent.position);
        if (durTimeEl) durTimeEl.textContent = fmtTime(persistent.duration);
        updateWaveformProgress(persistent.position || 0, persistent.duration || 0);
        setPlayingUI(!persistent.paused);
      } else {
        player.load(pseudoTrack, false);
        const seekOnce = () => { a.currentTime = resumePosition; a.removeEventListener('loadedmetadata', seekOnce); };
        a.addEventListener('loadedmetadata', seekOnce);
        setPlayingUI(false);
        fetchLyrics(pseudoTrack);
      }
    } else {
      loadTrack(0, false);
    }
  };

  function showPlaylistError(message) {
    if (trackTitleEl) trackTitleEl.textContent = message;
    if (trackArtistEl) trackArtistEl.textContent = 'Find songs in Search and save them offline';
    PLAYER_TRACKS = [];
    setActiveTrack(null);
    lyricsCache = {};
  }

  

  function isAcceptableLettered(text) {
    if (!text || typeof text !== 'string') return false;
    const cleaned = text.replace(/\[\d{2}:\d{2}([.:]\d{1,3})?\]/g, '').trim();
    if (!cleaned) return true;

    const latinChars = (cleaned.match(/[a-zA-Z\u00C0-\u024F]/g) || []).length;
    const banglaChars = (cleaned.match(/[\u0980-\u09FF]/g) || []).length;
    const acceptedChars = latinChars + banglaChars;

    const rejectedChars = (cleaned.match(/[\u0900-\u097F\u0A00-\u0A7F\u0A80-\u0AFF\u0B00-\u0B7F\u0B80-\u0BFF\u0C00-\u0C7F\u0C80-\u0CFF\u0D00-\u0D7F\u0600-\u06FF\u0750-\u077F\u4E00-\u9FFF\u3040-\u309F\u30A0-\u30FF\uAC00-\uD7AF\u0400-\u04FF\u0500-\u052F\u0E00-\u0E7F\u1000-\u109F]/g) || []).length;

    if (rejectedChars > 0) return false;

    const totalAlpha = acceptedChars + rejectedChars;
    if (totalAlpha === 0) return true;

    return acceptedChars / totalAlpha > 0.7;
  }

  function isLyricsAcceptable(lyricsText) {
    if (!lyricsText) return false;
    const lines = lyricsText.split('\n').filter((l) => {
      const cleaned = l.replace(/\[\d{2}:\d{2}([.:]\d{1,3})?\]/g, '').trim();
      return cleaned.length > 0;
    });
    if (lines.length === 0) return true;
    const goodLines = lines.filter((l) => isAcceptableLettered(l)).length;
    return (goodLines / lines.length) >= 0.8;
  }

  function parseLyric(lrc) {
    const regex = /^\[(?<time>\d{2}:\d{2}(\.\d{1,3})?)\](?<text>.*)/;
    const lines = lrc.split('\n');
    const output = [];

    lines.forEach((line) => {
      const match = line.match(regex);
      if (!match) return;
      const { time, text } = match.groups;
      output.push({ time: parseLrcTime(time), text: text.trim() });
    });

    function parseLrcTime(t) {
      const [min, sec] = t.split(':');
      return parseInt(min, 10) * 60 + parseFloat(sec);
    }

    return output;
  }

  function syncLyric(lyrics, time) {
    let bestIdx = null;
    let bestScore = Infinity;
    for (let i = 0; i < lyrics.length; i++) {
      const score = time - lyrics[i].time;
      if (score >= 0 && score < bestScore) {
        bestScore = score;
        bestIdx = i;
      }
    }
    return bestIdx;
  }

  function updateSyncedLyrics() {
    const a = getAud();
    if (!parsedLyrics.length || !lyricsList || !a) return;
    const idx = syncLyric(parsedLyrics, a.currentTime);
    if (idx === null || idx === activeLyricIdx) return;
    activeLyricIdx = idx;

    const lines = lyricsList.querySelectorAll('.lyrics-line');
    lines.forEach((el, i) => {
      el.classList.toggle('is-active', i === idx);
    });

    const activeEl = lines[idx];
    const isOpen = lyricsSheet && lyricsSheet.classList.contains('is-open');
    if (activeEl && isOpen) {
      activeEl.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
  }

  const LYRICS_STORE_KEY = 'jinka.lyrics.v1';

  function lyricsStoreKeyFor(t) {
    const norm = (s) => String(s || '').trim().toLowerCase().replace(/\s+/g, ' ');
    return norm(t.artist) + '|' + norm(t.title);
  }

  function loadLyricsStore() {
    try {
      const raw = localStorage.getItem(LYRICS_STORE_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch (_) { return {}; }
  }

  function saveLyricsToStore(key, entry) {
    try {
      const store = loadLyricsStore();
      store[key] = entry;
      localStorage.setItem(LYRICS_STORE_KEY, JSON.stringify(store));
    } catch (_) {  }
  }

  async function fetchLyrics(track) {
    const t = track;
    if (!t) return;
    const storeKey = lyricsStoreKeyFor(t);

    const isActive = () =>
      !!lyricsSheet &&
      lyricsSheet.classList.contains('is-open') &&
      !!activeTrack &&
      lyricsStoreKeyFor(activeTrack) === storeKey;

    if (lyricsCache[storeKey] !== undefined) {
      if (isActive()) renderLyricsPanel();
      return;
    }

    const store = loadLyricsStore();
    if (store[storeKey]) {
      lyricsCache[storeKey] = store[storeKey];
      if (isActive()) renderLyricsPanel();
      return;
    }

    lyricsCache[storeKey] = null;

    const a = getAud();
    const audioDuration = a ? a.duration : undefined;

    async function tryGet(url) {
      const r = await fetch(url);
      if (!r.ok) throw new Error(String(r.status));
      return r.json();
    }

    const DURATION_TOLERANCE = 15;

    function scoreResult(r, dur) {
      const hasSynced = r.syncedLyrics && isLyricsAcceptable(r.syncedLyrics);
      const hasPlain = r.plainLyrics && isLyricsAcceptable(r.plainLyrics);
      if (!hasSynced && !hasPlain) return null;

      let score = 0;
      if (dur && r.duration) score += Math.abs(r.duration - dur);
      if (hasSynced) score -= 5;

      return { result: r, score, hasSynced, hasPlain };
    }

    function pickBestAcceptable(results, dur) {
      if (!Array.isArray(results) || !results.length) return null;
      const scored = results.map((r) => scoreResult(r, dur)).filter((s) => s !== null);
      if (!scored.length) return null;
      scored.sort((a, b) => a.score - b.score);
      return scored[0];
    }

    try {
      let bestPick = null;

      try {
        const qGet = new URLSearchParams({ track_name: t.title, artist_name: t.artist });
        if (t.album) qGet.append('album_name', t.album);
        if (audioDuration) qGet.append('duration', Math.round(audioDuration));

        const data = await tryGet('https://lrclib.net/api/get?' + qGet);
        if (data) {
          const durOk = !audioDuration || !data.duration ||
            Math.abs(data.duration - audioDuration) <= DURATION_TOLERANCE;
          if (durOk) {
            const s = scoreResult(data, audioDuration);
            if (s) bestPick = s;
          }
        }
      } catch (e) {  }

      try {
        const qSearch = new URLSearchParams({ track_name: t.title, artist_name: t.artist });
        const results = await tryGet('https://lrclib.net/api/search?' + qSearch);
        if (Array.isArray(results) && results.length) {
          const searchPick = pickBestAcceptable(results, audioDuration);
          if (searchPick && (!bestPick || searchPick.score < bestPick.score)) {
            bestPick = searchPick;
          }
        }
      } catch (e) {  }

      if (!bestPick) {
        try {
          const qBroad = new URLSearchParams({ q: (t.artist ? t.artist + ' ' : '') + t.title });
          const results2 = await tryGet('https://lrclib.net/api/search?' + qBroad);
          if (Array.isArray(results2) && results2.length) {
            const broadPick = pickBestAcceptable(results2, audioDuration);
            if (broadPick) bestPick = broadPick;
          }
        } catch (e) {  }
      }

      if (!bestPick) {
        try {
          const qRoman = new URLSearchParams({ q: (t.artist ? t.artist + ' ' : '') + t.title + ' romanized' });
          const results3 = await tryGet('https://lrclib.net/api/search?' + qRoman);
          if (Array.isArray(results3) && results3.length) {
            const romanPick = pickBestAcceptable(results3, audioDuration);
            if (romanPick) bestPick = romanPick;
          }
        } catch (e) {  }
      }

      if (bestPick) {
        if (bestPick.hasSynced) {
          lyricsCache[storeKey] = { type: 'lrc', content: bestPick.result.syncedLyrics };
        } else if (bestPick.hasPlain) {
          lyricsCache[storeKey] = { type: 'plain', content: bestPick.result.plainLyrics };
        } else {
          lyricsCache[storeKey] = { type: 'none' };
        }
      } else {
        lyricsCache[storeKey] = { type: 'none' };
      }
      saveLyricsToStore(storeKey, lyricsCache[storeKey]);
    } catch (e) {
      lyricsCache[storeKey] = { type: 'error' };
    }

    if (isActive()) renderLyricsPanel();
  }

  function renderLyricsPanel() {
    if (!lyricsList) return;
    lyricsList.innerHTML = '';
    parsedLyrics = [];
    activeLyricIdx = -1;

    const current = activeTrack;
    if (!current) {
      const p = document.createElement('p');
      p.className = 'lyrics-line';
      p.textContent = 'No track loaded yet.';
      lyricsList.appendChild(p);
      return;
    }

    const storeKey = lyricsStoreKeyFor(current);
    const cached = lyricsCache[storeKey];

    if (cached === null || cached === undefined) {
      const p = document.createElement('p');
      p.className = 'lyrics-line';
      p.textContent = 'Fetching lyrics…';
      lyricsList.appendChild(p);
      return;
    }

    if (cached.type === 'error') {
      const p = document.createElement('p');
      p.className = 'lyrics-line';
      p.textContent = 'Couldn\u2019t load lyrics.';
      lyricsList.appendChild(p);
      return;
    }

    if (cached.type === 'none') {
      const p = document.createElement('p');
      p.className = 'lyrics-line';
      p.textContent = 'No lyrics available for this track.';
      lyricsList.appendChild(p);
      return;
    }

    if (cached.type === 'lrc') {
      parsedLyrics = parseLyric(cached.content);
      const frag = document.createDocumentFragment();
      parsedLyrics.forEach(({ text }) => {
        const p = document.createElement('p');
        p.className = 'lyrics-line';
        p.textContent = text;
        frag.appendChild(p);
      });
      lyricsList.appendChild(frag);
      activeLyricIdx = -1;
      updateSyncedLyrics();
      return;
    }

    if (cached.type === 'plain') {
      const frag = document.createDocumentFragment();
      cached.content.split('\n').forEach((line) => {
        const p = document.createElement('p');
        p.className = 'lyrics-line';
        p.textContent = line.trim() === '' ? '\u00A0' : line;
        frag.appendChild(p);
      });
      lyricsList.appendChild(frag);
    }
  }

  function bindOnce(el, marker, eventName, handler, opts) {
    if (!el) return;
    const key = 'jinkaBound_' + marker;
    if (el.dataset[key] === '1') return;
    el.dataset[key] = '1';
    el.addEventListener(eventName, handler, opts);
  }

  function rebindUI() {
    aud = getAud();

    waveform = document.getElementById('waveform');
    playToggle = document.getElementById('playToggle');
    iconPlay = playToggle ? playToggle.querySelector('.icon-play') : null;
    iconPause = playToggle ? playToggle.querySelector('.icon-pause') : null;
    favoriteToggle = document.getElementById('favoriteToggle');
    sheetBackdrop = document.getElementById('sheetBackdrop');
    lyricsSheet = document.getElementById('lyricsSheet');
    lyricsList = document.getElementById('lyricsList');
    lyricsToggle = document.getElementById('lyricsToggle');
    lyricsClose = document.getElementById('lyricsClose');
    lyricsTrackTitle = document.getElementById('lyricsTrackTitle');
    trackTitleEl = document.querySelector('.track-info .track-title');
    trackArtistEl = document.querySelector('.track-info .track-artist');
    curTimeEl = document.getElementById('curTime');
    durTimeEl = document.getElementById('durTime');
    prevBtn = document.getElementById('prevBtn');
    nextBtn = document.getElementById('nextBtn');
    artworkImg = document.getElementById('artworkImg');
    repeatBtn = document.getElementById('repeatBtn');
    navItems = Array.from(document.querySelectorAll('.nav-item'));

    buildWaveform(currentWaveformSeed);

    let isScrubbing = false;
    bindOnce(waveform, 'pointerdown', 'pointerdown', (e) => {
      const a = getAud();
      if (!a || !isFinite(a.duration) || a.duration <= 0) return;
      isScrubbing = true;
      waveform.setPointerCapture(e.pointerId);
      seekFromClientX(e.clientX);
    });
    bindOnce(waveform, 'pointermove', 'pointermove', (e) => {
      if (!isScrubbing) return;
      seekFromClientX(e.clientX);
    });
    const endScrub = (e) => {
      if (!isScrubbing) return;
      isScrubbing = false;
      if (waveform && waveform.hasPointerCapture && e.pointerId != null && waveform.hasPointerCapture(e.pointerId)) {
        waveform.releasePointerCapture(e.pointerId);
      }
    };
    bindOnce(waveform, 'pointerup', 'pointerup', endScrub);
    bindOnce(waveform, 'pointercancel', 'pointercancel', endScrub);

    bindOnce(playToggle, 'click', 'click', () => {
      const a = getAud();
      if (!a || !a.src) return; // FIX #5
      if (a.paused) safePlay();
      else a.pause();
    });

    if (favoriteToggle) {
      const iconOutline = favoriteToggle.querySelector('.icon-heart-outline');
      const iconFilled = favoriteToggle.querySelector('.icon-heart-filled');
      bindOnce(favoriteToggle, 'click', 'click', () => {
        const isFavorited = favoriteToggle.getAttribute('aria-pressed') === 'true';
        favoriteToggle.setAttribute('aria-pressed', String(!isFavorited));
        favoriteToggle.setAttribute('aria-label', isFavorited ? 'Favorite' : 'Unfavorite');
        if (isFavorited) {
          if (iconOutline) iconOutline.removeAttribute('hidden');
          if (iconFilled) iconFilled.setAttribute('hidden', '');
        } else {
          if (iconOutline) iconOutline.setAttribute('hidden', '');
          if (iconFilled) iconFilled.removeAttribute('hidden');
        }
      });
    }

    bindOnce(sheetBackdrop, 'click', 'click', closeSheets);

    navItems.forEach((link) => {
      if (link.href === window.location.href) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });

    bindOnce(prevBtn, 'click', 'click', () => prevTrack());
    bindOnce(nextBtn, 'click', 'click', () => nextTrack());

    if (repeatBtn) {
      bindOnce(repeatBtn, 'click', 'click', () => {
        const nextMode = REPEAT_MODES[(REPEAT_MODES.indexOf(repeatMode) + 1) % REPEAT_MODES.length];
        setRepeatMode(nextMode);
      });
      applyRepeatUI();
    }

    bindOnce(lyricsToggle, 'click', 'click', () => {
      if (lyricsTrackTitle && activeTrack) {
        lyricsTrackTitle.textContent = activeTrack.title || 'Lyrics';
      }
      renderLyricsPanel();
      openSheet(lyricsSheet);
      lyricsToggle.setAttribute('aria-pressed', 'true');
    });
    bindOnce(lyricsClose, 'click', 'click', closeSheets);

    bindAudListeners();
    const a2 = getAud();
    if (a2 && hasPlayer()) {
      const state = getPlayer().getState();
      setPlayingUI(!state.paused);
      if (curTimeEl) curTimeEl.textContent = fmtTime(state.position || 0);
      if (durTimeEl) durTimeEl.textContent = fmtTime(state.duration || 0);
      updateWaveformProgress(state.position || 0, state.duration || 0);
      if (activeTrack) {
        setArtwork(activeTrack);
        if (trackTitleEl) trackTitleEl.textContent = activeTrack.title || '';
        if (trackArtistEl) trackArtistEl.textContent = activeTrack.artist || '';
      }
    }
    renderLastPlayedSection();
    if (lyricsSheet && lyricsSheet.classList.contains('is-open')) renderLyricsPanel();
  }

  window.__jinkaRebindUI = rebindUI;

  window.JinkaListenStats = {
    addListenedSeconds: addListenedSeconds,
    getTodaySeconds: getTodaySeconds,
    getAvgLast4DaysMinutes: getAvgLast4DaysMinutes
  };

  window.nextTrack = nextTrack;
  window.prevTrack = prevTrack;

  rebindUI();
  initPlayer();

})();
}


