

const libraryResultsEl = document.getElementById('libraryResults');
const libraryEmptyEl = document.getElementById('libraryEmpty');


let libraryTracks = [];
let playingIndex = null;

const TRIPLE_CLICK_WINDOW_MS = 600;
const clickTimestampsByIndex = new Map();

function syncLibraryPlayer() {
  const state = window.JinkaPlayer.getState();
  playingIndex = libraryTracks.findIndex((t) => t && t.localPath === state.url);
  clearHighlight();
  if (playingIndex >= 0) {
    const row = libraryResultsEl.querySelector('.song-row[data-index="' + playingIndex + '"]');
    if (row) row.classList.toggle('is-previewing', !state.paused);
  }
}

window.JinkaPlayer.subscribe(syncLibraryPlayer);

function hasBridge() {
  return typeof window.AndroidYT !== 'undefined' && window.AndroidYT !== null;
}

function nextRequestId() {
  return 'library-' + Date.now();
}

function renderLibrary(tracks) {
  libraryTracks = tracks;
  libraryResultsEl.innerHTML = '';
  clickTimestampsByIndex.clear();

  if (!tracks.length) {
    libraryEmptyEl.hidden = false;
    return;
  }
  libraryEmptyEl.hidden = true;

  tracks.forEach((track, index) => {
    const row = document.createElement('div');
    row.className = 'song-row';
    row.dataset.index = String(index);

    const thumb = document.createElement('div');
    thumb.className = 'song-row-thumb';
    if (track.thumbnailUrl) {
      const img = document.createElement('img');
      img.src = track.thumbnailUrl;
      img.alt = '';
      img.draggable = false;
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
    artist.textContent = track.artist || '';
    body.appendChild(title);
    body.appendChild(artist);

    const meta = document.createElement('div');
    meta.className = 'song-row-status';
    meta.textContent = '';

    row.appendChild(thumb);
    row.appendChild(body);
    row.appendChild(meta);

    row.addEventListener('click', () => handleRowClick(index, row));
    libraryResultsEl.appendChild(row);
  });
  syncLibraryPlayer();
}

function clearHighlight() {
  libraryResultsEl.querySelectorAll('.song-row.is-previewing').forEach((el) => {
    el.classList.remove('is-previewing');
  });
}

function togglePlay(index) {
  const track = libraryTracks[index];
  if (!track || !track.localPath) return;

  const state = window.JinkaPlayer.getState();
  if (state.url === track.localPath) {
    if (state.paused) window.JinkaPlayer.play();
    else window.JinkaPlayer.pause();
    return;
  }

  window.JinkaPlayer.load({
    id: track.id,
    title: track.title,
    artist: track.artist,
    thumbnailUrl: track.thumbnailUrl,
    url: track.localPath
  }, true);
}

function handleRowClick(index, row) {
  const now = Date.now();
  const stamps = (clickTimestampsByIndex.get(index) || []).filter(
    (t) => now - t < TRIPLE_CLICK_WINDOW_MS
  );
  stamps.push(now);

  if (stamps.length >= 3) {
    clickTimestampsByIndex.delete(index);
    removeTrack(index, row);
    return;
  }

  clickTimestampsByIndex.set(index, stamps);
  togglePlay(index);
}

function removeTrack(index, row) {
  const track = libraryTracks[index];
  if (!track) return;

  const state = window.JinkaPlayer.getState();
  if (state.url === track.localPath) {
    window.JinkaPlayer.pause();
  }

  let finished = false;
  const finish = () => {
    if (finished) return;
    finished = true;
    if (hasBridge() && typeof window.AndroidYT.deleteOfflineTrack === 'function') {
      window.AndroidYT.deleteOfflineTrack(nextRequestId(), track.source, track.id);
    }
    renderLibrary(libraryTracks.filter((_, i) => i !== index));
  };

  row.classList.add('is-removing');
  row.addEventListener('transitionend', finish, { once: true });
  setTimeout(finish, 220);
}

window.onOfflineTrackDeleteError = function (requestId, message) {
  if (hasBridge()) window.AndroidYT.listOfflineTracks(nextRequestId());
};

window.onOfflineTracksListed = function (requestId, jsonArrayString) {
  let parsed = [];
  try { parsed = JSON.parse(jsonArrayString); } catch (e) { parsed = []; }
  renderLibrary(parsed);
};

if (hasBridge()) {
  window.AndroidYT.listOfflineTracks(nextRequestId());
} else {
  libraryEmptyEl.hidden = false;
}
