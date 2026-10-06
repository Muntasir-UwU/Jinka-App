
(() => {
  const MIN_VISIBLE_MS = 320;
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function hideLoader(loader) {
    window.dispatchEvent(new CustomEvent('jinka:reveal'));
    loader.classList.add('is-hidden');
    loader.addEventListener('transitionend', () => {
      loader.setAttribute('hidden', '');
    }, { once: true });
  }

  document.addEventListener('DOMContentLoaded', () => {
    const loader = document.querySelector('.jinka-loader:not(.jinka-loader--inline)');
    if (!loader) {
      window.dispatchEvent(new CustomEvent('jinka:reveal'));
      return;
    }
    const delay = reduceMotion ? 0 : MIN_VISIBLE_MS;
    setTimeout(() => hideLoader(loader), delay);
  });
})();
