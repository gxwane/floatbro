/**
 * FloatBro Lightweight DOM Pagination Sanitizer & Touch Hijacking Neutralizer
 *
 * Scans for chapter/page pagination links, cleans time-of-use event listeners,
 * performs top-vs-bottom structural cross-validation, and extrapolates sequential chapter integers.
 * Pure non-destructive implementation: never invokes cloneNode to preserve Virtual DOM / VNode mapping.
 */
(function() {
    if (window.__floatBroPaginationSanitizerInjected) return;
    window.__floatBroPaginationSanitizerInjected = true;

    var EXCLUDE_SELECTORS = '#content, #chaptercontent, .content, .read-content, #comments, .comment-box, .reply-list, article, [role="article"]';
    var PAGINATION_TEXT_REGEX = /下一[章页回节步]|下[一]?页|next\s*chapter|next\s*page/i;

    function isExcluded(el) {
        if (!el || !el.closest) return false;
        try {
            return el.closest(EXCLUDE_SELECTORS) !== null;
        } catch (_) {
            return false;
        }
    }

    function computeSequentialNextUrl() {
        try {
            var path = window.location.pathname || '';
            var match = path.match(/^(.*?)(\d+)(\.[a-zA-Z0-9]+|\/)?$/);
            if (match && match[2]) {
                var prefix = match[1];
                var numStr = match[2];
                var suffix = match[3] || '';
                var nextNum = parseInt(numStr, 10) + 1;
                // Preserve leading zeros if any
                var padded = (nextNum + '').padStart(numStr.length, '0');
                return window.location.origin + prefix + padded + suffix;
            }
        } catch (_) {}
        return null;
    }

    function sanitizeAnchor(anchor, fallbackNextUrl) {
        if (!anchor || anchor.__floatBroSanitized) return;
        anchor.__floatBroSanitized = true;
        anchor.setAttribute('data-floatbro-pagination', 'true');

        if (fallbackNextUrl) {
            anchor.setAttribute('data-floatbro-fallback-next', fallbackNextUrl);
        }

        // 1. Neutralize rogue inline script attributes
        var onclickAttr = anchor.getAttribute('onclick') || '';
        if (/location|window\.open|http|void\(0\)/i.test(onclickAttr)) {
            anchor.removeAttribute('onclick');
        }
        anchor.removeAttribute('ontouchstart');
        anchor.removeAttribute('onpointerdown');

        // 2. Install high-priority capture-phase event listener
        anchor.addEventListener('click', function(e) {
            // Notify native kernel of active pagination click window
            try {
                if (window.FloatBroNavBridge && typeof window.FloatBroNavBridge.notifyPaginationClicked === 'function') {
                    window.FloatBroNavBridge.notifyPaginationClicked();
                }
            } catch (_) {}

            // Check if href was secretly rewritten by rogue scripts to an external origin
            var fallback = anchor.getAttribute('data-floatbro-fallback-next');
            if (fallback && anchor.href && anchor.href.indexOf(window.location.hostname) === -1) {
                anchor.href = fallback;
            }
        }, true);
    }

    function runSanitization() {
        var fallbackNextUrl = computeSequentialNextUrl();
        var allAnchors = document.querySelectorAll('a[href], a[rel~="next"], .next, .btn-next, #next_url');
        var candidates = [];

        for (var i = 0; i < allAnchors.length; i++) {
            var a = allAnchors[i];
            if (isExcluded(a)) continue;

            var text = (a.innerText || a.textContent || '').trim();
            var rel = a.getAttribute('rel') || '';
            var className = a.className || '';
            var id = a.id || '';

            var isNext = rel.toLowerCase() === 'next' ||
                PAGINATION_TEXT_REGEX.test(text) ||
                /(^|\s)(next|btn-next|next-page)(\s|$)/i.test(className) ||
                id === 'next_url';

            if (isNext) {
                candidates.push(a);
            }
        }

        if (candidates.length === 0) return;

        // Structural cross-validation: top navigation vs bottom navigation
        var topCleanHref = null;
        for (var j = 0; j < candidates.length; j++) {
            var cand = candidates[j];
            var href = cand.getAttribute('href') || '';
            // If candidate is near top of document or has clean same-origin href
            if (href.indexOf('http') === -1 || href.indexOf(window.location.hostname) !== -1) {
                if (!topCleanHref && href !== '#' && !href.startsWith('javascript:')) {
                    topCleanHref = cand.href;
                }
            }
        }

        var effectiveFallback = topCleanHref || fallbackNextUrl;

        for (var k = 0; k < candidates.length; k++) {
            var target = candidates[k];
            var rawHref = target.getAttribute('href') || '';
            // If bottom link points to rogue external domain or trampoline
            if (effectiveFallback && (rawHref.indexOf('://') !== -1 && rawHref.indexOf(window.location.hostname) === -1)) {
                target.href = effectiveFallback;
            }
            sanitizeAnchor(target, effectiveFallback);
        }
    }

    // Run when DOM is interactive and again on complete
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', runSanitization);
    } else {
        runSanitization();
    }
    window.addEventListener('load', runSanitization);

    // Watch for dynamic infinite-scroll / SPA chapter loading
    try {
        var observer = new MutationObserver(function(mutations) {
            for (var m = 0; m < mutations.length; m++) {
                if (mutations[m].addedNodes && mutations[m].addedNodes.length > 0) {
                    runSanitization();
                    break;
                }
            }
        });
        observer.observe(document.body || document.documentElement, { childList: true, subtree: true });
    } catch (_) {}
})();
