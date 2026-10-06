

const searchInput = document.getElementById('searchInput');
const resultsEl = document.getElementById('searchResults');
const hintEl = document.getElementById('searchHint');
const miniPlayer = document.getElementById('miniPlayer');
const miniPlayerThumb = document.getElementById('miniPlayerThumb');
const miniPlayerTitle = document.getElementById('miniPlayerTitle');
const miniPlayerArtist = document.getElementById('miniPlayerArtist');
const miniPlayerToggle = document.getElementById('miniPlayerToggle');
const miniPlayerIconPlay = miniPlayerToggle ? miniPlayerToggle.querySelector('.icon-play') : null;
const miniPlayerIconPause = miniPlayerToggle ? miniPlayerToggle.querySelector('.icon-pause') : null;

let searchDebounceTimer = null;
let latestSearchRequestId = null;
let currentResults = [];       // last rendered result set, indexed by row index
let previewingIndex = null;    // which row is currently the preview source
let pendingPreviewIndex = null; // row waiting on resolveStream() to come back

let reqCounter = 0;
function nextRequestId(prefix) {
  reqCounter += 1;
  return prefix + '-' + Date.now() + '-' + reqCounter;
}

function hasBridge() {
  return typeof window.AndroidYT !== 'undefined' && window.AndroidYT !== null;
}

function formatDuration(totalSeconds) {
  const s = Math.max(0, Math.floor(totalSeconds || 0));
  const m = Math.floor(s / 60);
  const rem = s % 60;
  return m + ':' + String(rem).padStart(2, '0');
}

function normalizeSearchThumbnails(track) {
  if (!track || track.source !== 'youtube') return track;

  const idMatch = String(track.id || '').match(/(?:v=|youtu\.be\/|\/shorts\/)([A-Za-z0-9_-]{6,})/);
  if (!idMatch) return track;

  const id = idMatch[1];
  if (!track.thumbnailUrl) track.thumbnailUrl = 'https://i.ytimg.com/vi/' + id + '/mqdefault.jpg';
  if (!track.thumbnailHighUrl) track.thumbnailHighUrl = 'https://i.ytimg.com/vi/' + id + '/maxresdefault.jpg';
  if (!track.thumbnailFallbackUrl) track.thumbnailFallbackUrl = 'https://i.ytimg.com/vi/' + id + '/hqdefault.jpg';
  return track;
}

function isTrackSaved(track) {
  if (!track || !hasBridge() || typeof window.AndroidYT.isOffline !== 'function') return false;
  try { return !!window.AndroidYT.isOffline(track.source, track.id); } catch (_) { return false; }
}

function renderSearchLoader() {
  resultsEl.innerHTML =
    '<div class="jinka-loader jinka-loader--inline" aria-hidden="true">' +
      '<div class="jinka-loader-badge">' +
        '<div class="jinka-loader-ring"></div>' +
        '<img class="jinka-loader-mark" src="img/ic_notification.png" alt="" draggable="false">' +
      '</div>' +
    '</div>';
}

function renderResults(results) {
  results = Array.isArray(results) ? results.map(normalizeSearchThumbnails) : [];
  currentResults = results;
  try { sessionStorage.setItem('jinka.search.results', JSON.stringify(results)); } catch (_) {}
  resultsEl.innerHTML = '';

  if (!results.length) {
    hintEl.textContent = 'No results. Try a different search.';
    return;
  }
  hintEl.textContent = 'Tap a result to preview it. Double-tap to save it offline.';

  results.forEach((track, index) => {
    const row = document.createElement('div');
    row.className = 'song-row';
    row.dataset.index = String(index);

    const thumb = document.createElement('div');
    thumb.className = 'song-row-thumb';
    if (track.thumbnailUrl || track.thumbnailFallbackUrl) {
      const img = document.createElement('img');
      const primary = track.thumbnailUrl || track.thumbnailFallbackUrl;
      img.src = primary;
      img.alt = '';
      img.draggable = false;
      img.onerror = () => {
        if (track.thumbnailFallbackUrl && img.src !== track.thumbnailFallbackUrl) {
          img.src = track.thumbnailFallbackUrl;
        } else {
          img.removeAttribute('src');
        }
      };
      thumb.appendChild(img);
    } else {
      thumb.innerHTML = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>';
    }

    const body = document.createElement('div');
    body.className = 'song-row-body';
    const title = document.createElement('div');
    title.className = 'song-row-title';
    title.textContent = track.title || 'Unknown title';
    const artist = document.createElement('div');
    artist.className = 'song-row-artist';
    const sourceLabel = track.source === 'soundcloud' ? 'SoundCloud' : 'YouTube Music';
    artist.textContent = (track.artist ? track.artist + ' · ' : '') + sourceLabel;
    body.appendChild(title);
    body.appendChild(artist);

    const meta = document.createElement('div');
    meta.className = 'song-row-status';
    if (isTrackSaved(track)) {
      meta.textContent = 'Saved ✓';
      meta.classList.add('is-downloaded');
    } else {
      meta.textContent = formatDuration(track.durationSeconds);
    }

    row.appendChild(thumb);
    row.appendChild(body);
    row.appendChild(meta);

    attachTapHandlers(row, index);
    resultsEl.appendChild(row);
  });
}


