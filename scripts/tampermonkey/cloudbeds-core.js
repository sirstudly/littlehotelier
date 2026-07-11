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

(function () {
    'use strict';

    if (window.CB) { return; } // already initialised in this sandbox

    // Cloudbeds connect is a single-page app: opening a booking changes the URL to
    // /connect/{propertyId}#/reservations/{id} via history.pushState, which fires
    // NEITHER hashchange NOR popstate. Patch History.prototype (not just the
    // sandbox's history object) so the page app's pushState/replaceState calls
    // also emit our unified 'cb:locationchange' event.
    function installLocationChange() {
        if (window.__cbLocationChangePatched) { return; }
        window.__cbLocationChangePatched = true;

        function emit() { window.dispatchEvent(new Event('cb:locationchange')); }

        ['pushState', 'replaceState'].forEach(function (method) {
            var original = History.prototype[method];
            if (typeof original !== 'function') { return; }
            History.prototype[method] = function () {
                var result = original.apply(this, arguments);
                emit();
                return result;
            };
        });

        window.addEventListener('popstate', emit);
        window.addEventListener('hashchange', emit);
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

    // Parse the current route. reservationId is null when not on a reservation view.
    function context() {
        var propMatch = /\/connect\/(\d+)/.exec(location.pathname);
        var resMatch = /#\/reservations\/(\d+)/.exec(location.hash);
        var hostel = detectHostel();
        return {
            host: location.host,
            propertyId: propMatch ? propMatch[1] : null,
            reservationId: resMatch ? resMatch[1] : null,
            hostel: hostel,               // { title, path } or null
            hostelPath: hostel ? hostel.path : null
        };
    }

    // Register a handler that runs once each time a reservation view is entered.
    // fn(ctx) may return a Promise; resolve with false to allow retry on the next
    // route change (e.g. waitFor timed out). Leaving the reservation view clears
    // completion state so dashboard -> back to the same booking re-runs handlers.
    function onReservation(key, fn) {
        var completedFor = {};
        var inFlightFor = {};

        function tryRun() {
            var ctx = context();
            if (!ctx.reservationId) { return; }

            var id = ctx.reservationId;
            if (completedFor[id] || inFlightFor[id]) { return; }

            inFlightFor[id] = true;
            function finish(success) {
                delete inFlightFor[id];
                if (success !== false) { completedFor[id] = true; }
            }

            try {
                var ret = fn(ctx);
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
            }
            tryRun();
        }

        // 'cb:locationchange' covers pushState/replaceState/popstate/hashchange
        // (see installLocationChange above), so this fires on every SPA navigation.
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
    function waitFor(target, opts) {
        opts = opts || {};
        var timeout = opts.timeout == null ? 60000 : opts.timeout;

        return waitForBody().then(function (body) {
            var within = opts.within || body || document.documentElement;

            return new Promise(function (result) {
                var existing = resolve(target);
                if (existing) { result(existing); return; }

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
                observer.observe(within, { childList: true, subtree: true });

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
