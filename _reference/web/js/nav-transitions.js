
(() => {
  const NAV_ORDER = ['home', 'search', 'library', 'user']; // left-to-right, matches the bottom nav
  const PAGE_FILE = { home: 'index.html', search: 'search.html', library: 'library.html', user: 'user.html' };
  const NAV_DIR_KEY = 'jinka:navDir';

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduceMotion) return;

  function currentPage() {
    return document.body.getAttribute('data-page') || 'home';
  }

  function pageFromHref(href) {
    try {
      const file = new URL(href, location.href).pathname.split('/').pop() || 'index.html';
      return Object.keys(PAGE_FILE).find((k) => PAGE_FILE[k] === file) || null;
    } catch (_) {
      return null;
    }
  }

  function directionTo(targetPage) {
    const from = NAV_ORDER.indexOf(currentPage());
    const to = NAV_ORDER.indexOf(targetPage);
    if (from === -1 || to === -1 || to === from) return null;
    return to > from ? 'forward' : 'back';
  }

  document.addEventListener('DOMContentLoaded', () => {
    const shell = document.querySelector('.app-shell');
    if (!shell) return;

    let enterDir = 'forward';
    try {
      enterDir = sessionStorage.getItem(NAV_DIR_KEY) || 'forward';
      sessionStorage.removeItem(NAV_DIR_KEY);
    } catch (_) {  }

    let entered = false;
    const startEnter = () => {
      if (entered) return;
      entered = true;
      const enterClass = enterDir === 'back' ? 'jinka-entering-back' : 'jinka-entering-forward';
      shell.classList.add(enterClass);
      shell.addEventListener('animationend', () => {
        shell.classList.remove('jinka-entering-forward', 'jinka-entering-back');
      }, { once: true });
    };

    window.addEventListener('jinka:reveal', startEnter, { once: true });
    setTimeout(startEnter, 700); // fallback if the splash/reveal never happens

    document.querySelectorAll('a[href$=".html"]').forEach((link) => {
      link.addEventListener('click', (ev) => {
        const targetPage = pageFromHref(link.getAttribute('href'));
        if (!targetPage) return;
        if (targetPage === currentPage()) {
          ev.preventDefault();
          return;
        }
        ev.preventDefault();

        const dir = directionTo(targetPage) || 'forward';
        try { sessionStorage.setItem(NAV_DIR_KEY, dir); } catch (_) {  }

        const leaveClass = dir === 'back' ? 'jinka-leaving-back' : 'jinka-leaving-forward';
        shell.classList.add(leaveClass);

        const go = () => { window.location.href = link.href; };
        shell.addEventListener('animationend', go, { once: true });
        setTimeout(go, 280); // safety net in case animationend doesn't fire (leave anim is 240ms — don't cut it short)
      }, { passive: false });
    });
  });
})();