function attachTapHandlers(row, index) {
  let tapTimer = null;
  let tapCount = 0;

  function handleTap() {
    tapCount += 1;
    if (tapCount === 1) {
      tapTimer = setTimeout(() => {
        tapCount = 0;
        previewTrack(index);
      }, 280);
    } else if (tapCount === 2) {
      clearTimeout(tapTimer);
      tapCount = 0;
      downloadTrack(index);
    }
  }

  row.addEventListener('click', handleTap);
}

function setRowStatus(index, text, cssClass) {
  const row = resultsEl.querySelector('.song-row[data-index="' + index + '"]');
  if (!row) return;
  const statusEl = row.querySelector('.song-row-status');
  if (!statusEl) return;
  statusEl.textContent = text;
  statusEl.className = 'song-row-status' + (cssClass ? ' ' + cssClass : '');
}

function clearRowHighlight() {
  resultsEl.querySelectorAll('.song-row.is-previewing').forEach((el) => {
    el.classList.remove('is-previewing');
  });
}


function setMiniPlayerPlayingUI(isPlaying) {
  if (!miniPlayerToggle) return;
  miniPlayerToggle.setAttribute('aria-pressed', String(isPlaying));
  miniPlayerToggle.setAttribute('aria-label', isPlaying ? 'Pause' : 'Play');
  if (miniPlayerIconPlay && miniPlayerIconPause) {
    if (isPlaying) {
      miniPlayerIconPlay.setAttribute('hidden', '');
      miniPlayerIconPause.removeAttribute('hidden');
    } else {
      miniPlayerIconPlay.removeAttribute('hidden');
      miniPlayerIconPause.setAttribute('hidden', '');
    }
  }
}

function showMiniPlayer(track) {
  if (!miniPlayer) return;
  if (miniPlayerTitle) miniPlayerTitle.textContent = track.title || 'Unknown title';
  if (miniPlayerArtist) {
    const sourceLabel = track.source === 'soundcloud' ? 'SoundCloud' : 'YouTube Music';
    miniPlayerArtist.textContent = (track.artist ? track.artist + ' · ' : '') + sourceLabel;
  }
  if (miniPlayerThumb) {
    miniPlayerThumb.innerHTML = '';
    if (track.thumbnailUrl || track.thumbnailFallbackUrl) {
      const img = document.createElement('img');
      const primary = track.thumbnailUrl || track.thumbnailFallbackUrl;
      img.src = primary;
      img.alt = '';
      img.draggable = false;
      img.onerror = () => {
        if (track.thumbnailFallbackUrl && img.src !== track.thumbnailFallbackUrl) {
          img.src = track.thumbnailFallbackUrl;
        } else {
          img.removeAttribute('src');
        }
      };
      miniPlayerThumb.appendChild(img);
    } else {
      miniPlayerThumb.innerHTML = '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>';
    }
  }
  miniPlayer.hidden = false;
}

function hideMiniPlayer() {
  if (!miniPlayer) return;
  miniPlayer.hidden = true;
}

function syncSearchPlayer() {
  const state = window.JinkaPlayer.getState();
  const active = currentResults.find((track) => track && track.playUrl === state.url);
  if (active) showMiniPlayer(active);
  else if (state.url) showMiniPlayer({
    title: state.title,
    artist: state.artist,
    thumbnailUrl: state.thumbnailUrl,
    thumbnailHighUrl: state.thumbnailHighUrl,
    thumbnailFallbackUrl: state.thumbnailFallbackUrl
  });
  else hideMiniPlayer();
  if (state.url) setMiniPlayerPlayingUI(!state.paused);

  clearRowHighlight();
  if (active) {
    const index = currentResults.indexOf(active);
    const row = resultsEl.querySelector('.song-row[data-index="' + index + '"]');
    if (row && !state.paused) row.classList.add('is-previewing');
  }
}

window.JinkaPlayer.subscribe(syncSearchPlayer);

if (miniPlayerToggle) {
  miniPlayerToggle.addEventListener('click', () => {
    const state = window.JinkaPlayer.getState();
    if (!state.url) return;
    if (state.paused) window.JinkaPlayer.play();
    else window.JinkaPlayer.pause();
  });
}


