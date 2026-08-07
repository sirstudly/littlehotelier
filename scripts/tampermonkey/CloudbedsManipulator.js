// ==UserScript==
// @name         Cloudbeds Manipulator
// @namespace    http://cloudbeds.com/
// @version      1.4
// @updateURL    https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsManipulator.js
// @downloadURL  https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsManipulator.js
// @description  Highlight Channel Collect / Airbnb bookings that must not be charged (with EVL levy exception).
// @author       RONBOT
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/cloudbeds-core.js
// @grant        GM_xmlhttpRequest
// @grant        unsafeWindow
// ==/UserScript==

(function () {
    'use strict';

    var CB = window.CB;

    // Stay eligible from this date (last night = day before checkout). Charge levy when checkout is after it.
    var EVL_STAY_FROM = '2026-07-24';

    // Folio/totals may re-render after the header is ready. Keep re-applying the injected
    // message for the current reservation until we navigate away.
    var keepAliveObserver = null;
    var keepAliveReservationId = null;

    function findSourceValue() {
        // The value next to the "Source" label span.
        var labels = CB.byText(document, 'span.small-text', 'Source', { exact: true });
        if (!labels.length) { return null; }
        return labels[0].nextElementSibling;
    }

    function findCheckoutText() {
        var labels = CB.byText(document, 'span.small-text', 'Check-Out', { exact: true });
        if (!labels.length) { return null; }
        var value = labels[0].nextElementSibling;
        return value ? (value.textContent || '').trim() : null;
    }

    // Parse UK dd/MM/YYYY into YYYY-MM-DD, or null if unknown.
    function parseCheckoutToIso(text) {
        if (!text) { return null; }
        var m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(String(text).trim());
        if (!m) { return null; }
        return m[3] + '-' + m[2] + '-' + m[1];
    }

    // True when levy should be charged for alt-collect sources: missing/unparseable
    // checkout defaults to charge; only skip when checkout is known and <= EVL start.
    function shouldChargeGuestLevy(checkoutText) {
        var iso = parseCheckoutToIso(checkoutText);
        if (!iso) { return true; }
        return iso > EVL_STAY_FROM;
    }

    function stopKeepAlive() {
        if (keepAliveObserver) {
            keepAliveObserver.disconnect();
            keepAliveObserver = null;
        }
        keepAliveReservationId = null;
    }

    function clearPreviousHighlight() {
        stopKeepAlive();
        document.querySelectorAll('[data-cb-charge-styled]').forEach(function (el) {
            el.removeAttribute('style');
            el.removeAttribute('data-cb-charge-styled');
        });
        document.querySelectorAll('h1[data-charge-note]').forEach(function (el) {
            el.removeAttribute('data-charge-note');
        });
        document.querySelectorAll('[data-cb-charge-message]').forEach(function (el) {
            el.remove();
        });
    }

    function buildChargeMessage() {
        var sourceType = findSourceValue();
        if (!sourceType) { return null; }

        // Guests may still need to pay for BDC Smart Flex (risk-free) bookings if balance owing.
        var moreInfo = (document.querySelector('span.have_custom') || {}).textContent || '';
        if (moreInfo.indexOf('This is a Smart Flex reservation') !== -1 &&
            moreInfo.indexOf('You can charge 0.00 GBP') !== -1) {
            return null; // Smart Flex with £0 chargeable — no banner
        }

        var sourceText = (sourceType.textContent || '').trim();
        var isAltCollect = (sourceText === 'Agoda' || sourceText === 'Airbnb (API)');
        if (isAltCollect) {
            // Default to CHARGE GUEST LEVY! when checkout is missing or unparseable.
            return shouldChargeGuestLevy(findCheckoutText())
                ? 'CHARGE GUEST LEVY ONLY!'
                : "DON'T CHARGE GUEST!";
        }
        return "CHARGE CARD ON FILE; DON'T CHARGE GUEST!";
    }

    function ensureMessage(message) {
        var totals = document.querySelector("[id='rs-totals-container']");
        if (!totals) { return false; }
        if (totals.querySelector('[data-cb-charge-message]')) { return true; }

        var span = document.createElement('span');
        span.className = 'balance-due-title pull-right bold uppercase';
        span.setAttribute('style', 'color:red; margin-right: 20px');
        span.setAttribute('data-cb-charge-message', '1');
        span.textContent = message;
        totals.appendChild(span);
        return true;
    }

    function applyHighlightStyles(node) {
        var sourceType = findSourceValue();
        if (!sourceType || !node) { return false; }

        sourceType.setAttribute('style', 'color:red; font-weight:bold');
        sourceType.setAttribute('data-cb-charge-styled', '1');
        node.setAttribute('style', 'color:red; font-weight:bold');
        node.setAttribute('data-cb-charge-styled', '1');

        var guestName = document.querySelector('h1.main-guest-name');
        if (guestName) { guestName.setAttribute('data-charge-note', 'enabled'); }
        return true;
    }

    // Paint (or re-paint) highlight + banner. Safe to call repeatedly after folio re-renders.
    function highlightNoCharge(node) {
        var message = buildChargeMessage();
        if (message == null) { return false; }
        if (!applyHighlightStyles(node)) { return false; }
        ensureMessage(message);
        return true;
    }

    // Match a .big-text element whose text mentions a channel-collect / Airbnb booking.
    // Also require Check-Out, Source, and the totals container so we don't treat a
    // half-swapped SPA view as ready (and so the banner has somewhere to land).
    function findReadyHighlightTarget() {
        var hits = CB.byText(document, "[class='big-text']", 'Channel Collect Booking');
        if (!hits.length) { hits = CB.byText(document, "[class='big-text']", 'Airbnb'); }
        if (!hits.length) { return null; }
        if (!findCheckoutText()) { return null; }
        if (!findSourceValue()) { return null; }
        if (!document.querySelector("[id='rs-totals-container']")) { return null; }
        return hits[0];
    }

    // Re-apply when Vue wipes #rs-totals-container children (or restyles Source) after
    // late API responses. Stops on reservation change / clearPreviousHighlight.
    function startKeepAlive(reservationId) {
        stopKeepAlive();
        keepAliveReservationId = reservationId;

        var pending = false;
        keepAliveObserver = new MutationObserver(function () {
            if (pending) { return; }
            if (CB.context().reservationId !== keepAliveReservationId) {
                stopKeepAlive();
                return;
            }
            // Debounce: folio re-renders often arrive as a burst of mutations.
            pending = true;
            setTimeout(function () {
                pending = false;
                if (CB.context().reservationId !== keepAliveReservationId) {
                    stopKeepAlive();
                    return;
                }
                var node = findReadyHighlightTarget();
                if (node) { highlightNoCharge(node); }
            }, 100);
        });
        keepAliveObserver.observe(document.body || document.documentElement, {
            childList: true,
            subtree: true
        });
    }

    CB.onReservation('manipulator', function (ctx, meta) {
        clearPreviousHighlight();
        // settle: on booking→booking the old view still matches while the new one
        // renders; wait for the DOM to go quiet so we style the FINAL elements
        // (anything painted mid-swap gets wiped by the re-render).
        //
        // Note: settle alone cannot cover folio APIs that land later; the
        // keep-alive observer below re-injects the banner after those mutations.
        return CB.waitFor(findReadyHighlightTarget, {
            skipExisting: !!(meta && meta.skipExisting),
            settle: 800
        }).then(function (node) {
            if (!node) { return false; }
            // Guard against a late DOM swap finishing under a different reservation.
            if (CB.context().reservationId !== ctx.reservationId) { return false; }
            var ok = highlightNoCharge(node);
            if (ok) { startKeepAlive(ctx.reservationId); }
            return ok;
        });
    });
})();
