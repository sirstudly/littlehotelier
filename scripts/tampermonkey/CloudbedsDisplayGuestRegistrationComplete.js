// ==UserScript==
// @name         Cloudbeds Display Guest Registration Complete
// @namespace    http://cloudbeds.com/
// @version      0.3
// @updateURL    https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsDisplayGuestRegistrationComplete.js
// @downloadURL  https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsDisplayGuestRegistrationComplete.js
// @description  Show guest identity-document registration status next to the reservation guest name.
// @author       RONBOT
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/cloudbeds-core.js
// @grant        unsafeWindow
// @run-at       document-start
// ==/UserScript==

(function () {
    'use strict';

    var CB = window.CB;
    var pageWindow = (typeof unsafeWindow !== 'undefined') ? unsafeWindow : window;

    var MSG_ATTR = 'data-cb-guest-reg-status';
    var MSG_STYLE = 'margin-left: 12px; font-weight: bold; color: #6d4aff;';
    var DATA_EVENT = 'cb:guest-reg-data';

    // reservationId -> status message string
    var cache = {};

    var keepAliveObserver = null;
    var keepAliveReservationId = null;
    var pendingTitleWaitId = null;

    function isBlank(value) {
        return value == null || String(value).trim() === '';
    }

    // UK / Ireland: document type + issuing country required; number optional.
    function isDocumentNumberOptional(guest) {
        var code = String(guest.document_issuing_country || '').trim().toUpperCase();
        if (code === 'GB' || code === 'IE') { return true; }
        var name = String(guest.document_issuing_country_name || '').trim().toLowerCase();
        return name === 'ireland' ||
            name === 'united kingdom' ||
            name === 'united kingdom of great britain and northern ireland';
    }

    function isDocumentComplete(guest) {
        var type = guest.document_type;
        if (isBlank(type) || type === 'na' || type === '-') { return false; }
        var country = guest.document_issuing_country;
        if (isBlank(country) || country === 'na') { return false; }
        if (!isDocumentNumberOptional(guest) && isBlank(guest.document_number)) { return false; }
        return true;
    }

    function guestDisplayName(guest) {
        var name = (String(guest.first_name || '').trim() + ' ' +
            String(guest.last_name || '').trim()).trim();
        return name || 'Unknown';
    }

    function namedGuests(reservation) {
        var list = reservation.additional_guests || [];
        return list.filter(function (guest) {
            return String(guest.deleted) !== '1';
        });
    }

    // Occupancy gap first; then named guests missing docs; else complete.
    function buildStatusMessage(reservation) {
        var expected = (Number(reservation.adults_number) || 0) +
            (Number(reservation.kids_number) || 0);
        var guests = namedGuests(reservation);

        if (expected > guests.length) {
            var missing = expected - guests.length;
            return 'Guest Registration incomplete - missing details for ' +
                missing + ' guests';
        }

        var incomplete = guests.filter(function (guest) {
            return !isDocumentComplete(guest);
        });
        if (incomplete.length) {
            return 'Guest Registration incomplete - missing details for ' +
                incomplete.map(guestDisplayName).join(', ');
        }

        return 'Guest Registration Complete';
    }

    function unwrapReservation(payload) {
        if (!payload || typeof payload !== 'object') { return null; }
        if (payload.additional_guests || payload.reservation_id) { return payload; }
        if (payload.data && typeof payload.data === 'object') {
            return unwrapReservation(payload.data);
        }
        if (payload.reservation && typeof payload.reservation === 'object') {
            return unwrapReservation(payload.reservation);
        }
        return null;
    }

    function handleReservationPayload(payload) {
        var reservation = unwrapReservation(payload);
        if (!reservation || reservation.reservation_id == null) { return; }

        var reservationId = String(reservation.reservation_id);
        cache[reservationId] = buildStatusMessage(reservation);
        window.dispatchEvent(new CustomEvent(DATA_EVENT, {
            detail: { reservationId: reservationId }
        }));
        // XHR often arrives before .page-title h3; inject when ready and keep alive
        // even if onReservation already finished (or raced ahead).
        scheduleInject(reservationId);
    }

    function installXhrHook() {
        if (pageWindow.__cbGuestRegXhrHooked) { return; }
        pageWindow.__cbGuestRegXhrHooked = true;

        var XHR = pageWindow.XMLHttpRequest;
        if (!XHR || !XHR.prototype) { return; }

        var originalOpen = XHR.prototype.open;
        var originalSend = XHR.prototype.send;

        XHR.prototype.open = function (method, url) {
            try {
                this.__cbGuestRegUrl = url == null ? '' : String(url);
            } catch (e) {
                this.__cbGuestRegUrl = '';
            }
            return originalOpen.apply(this, arguments);
        };

        XHR.prototype.send = function () {
            var xhr = this;
            var url = xhr.__cbGuestRegUrl || '';
            if (/\/connect\/reservations\/get_reservation(?:\?|$)/.test(url) ||
                    /\/reservations\/get_reservation(?:\?|$)/.test(url)) {
                xhr.addEventListener('load', function () {
                    if (xhr.status < 200 || xhr.status >= 300) { return; }
                    try {
                        handleReservationPayload(JSON.parse(xhr.responseText));
                    } catch (e) {
                        console.error('[CB:guest-reg-complete] failed to parse get_reservation', e);
                    }
                });
            }
            return originalSend.apply(this, arguments);
        };
    }

    function clearMessage() {
        document.querySelectorAll('[' + MSG_ATTR + ']').forEach(function (el) {
            el.remove();
        });
    }

    function ensureMessage(h3, text) {
        if (!h3 || !h3.parentNode) { return false; }

        var existing = h3.parentNode.querySelector('[' + MSG_ATTR + ']');
        if (existing) {
            if (existing.textContent !== text) {
                existing.textContent = text;
            }
            return true;
        }

        var span = document.createElement('span');
        span.setAttribute(MSG_ATTR, '1');
        span.setAttribute('style', MSG_STYLE);
        span.textContent = text;
        if (h3.nextSibling) {
            h3.parentNode.insertBefore(span, h3.nextSibling);
        } else {
            h3.parentNode.appendChild(span);
        }
        return true;
    }

    function tryInject(reservationId) {
        if (!reservationId) { return false; }
        if (String(CB.context().reservationId) !== String(reservationId)) { return false; }
        var message = cache[reservationId];
        if (!message) { return false; }
        var h3 = document.querySelector('.page-title h3');
        if (!h3) { return false; }
        return ensureMessage(h3, message);
    }

    // Inject now if the title exists; otherwise wait for it (XHR/title race).
    function scheduleInject(reservationId) {
        if (!reservationId) { return; }
        if (String(CB.context().reservationId) !== String(reservationId)) { return; }

        if (tryInject(reservationId)) {
            if (String(keepAliveReservationId) !== String(reservationId)) {
                startKeepAlive(reservationId);
            }
            return;
        }

        if (pendingTitleWaitId === reservationId) { return; }
        pendingTitleWaitId = reservationId;
        CB.waitFor('.page-title h3', { timeout: 30000, settle: 200 }).then(function (h3) {
            if (pendingTitleWaitId === reservationId) {
                pendingTitleWaitId = null;
            }
            if (!h3) { return; }
            if (String(CB.context().reservationId) !== String(reservationId)) { return; }
            if (tryInject(reservationId)) {
                startKeepAlive(reservationId);
            }
        });
    }

    function waitForCache(reservationId, timeoutMs) {
        return new Promise(function (resolve) {
            if (cache[reservationId]) {
                resolve(cache[reservationId]);
                return;
            }

            var done = false;
            var timer = null;

            function cleanup() {
                if (done) { return; }
                done = true;
                window.removeEventListener(DATA_EVENT, onData);
                if (timer) { clearTimeout(timer); }
            }

            function onData(event) {
                if (!event.detail || String(event.detail.reservationId) !== String(reservationId)) {
                    return;
                }
                if (!cache[reservationId]) { return; }
                cleanup();
                resolve(cache[reservationId]);
            }

            window.addEventListener(DATA_EVENT, onData);
            timer = setTimeout(function () {
                cleanup();
                resolve(cache[reservationId] || null);
            }, timeoutMs == null ? 60000 : timeoutMs);
        });
    }

    function stopKeepAliveObserver() {
        if (keepAliveObserver) {
            keepAliveObserver.disconnect();
            keepAliveObserver = null;
        }
        keepAliveReservationId = null;
    }

    function stopKeepAlive() {
        stopKeepAliveObserver();
        clearMessage();
    }

    // Vue may rewrite .page-title; re-inject after those mutations.
    function startKeepAlive(reservationId) {
        stopKeepAliveObserver();
        keepAliveReservationId = reservationId;

        var pending = false;
        keepAliveObserver = new MutationObserver(function () {
            if (pending) { return; }
            if (String(CB.context().reservationId) !== String(keepAliveReservationId)) {
                stopKeepAlive();
                return;
            }
            pending = true;
            setTimeout(function () {
                pending = false;
                if (String(CB.context().reservationId) !== String(keepAliveReservationId)) {
                    stopKeepAlive();
                    return;
                }
                tryInject(keepAliveReservationId);
            }, 100);
        });
        keepAliveObserver.observe(document.body || document.documentElement, {
            childList: true,
            subtree: true
        });
    }

    installXhrHook();

    CB.onReservation('guest-reg-complete', function (ctx, meta) {
        stopKeepAlive();
        pendingTitleWaitId = null;

        var reservationId = String(ctx.reservationId);

        return CB.waitFor('.page-title h3', {
            skipExisting: !!(meta && meta.skipExisting),
            settle: 500
        }).then(function (h3) {
            if (!h3) { return false; }
            if (String(CB.context().reservationId) !== reservationId) { return false; }

            return waitForCache(reservationId).then(function (message) {
                if (!message) { return false; }
                if (String(CB.context().reservationId) !== reservationId) { return false; }

                var title = document.querySelector('.page-title h3');
                if (!title) { return false; }
                ensureMessage(title, message);
                startKeepAlive(reservationId);
                return true;
            });
        });
    });
})();
