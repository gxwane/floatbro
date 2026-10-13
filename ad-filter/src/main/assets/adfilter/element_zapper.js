/*
 * FloatBro Lightweight Mobile Element Zapper
 * Touch-optimized DOM inspection, highlight, and robust selector generation.
 * Follows AUDIT-SEC-ARCH-20261013-02 guidelines.
 */
(function() {
    'use strict';

    if (window.__FloatBroZapper && window.__FloatBroZapper.isActive) {
        return;
    }

    var TOUCH_SLOP = 10;
    var startX = 0, startY = 0, startTime = 0;
    var isZapperActive = false;
    var targetStack = [];
    var overlayEl = null;
    var tempStyleEl = null;

    var ATOMIC_CLASS_REGEX = /^(flex|grid|block|inline|w-|h-|p-|m-|text-|bg-|border-|shadow-|justify-|items-|col-|row-|relative|absolute|fixed|overflow-|z-)/i;
    var DYNAMIC_HASH_REGEX = /(\d{5,}|[a-f0-9]{8,}|^css-|^jsx-|^__[a-zA-Z0-9]{3,})/i;
    var GENERIC_CLASSES = ['container', 'wrapper', 'box', 'item', 'content', 'main', 'wrap', 'inner', 'outer', 'header', 'footer', 'body'];

    function createOverlay() {
        if (!overlayEl) {
            overlayEl = document.createElement('div');
            overlayEl.id = '__floatbro_zapper_overlay__';
            overlayEl.style.position = 'fixed';
            overlayEl.style.pointerEvents = 'none';
            overlayEl.style.zIndex = '2147483647';
            overlayEl.style.border = '2px solid #ef4444';
            overlayEl.style.backgroundColor = 'rgba(239, 68, 68, 0.2)';
            overlayEl.style.borderRadius = '4px';
            overlayEl.style.boxSizing = 'border-box';
            overlayEl.style.display = 'none';
            overlayEl.style.transition = 'all 0.1s ease';
            document.documentElement.appendChild(overlayEl);
        }
    }

    function updateOverlay(el) {
        if (!el || !overlayEl) return;
        var rect = el.getBoundingClientRect();
        if (rect.width === 0 && rect.height === 0) {
            overlayEl.style.display = 'none';
            return;
        }
        overlayEl.style.left = rect.left + 'px';
        overlayEl.style.top = rect.top + 'px';
        overlayEl.style.width = rect.width + 'px';
        overlayEl.style.height = rect.height + 'px';
        overlayEl.style.display = 'block';
    }

    function isCeilingElement(el) {
        if (!el) return true;
        var tag = el.tagName ? el.tagName.toUpperCase() : '';
        if (tag === 'HTML' || tag === 'BODY' || tag === 'HEAD') return true;
        var id = el.id ? el.id.toLowerCase() : '';
        if (id === 'root' || id === 'app' || id === '__next') return true;
        return false;
    }

    function cleanClass(cls) {
        if (!cls || typeof cls !== 'string') return null;
        cls = cls.trim();
        if (!cls) return null;
        if (ATOMIC_CLASS_REGEX.test(cls)) return null;
        if (DYNAMIC_HASH_REGEX.test(cls)) return null;
        if (GENERIC_CLASSES.indexOf(cls.toLowerCase()) !== -1) return null;
        return cls;
    }

    function getCleanClasses(el) {
        if (!el.className || typeof el.className !== 'string') return [];
        var parts = el.className.split(/\s+/);
        var res = [];
        for (var i = 0; i < parts.length; i++) {
            var c = cleanClass(parts[i]);
            if (c && res.indexOf(c) === -1) {
                res.push(c);
            }
        }
        return res;
    }

    function generateSelector(el) {
        if (!el) return null;
        var tag = el.tagName.toLowerCase();

        // Tier 1: iframe
        if (tag === 'iframe') {
            var src = el.getAttribute('src');
            if (src && src.indexOf('http') === 0) {
                try {
                    var u = new URL(src);
                    return 'iframe[src*="' + CSS.escape(u.hostname) + '"]';
                } catch (_) {}
            }
        }

        // Tier 2: Ad-specific data attributes
        var dataAd = el.getAttribute('data-ad-slot') || el.getAttribute('data-ad') || el.getAttribute('data-ad-unit');
        if (dataAd) {
            return tag + '[data-ad-slot="' + CSS.escape(dataAd) + '"]';
        }

        // Tier 3: Stable ID
        if (el.id && !DYNAMIC_HASH_REGEX.test(el.id)) {
            var idSel = '#' + CSS.escape(el.id);
            try {
                if (document.querySelectorAll(idSel).length === 1) {
                    return idSel;
                }
            } catch (_) {}
        }

        // Tier 4: Clean semantic class names
        var classes = getCleanClasses(el);
        if (classes.length > 0) {
            for (var c = 0; c < classes.length; c++) {
                var classSel = '.' + CSS.escape(classes[c]);
                try {
                    if (document.querySelectorAll(classSel).length === 1) {
                        return classSel;
                    }
                    var tagClassSel = tag + '.' + CSS.escape(classes[c]);
                    if (document.querySelectorAll(tagClassSel).length === 1) {
                        return tagClassSel;
                    }
                } catch (_) {}
            }
        }

        // Tier 5: Hierarchical parent path
        var parent = el.parentElement;
        if (parent && !isCeilingElement(parent)) {
            var parentClasses = getCleanClasses(parent);
            if (parent.id && !DYNAMIC_HASH_REGEX.test(parent.id)) {
                var pIdSel = '#' + CSS.escape(parent.id) + ' > ' + tag;
                try {
                    if (document.querySelectorAll(pIdSel).length === 1) return pIdSel;
                } catch (_) {}
            } else if (parentClasses.length > 0) {
                var pClassSel = '.' + CSS.escape(parentClasses[0]) + ' > ' + tag;
                try {
                    if (document.querySelectorAll(pClassSel).length === 1) return pClassSel;
                } catch (_) {}
            }
        }

        // Tier 6: Tag with nth-of-type if parent is identifiable
        if (parent && parent.children) {
            var siblings = parent.children;
            var index = 1;
            for (var s = 0; s < siblings.length; s++) {
                if (siblings[s] === el) break;
                if (siblings[s].tagName === el.tagName) index++;
            }
            if (parent.id && !DYNAMIC_HASH_REGEX.test(parent.id)) {
                return '#' + CSS.escape(parent.id) + ' > ' + tag + ':nth-of-type(' + index + ')';
            }
        }

        if (classes.length > 0) {
            return '.' + CSS.escape(classes[0]);
        }
        return tag;
    }

    function onTouchStart(e) {
        if (!isZapperActive || !e.touches || e.touches.length === 0) return;
        var t = e.touches[0];
        startX = t.clientX;
        startY = t.clientY;
        startTime = Date.now();
    }

    function onTouchEnd(e) {
        if (!isZapperActive || !e.changedTouches || e.changedTouches.length === 0) return;
        var t = e.changedTouches[0];
        var dx = Math.abs(t.clientX - startX);
        var dy = Math.abs(t.clientY - startY);
        var dt = Date.now() - startTime;

        // Disambiguate tap vs scroll (TouchSlop = 10px, dt < 350ms)
        if (dx < TOUCH_SLOP && dy < TOUCH_SLOP && dt < 350) {
            e.preventDefault();
            e.stopPropagation();
            selectAt(t.clientX, t.clientY);
        }
    }

    function onClick(e) {
        if (!isZapperActive) return;
        e.preventDefault();
        e.stopPropagation();
    }

    function selectAt(x, y) {
        if (overlayEl) overlayEl.style.display = 'none';
        var target = document.elementFromPoint(x, y);
        if (overlayEl) overlayEl.style.display = 'block';

        if (!target || isCeilingElement(target)) {
            return;
        }

        // Elevate iframe
        if (target.tagName.toLowerCase() === 'iframe') {
            // Already iframe
        } else {
            var closestIframe = target.closest('iframe');
            if (closestIframe) {
                target = closestIframe;
            }
        }

        targetStack = [target];
        updateOverlay(target);
    }

    window.__FloatBroZapper = {
        isActive: false,

        init: function() {
            createOverlay();
            isZapperActive = true;
            this.isActive = true;
            targetStack = [];
            window.addEventListener('touchstart', onTouchStart, { capture: true, passive: true });
            window.addEventListener('touchend', onTouchEnd, { capture: true, passive: false });
            window.addEventListener('click', onClick, { capture: true });
            return JSON.stringify({ status: 'ready' });
        },

        expandParent: function() {
            if (!isZapperActive || targetStack.length === 0) {
                return JSON.stringify({ success: false, canExpand: false, canShrink: false });
            }
            var current = targetStack[targetStack.length - 1];
            var parent = current.parentElement;
            if (!parent || isCeilingElement(parent)) {
                return JSON.stringify({ success: false, canExpand: false, canShrink: targetStack.length > 1, reason: 'ceiling' });
            }
            targetStack.push(parent);
            updateOverlay(parent);
            var nextParent = parent.parentElement;
            var canExpand = nextParent && !isCeilingElement(nextParent);
            return JSON.stringify({
                success: true,
                canExpand: canExpand,
                canShrink: true,
                tagName: parent.tagName.toLowerCase()
            });
        },

        shrinkChild: function() {
            if (!isZapperActive || targetStack.length <= 1) {
                return JSON.stringify({ success: false, canShrink: false, canExpand: targetStack.length > 0 });
            }
            targetStack.pop();
            var current = targetStack[targetStack.length - 1];
            if (!document.documentElement.contains(current)) {
                if (overlayEl) overlayEl.style.display = 'none';
                return JSON.stringify({ success: false, canShrink: false, canExpand: false });
            }
            updateOverlay(current);
            var parent = current.parentElement;
            var canExpand = parent && !isCeilingElement(parent);
            return JSON.stringify({
                success: true,
                canShrink: targetStack.length > 1,
                canExpand: canExpand,
                tagName: current.tagName ? current.tagName.toLowerCase() : ''
            });
        },

        getSelectionState: function() {
            if (!isZapperActive || targetStack.length === 0) {
                return JSON.stringify({ hasTarget: false, canExpand: false, canShrink: false });
            }
            var current = targetStack[targetStack.length - 1];
            var parent = current.parentElement;
            var canExpand = parent && !isCeilingElement(parent);
            return JSON.stringify({
                hasTarget: true,
                canExpand: canExpand,
                canShrink: targetStack.length > 1,
                tagName: current.tagName ? current.tagName.toLowerCase() : ''
            });
        },

        hideCurrent: function() {
            if (!isZapperActive || targetStack.length === 0) {
                return JSON.stringify({ success: false });
            }
            var current = targetStack[targetStack.length - 1];
            current.setAttribute('data-floatbro-zap-temp', 'true');

            if (!tempStyleEl) {
                tempStyleEl = document.createElement('style');
                tempStyleEl.id = '__floatbro_zapper_temp_css__';
                tempStyleEl.textContent = '[data-floatbro-zap-temp="true"] { display: none !important; }';
                document.documentElement.appendChild(tempStyleEl);
            }

            if (overlayEl) overlayEl.style.display = 'none';
            return JSON.stringify({ success: true });
        },

        getCurrentSelector: function() {
            if (targetStack.length === 0) return null;
            var current = targetStack[targetStack.length - 1];
            var sel = generateSelector(current);
            return JSON.stringify({
                selector: sel,
                tagName: current.tagName.toLowerCase()
            });
        },

        exit: function() {
            isZapperActive = false;
            this.isActive = false;
            window.removeEventListener('touchstart', onTouchStart, { capture: true, passive: true });
            window.removeEventListener('touchend', onTouchEnd, { capture: true, passive: false });
            window.removeEventListener('click', onClick, { capture: true });

            if (overlayEl && overlayEl.parentNode) {
                overlayEl.parentNode.removeChild(overlayEl);
                overlayEl = null;
            }

            // NOTE: Do NOT remove tempStyleEl or data-floatbro-zap-temp attribute!
            // Elements that were zapped in this session must remain hidden.

            targetStack = [];
            return JSON.stringify({ status: 'exited' });
        }
    };

    window.__FloatBroZapper.init();
})();
