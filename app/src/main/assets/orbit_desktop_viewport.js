/* User-requested desktop layout only. Removed with document-script handlers on mobile reload. */
(function () {
  'use strict';
  if (window.top !== window || window.__hiDesktopViewport) return;
  window.__hiDesktopViewport = true;
  var content = 'width=1280';
  var observedHead = null;
  function apply() {
    var head = document.head;
    if (!head) return;
    // Stop watching page-body churn once the parser has supplied a head.
    if (observedHead !== head) {
      observer.disconnect();
      observer.observe(head, { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] });
      observedHead = head;
    }
    var viewport = head.querySelector('meta[name="viewport" i]');
    if (!viewport) {
      viewport = document.createElement('meta');
      viewport.name = 'viewport';
      head.appendChild(viewport);
    }
    if (viewport.getAttribute('content') !== content) viewport.setAttribute('content', content);
  }
  // The head/viewport may be parsed after this document-start script runs.
  var observer = new MutationObserver(apply);
  observer.observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] });
  window.addEventListener('pagehide', function (event) { if (!event.persisted) observer.disconnect(); });
  document.addEventListener('DOMContentLoaded', apply, { once: true });
  apply();
})();
