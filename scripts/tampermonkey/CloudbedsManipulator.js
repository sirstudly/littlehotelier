// ==UserScript==
// @name         Cloudbeds Manipulator
// @namespace    http://cloudbeds.com/
// @version      0.9
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

    function clearPreviousHighlight() {
        document.querySelectorAll('h1[data-charge-note]').forEach(function (el) {
            el.removeAttribute('data-charge-note');
        });
        document.querySelectorAll('[data-cb-charge-message]').forEach(function (el) {
            el.remove();
        });
    }

    function highlightNoCharge(node) {
        var sourceType = findSourceValue();
        if (!sourceType) { return false; }

        // Guests may still need to pay for BDC Smart Flex (risk-free) bookings if balance owing.
        var moreInfo = (document.querySelector('span.have_custom') || {}).textContent || '';
        if (moreInfo.indexOf('This is a Smart Flex reservation') === -1 ||
            moreInfo.indexOf('You can charge 0.00 GBP') === -1) {

            sourceType.setAttribute('style', 'color:red; font-weight:bold');
            node.setAttribute('style', 'color:red; font-weight:bold');

            var sourceText = (sourceType.textContent || '').trim();
            var isAltCollect = (sourceText === 'Agoda' || sourceText === 'Airbnb (API)');
            var message;

            if (isAltCollect) {
                // Default to CHARGE GUEST LEVY! when checkout is missing or unparseable.
                message = shouldChargeGuestLevy(findCheckoutText())
                    ? 'CHARGE GUEST LEVY ONLY!'
                    : "DON'T CHARGE GUEST!";
            } else {
                message = "CHARGE CARD ON FILE; DON'T CHARGE GUEST!";
            }

            var totals = document.querySelector("[id='rs-totals-container']");
            if (totals) {
                var span = document.createElement('span');
                span.className = 'balance-due-title pull-right bold uppercase';
                span.setAttribute('style', 'color:red; margin-right: 20px');
                span.setAttribute('data-cb-charge-message', '1');
                span.textContent = message;
                totals.appendChild(span);
            }
        }

        var guestName = document.querySelector('h1.main-guest-name');
        if (guestName) { guestName.setAttribute('data-charge-note', 'enabled'); }
        return true;
    }

    // Match a .big-text element whose text mentions a channel-collect / Airbnb booking.
    // Also require Check-Out so we don't treat a half-swapped SPA view as ready.
    function findReadyHighlightTarget() {
        var hits = CB.byText(document, "[class='big-text']", 'Channel Collect Booking');
        if (!hits.length) { hits = CB.byText(document, "[class='big-text']", 'Airbnb'); }
        if (!hits.length) { return null; }
        if (!findCheckoutText()) { return null; }
        if (!findSourceValue()) { return null; }
        return hits[0];
    }

    CB.onReservation('manipulator', function (ctx, meta) {
        clearPreviousHighlight();
        return CB.waitFor(findReadyHighlightTarget, {
            skipExisting: !!(meta && meta.skipExisting)
        }).then(function (node) {
            if (!node) { return false; }
            // Guard against a late DOM swap finishing under a different reservation.
            if (CB.context().reservationId !== ctx.reservationId) { return false; }
            return highlightNoCharge(node);
        });
    });
})();
