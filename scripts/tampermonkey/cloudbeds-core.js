// Cloudbeds userscript shared core (vanilla JS, no jQuery).
//
// This file is @require'd by the individual Cloudbeds Tampermonkey scripts.
// It replaces the old jQuery + waitForKeyElements (setInterval polling) approach
// with an event-driven model:
//   - reservation views are SPA hash routes (/connect/{propertyId}#/reservations/{reservationId}),
//     so we react to hashchange/popstate instead of polling forever;
//   - element waiting uses a scoped MutationObserver that disconnects on first
//     match or after a timeout, instead of an eternal 300ms poll.
//
// Everything is exposed on window.CB inside Tampermonkey's isolated sandbox.
// Typing window.CB in the page DevTools console will always be undefined; that
// is normal and does not mean the scripts are not running.
//
// Feature scripts should declare: // @grant unsafeWindow
// so we can patch the page's history (SPA pushState lives on the page realm).

(function () {
    'use strict';

    if (window.CB) { return; } // already initialised in this sandbox

    var CORE_VERSION = '1.2';
    // One visible line so we can confirm in DevTools which core build is running.
    console.info('[CB] cloudbeds-core v' + CORE_VERSION + ' loaded');

    // Page realm (Cloudbeds app) vs Tampermonkey sandbox. pushState runs on the page.
    var pageWindow = (typeof unsafeWindow !== 'undefined') ? unsafeWindow : window;

    function currentHref() {
        try {
            return pageWindow.location.href;
        } catch (e) {
            return location.href;
        }
    }

    // Cloudbeds connect is a single-page app: opening a booking changes the URL to
    // /connect/{propertyId}#/reservations/{id} via history.pushState, which fires
    // NEITHER hashchange NOR popstate. Patch the PAGE history so SPA navigations
    // emit our unified 'cb:locationchange' event into this sandbox.
    //
    // The patch can still be bypassed if the app captured a bound reference to
    // pushState before we ran (userscripts inject at document-idle), so a cheap
    // URL watcher (single string compare per tick) acts as a guaranteed fallback.
    function installLocationChange() {
        if (window.__cbLocationChangePatched) { return; }
        window.__cbLocationChangePatched = true;

        var lastHref = currentHref();

        function emit(source) {
            lastHref = currentHref();
            console.debug('[CB] locationchange via ' + source + ': ' + lastHref);
            window.dispatchEvent(new Event('cb:locationchange'));
        }

        ['pushState', 'replaceState'].forEach(function (method) {
            var original = pageWindow.history[method];
            if (typeof original !== 'function') { return; }
            pageWindow.history[method] = function () {
                var result = original.apply(this, arguments);
                emit(method);
                return result;
            };
        });

        pageWindow.addEventListener('popstate', function () { emit('popstate'); });
        pageWindow.addEventListener('hashchange', function () { emit('hashchange'); });
        // Also listen on the sandbox window in case TM mirrors these events.
        window.addEventListener('popstate', function () { emit('popstate:sandbox'); });
        window.addEventListener('hashchange', function () { emit('hashchange:sandbox'); });

        // Fallback URL watcher: catches SPA navigations that bypass the patch.
        setInterval(function () {
            if (currentHref() !== lastHref) { emit('urlwatch'); }
        }, 1000);
    }
    installLocationChange();

    var HOSTEL_MAP = [
        { title: 'Castle Rock', path: 'CRH' },
        { title: 'High Street', path: 'HSH' },
        { title: 'Royal Mile', path: 'RMB' },
        { title: 'Lochside', path: 'LSH' }
    ];

    // Detect the hostel from the document <title>. Returns the matching map entry
    // or null when zero / more than one match (mirrors the old $.grep length check).
    function detectHostel() {
        var title = document.title || '';
        var matches = HOSTEL_MAP.filter(function (h) { return title.indexOf(h.title) !== -1; });
        return matches.length === 1 ? matches[0] : null;
    }

    // Prefer page location (SPA updates) over sandbox location if they ever diverge.
    function currentLocation() {
        try {
            return pageWindow.location || location;
        } catch (e) {
            return location;
        }
    }

    // Parse the current route. reservationId is null when not on a reservation view.
    function context() {
        var loc = currentLocation();
        var propMatch = /\/connect\/(\d+)/.exec(loc.pathname);
        var resMatch = /#\/reservations\/(\d+)/.exec(loc.hash);
        var hostel = detectHostel();
        return {
            host: loc.host,
            propertyId: propMatch ? propMatch[1] : null,
            reservationId: resMatch ? resMatch[1] : null,
            hostel: hostel,               // { title, path } or null
            hostelPath: hostel ? hostel.path : null
        };
    }

    // Register a handler that runs once each time a reservation view is entered.
    // fn(ctx, meta) may return a Promise; resolve with false to allow retry on the
    // next route change (e.g. waitFor timed out). meta.skipExisting is true when
    // navigating booking→booking so waiters ignore stale DOM from the previous one.
    function onReservation(key, fn) {
        var completedFor = {};
        var inFlightFor = {};
        var prevId = null;

        function tryRun() {
            var ctx = context();
            if (!ctx.reservationId) { return; }

            var id = ctx.reservationId;
            var skipExisting = (prevId !== null && prevId !== id);
            if (prevId && prevId !== id) {
                delete inFlightFor[prevId]; // abandon wait for previous booking
            }
            prevId = id;

            if (completedFor[id] || inFlightFor[id]) {
                console.debug('[CB:' + key + '] skip reservation ' + id +
                    (completedFor[id] ? ' (completed)' : ' (in flight)'));
                return;
            }

            console.debug('[CB:' + key + '] run for reservation ' + id +
                ' skipExisting=' + skipExisting);
            inFlightFor[id] = true;
            function finish(success) {
                delete inFlightFor[id];
                if (success !== false) { completedFor[id] = true; }
                console.debug('[CB:' + key + '] finished reservation ' + id +
                    ' success=' + (success !== false));
            }

            try {
                var ret = fn(ctx, { skipExisting: skipExisting });
                if (ret && typeof ret.then === 'function') {
                    ret.then(finish).catch(function (e) {
                        delete inFlightFor[id];
                        console.error('[CB:' + key + ']', e);
                    });
                } else {
                    finish(ret);
                }
            } catch (e) {
                delete inFlightFor[id];
                console.error('[CB:' + key + ']', e);
            }
        }

        function onRouteChange() {
            if (!context().reservationId) {
                completedFor = {};
                inFlightFor = {};
                prevId = null;
                return;
            }
            tryRun();
        }

        window.addEventListener('cb:locationchange', onRouteChange);

        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', onRouteChange);
        } else {
            onRouteChange();
        }
    }

    // Resolve `target` against the DOM. `target` is either a CSS selector string
    // (returns the first match) or a function returning an element / falsy.
    function resolve(target) {
        if (typeof target === 'function') { return target() || null; }
        return document.querySelector(target);
    }

    // Wait for document.body when the script runs before the body exists.
    function waitForBody() {
        if (document.body) { return Promise.resolve(document.body); }
        return new Promise(function (resolve) {
            var obs = new MutationObserver(function () {
                if (document.body) {
                    obs.disconnect();
                    resolve(document.body);
                }
            });
            obs.observe(document.documentElement, { childList: true });
        });
    }

    // Wait for an element to appear. Resolves with the element, or null on timeout.
    // Uses a MutationObserver scoped to `within` that disconnects as soon as the
    // target is found (or the timeout fires) - no polling.
    // Default timeout is 60s because reservation data can take 20+ seconds to load
    // on a cold tab (see get_reservation timing in captured HAR files).
    // opts.skipExisting: ignore a match already in the DOM (SPA booking→booking);
    // wait for a mutation that produces a fresh match instead.
    function waitFor(target, opts) {
        opts = opts || {};
        var timeout = opts.timeout == null ? 60000 : opts.timeout;
        var skipExisting = !!opts.skipExisting;

        return waitForBody().then(function (body) {
            var within = opts.within || body || document.documentElement;

            return new Promise(function (result) {
                if (!skipExisting) {
                    var existing = resolve(target);
                    if (existing) { result(existing); return; }
                }

                var done = false;
                var timer = null;
                var observer = null;

                function finish(el) {
                    if (done) { return; }
                    done = true;
                    if (timer) { clearTimeout(timer); }
                    if (observer) { observer.disconnect(); }
                    result(el);
                }

                observer = new MutationObserver(function () {
                    var el = resolve(target);
                    if (el) { finish(el); }
                });
                observer.observe(within, { childList: true, subtree: true, characterData: true });

                if (timeout > 0) {
                    timer = setTimeout(function () { finish(null); }, timeout);
                }
            });
        });
    }

    // Vanilla replacement for jQuery :contains(). Returns matching elements under
    // `root` whose textContent matches `text` (substring by default, exact when
    // opts.exact is true). Scoped to `root`, never the whole document unless asked.
    function byText(root, selector, text, opts) {
        opts = opts || {};
        var scope = root || document;
        var nodes = Array.prototype.slice.call(scope.querySelectorAll(selector));
        return nodes.filter(function (el) {
            var t = (el.textContent || '').trim();
            return opts.exact ? t === text : t.indexOf(text) !== -1;
        });
    }

    // Promise wrapper around GM_xmlhttpRequest for cross-origin calls (honours
    // each script's @connect directives and avoids CORS preflight).
    function gmFetch(opts) {
        return new Promise(function (result, reject) {
            GM_xmlhttpRequest({
                method: opts.method || 'GET',
                url: opts.url,
                headers: opts.headers || {},
                data: opts.data || null,
                responseType: opts.responseType,
                onload: function (r) { result(r); },
                onerror: function (e) { reject(e); },
                ontimeout: function (e) { reject(e); }
            });
        });
    }

    window.CB = {
        HOSTEL_MAP: HOSTEL_MAP,
        detectHostel: detectHostel,
        context: context,
        onReservation: onReservation,
        waitFor: waitFor,
        byText: byText,
        gmFetch: gmFetch
    };
})();
