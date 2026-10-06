
(() => {


const toastEl = document.getElementById('toast');
let toastTimer = null;
function showToast(msg, ms = 2200) {
  toastEl.textContent = msg;
  toastEl.classList.add('is-visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.remove('is-visible'), ms);
}


const sheetBackdrop = document.getElementById('sheetBackdrop');
function openSheet(sheet) {
  sheetBackdrop.hidden = false;
  requestAnimationFrame(() => {
    sheetBackdrop.classList.add('is-open');
    sheet.classList.add('is-open');
  });
  sheet.setAttribute('aria-hidden', 'false');
}
function closeSheets() {
  document.querySelectorAll('.sheet.is-open').forEach(s => {
    s.classList.remove('is-open');
    s.setAttribute('aria-hidden', 'true');
  });
  sheetBackdrop.classList.remove('is-open');
  setTimeout(() => { if (!document.querySelector('.sheet.is-open')) sheetBackdrop.hidden = true; }, 320);
}
sheetBackdrop.addEventListener('click', closeSheets);


const DEMO_STATE = {
  name: 'Alex'
};

function listenStats() {
  const api = window.JinkaListenStats || {};
  return {
    todaySeconds: typeof api.getTodaySeconds === 'function' ? api.getTodaySeconds() : 0,
    avgDailyMinutesLast4Days: typeof api.getAvgLast4DaysMinutes === 'function' ? api.getAvgLast4DaysMinutes() : 0
  };
}

function formatHM(totalSeconds) {
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

function getQuote({ todaySeconds, avgDailyMinutesLast4Days }) {
  const hoursToday = todaySeconds / 3600;

  if (hoursToday >= 4) {
    return `🎉 Congratulations — you've heard ${formatHM(todaySeconds)} today!`;
  }
  if (avgDailyMinutesLast4Days < 10) {
    return `Haven't heard much from you these past few days… didn't you like the app?`;
  }
  if (avgDailyMinutesLast4Days < 30) {
    return `Somewhat a good listener — keep it up 👀`;
  }
  return `A proud regular listener of ours`;
}

const userNameEl = document.getElementById('userName');
const userQuoteEl = document.getElementById('userQuote');
const avatarInitialEl = document.getElementById('avatarInitial');

let lastQuote = null;
function renderGreeting() {
  userNameEl.textContent = DEMO_STATE.name;
  avatarInitialEl.textContent = DEMO_STATE.name.charAt(0).toUpperCase();
  const quote = getQuote(listenStats());
  if (quote === lastQuote) return;
  lastQuote = quote;
  userQuoteEl.style.opacity = 0;
  setTimeout(() => {
    userQuoteEl.textContent = quote;
    userQuoteEl.style.opacity = 1;
  }, 120);
}
renderGreeting();


const listenedTimeEl = document.getElementById('listenedTime');
function pad(n) { return String(n).padStart(2, '0'); }
function renderListened() {
  const s = listenStats().todaySeconds;
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = Math.floor(s % 60);
  listenedTimeEl.textContent = `${pad(h)}:${pad(m)}:${pad(sec)}`;
}
renderListened();
setInterval(() => {
  renderListened();
  renderGreeting();
}, 1000);


const timerCard = document.getElementById('timerCard');
const timerSub = document.getElementById('timerSub');
const timerSheet = document.getElementById('timerSheet');
const timerClose = document.getElementById('timerClose');
const timerChips = document.getElementById('timerChips');
const customMins = document.getElementById('customMins');
const timerStartBtn = document.getElementById('timerStartBtn');
const timerCancelBtn = document.getElementById('timerCancelBtn');

let selectedMins = null;

function renderTimerCard() {
  if (JinkaSleepTimer.isActive() && JinkaSleepTimer.remaining() > 0) {
    const r = JinkaSleepTimer.remaining();
    const m = Math.floor(r / 60);
    const s = r % 60;
    timerSub.textContent = `Stops in ${pad(m)}:${pad(s)}`;
    timerCard.classList.add('is-active');
  } else {
    timerSub.textContent = 'Off';
    timerCard.classList.remove('is-active');
  }
}

timerCard.addEventListener('click', () => openSheet(timerSheet));
timerClose.addEventListener('click', closeSheets);

timerChips.addEventListener('click', (e) => {
  const chip = e.target.closest('.chip');
  if (!chip) return;
  selectedMins = Number(chip.dataset.mins);
  customMins.value = '';
  [...timerChips.children].forEach(c => c.classList.toggle('is-selected', c === chip));
});
customMins.addEventListener('input', () => {
  if (customMins.value) {
    selectedMins = Number(customMins.value);
    [...timerChips.children].forEach(c => c.classList.remove('is-selected'));
  }
});

timerStartBtn.addEventListener('click', () => {
  if (!selectedMins || selectedMins <= 0) {
    showToast('Pick or enter a duration first.');
    return;
  }
  JinkaSleepTimer.start(selectedMins);
  renderTimerCard();
  closeSheets();
  showToast(`Timer set for ${selectedMins} min. It keeps running across tabs.`);
});

timerCancelBtn.addEventListener('click', () => {
  JinkaSleepTimer.cancel();
  selectedMins = null;
  timerCard.classList.remove('is-active');
  timerSub.textContent = 'Off';
  [...timerChips.children].forEach(c => c.classList.remove('is-selected'));
  customMins.value = '';
  closeSheets();
});

JinkaSleepTimer.subscribe(renderTimerCard);

window.addEventListener('jinka:sleepElapsed', () => {
  showToast('Timer\u2019s up — playback stopped.');
});


const pokeCard = document.getElementById('pokeCard');
const pokeBadge = document.getElementById('pokeBadge');
const pokeSub = document.getElementById('pokeSub');
let pokeCount = 0;

pokeCard.addEventListener('click', () => {
  pokeCount += 1;
  pokeBadge.hidden = false;
  pokeBadge.textContent = pokeCount;
  pokeSub.textContent = 'Poked!';
  pokeCard.classList.add('is-poked');
  setTimeout(() => pokeCard.classList.remove('is-poked'), 350);
});


const messageBtn = document.getElementById('messageBtn');
const messageSheet = document.getElementById('messageSheet');
const messageClose = document.getElementById('messageClose');
const msgInput = document.getElementById('msgInput');
const msgSend = document.getElementById('msgSend');
const msgLog = document.getElementById('msgLog');

messageBtn.addEventListener('click', () => {
  openSheet(messageSheet);
  setTimeout(() => msgInput.focus(), 320); // focus after the sheet finishes sliding up -> keypad pops
});
messageClose.addEventListener('click', closeSheets);

msgInput.addEventListener('input', () => {
  msgInput.style.height = 'auto';
  msgInput.style.height = Math.min(120, msgInput.scrollHeight) + 'px';
});

function sendMessage() {
  const text = msgInput.value.trim();
  if (!text) return;
  const empty = msgLog.querySelector('.msg-empty');
  if (empty) empty.remove();
  const bubble = document.createElement('div');
  bubble.className = 'msg-bubble';
  bubble.textContent = text;
  msgLog.appendChild(bubble);
  msgLog.scrollTop = msgLog.scrollHeight;
  msgInput.value = '';
  msgInput.style.height = 'auto';
}
msgSend.addEventListener('click', sendMessage);
msgInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
});

})();