function previewTrack(index) {
  const track = currentResults[index];
  if (!track || !hasBridge()) return;

  const state = window.JinkaPlayer.getState();
  if (previewingIndex === index && state.url) {
    if (state.paused) window.JinkaPlayer.play();
    else window.JinkaPlayer.pause();
    return;
  }

  clearRowHighlight();
  const row = resultsEl.querySelector('.song-row[data-index="' + index + '"]');
  if (row) row.classList.add('is-previewing');

  pendingPreviewIndex = index;
  const requestId = nextRequestId('preview');
  latestSearchRequestId = null; // preview requests don't touch search state
  window.AndroidYT.resolveStream(requestId, track.source, track.id);
}

window.onYouTubeStreamResolved = function (requestId, streamUrl) {
  if (pendingPreviewIndex === null) return;
  const index = pendingPreviewIndex;
  pendingPreviewIndex = null;
  previewingIndex = index;
  const track = currentResults[index];
  if (track) {
    track.playUrl = streamUrl;
    try { sessionStorage.setItem('jinka.search.results', JSON.stringify(currentResults)); } catch (_) {}
    window.JinkaPlayer.load({
      id: track.id,
      title: track.title,
      artist: track.artist,
      thumbnailUrl: track.thumbnailUrl,
      thumbnailHighUrl: track.thumbnailHighUrl || track.thumbnailUrl || '',
      thumbnailFallbackUrl: track.thumbnailFallbackUrl || '',
      url: streamUrl
    }, true);
  }
};

window.onYouTubeStreamError = function (requestId, message) {
  console.error('[Jinka] preview resolve failed:', message);
  pendingPreviewIndex = null;
  clearRowHighlight();
  hideMiniPlayer();
};


function downloadTrack(index) {
  const track = currentResults[index];
  if (!track || !hasBridge()) return;

  if (isTrackSaved(track)) {
    setRowStatus(index, 'Saved ✓', 'is-downloaded');
    return;
  }

  setRowStatus(index, 'Saving…', 'is-downloading');
  const requestId = nextRequestId('download');
  window.AndroidYT.downloadTrack(
    requestId,
    track.source,
    track.id,
    track.title || '',
    track.artist || '',
    track.thumbnailHighUrl || track.thumbnailUrl || track.thumbnailFallbackUrl || ''
  );
  downloadRequests[requestId] = index;
}

const downloadRequests = {};

window.onDownloadComplete = function (requestId, trackJson) {
  const index = downloadRequests[requestId];
  delete downloadRequests[requestId];
  if (index === undefined) return;
  setRowStatus(index, 'Saved ✓', 'is-downloaded');
};

window.onDownloadError = function (requestId, message) {
  const index = downloadRequests[requestId];
  delete downloadRequests[requestId];
  console.error('[Jinka] download failed:', message);
  if (index === undefined) return;
  const track = currentResults[index];
  const label = track ? (track.title || 'This track') : 'This track';
  alert(label + ' failed to download:\n\n' + message);
  setRowStatus(index, 'Failed', '');
  setTimeout(() => setRowStatus(index, formatDuration(currentResults[index] && currentResults[index].durationSeconds), ''), 1800);
};


function runSearch(query) {
  if (!query.trim()) {
    currentResults = [];
    resultsEl.innerHTML = '';
    try { sessionStorage.setItem('jinka.search.results', '[]'); } catch (_) {}
    hintEl.textContent = 'Tap a result to preview it. Double-tap to save it offline.';
    return;
  }
  if (!hasBridge()) {
    hintEl.textContent = 'Search isn\u2019t available outside the app.';
    return;
  }
  hintEl.textContent = 'Searching…';
  renderSearchLoader();
  const requestId = nextRequestId('search');
  latestSearchRequestId = requestId;
  window.AndroidYT.searchTracks(requestId, query.trim());
}

window.onYouTubeSearchResults = function (requestId, jsonArrayString) {
  if (requestId !== latestSearchRequestId) return; // stale response, ignore
  let parsed = [];
  try { parsed = JSON.parse(jsonArrayString); } catch (e) { parsed = []; }
  renderResults(parsed);
};

window.onYouTubeSearchError = function (requestId, message) {
  if (requestId !== latestSearchRequestId) return;
  console.error('[Jinka] search failed:', message);
  hintEl.textContent = 'Search failed. Try again.';
  resultsEl.innerHTML = '';
  alert('Search failed:\n\n' + message);
};

searchInput.addEventListener('input', () => {
  clearTimeout(searchDebounceTimer);
  const query = searchInput.value;
  try { sessionStorage.setItem('jinka.search.query', query); } catch (_) {}
  searchDebounceTimer = setTimeout(() => runSearch(query), 450);
});

searchInput.addEventListener('search', () => {
  clearTimeout(searchDebounceTimer);
  runSearch(searchInput.value);
});


try {
  const savedQuery = sessionStorage.getItem('jinka.search.query');
  const savedResults = sessionStorage.getItem('jinka.search.results');
  if (savedQuery !== null) searchInput.value = savedQuery;
  if (savedResults) {
    const parsed = JSON.parse(savedResults);
    if (Array.isArray(parsed) && parsed.length) renderResults(parsed);
  }
} catch (_) {}
