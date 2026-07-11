// ==UserScript==
// @name         Cloudbeds Manipulator
// @namespace    http://cloudbeds.com/
// @version      0.7
// @description  Highlight Channel Collect / Airbnb bookings that must not be charged.
// @author       RONBOT
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/cloudbeds-core.js
// @grant        GM_xmlhttpRequest
// ==/UserScript==

(function () {
    'use strict';

    var CB = window.CB;

    function findSourceValue() {
        // The value next to the "Source" label span.
        var labels = CB.byText(document, 'span.small-text', 'Source', { exact: true });
        if (!labels.length) { return null; }
        return labels[0].nextElementSibling;
    }

    function highlightNoCharge(node) {
        var sourceType = findSourceValue();
        if (!sourceType) { return; }
        if (document.querySelector("h1[data-charge-note='enabled']")) { return; } // already flagged

        // Guests may still need to pay for BDC Smart Flex (risk-free) bookings if balance owing.
        var moreInfo = (document.querySelector('span.have_custom') || {}).textContent || '';
        if (moreInfo.indexOf('This is a Smart Flex reservation') === -1 ||
            moreInfo.indexOf('You can charge 0.00 GBP') === -1) {

            sourceType.setAttribute('style', 'color:red; font-weight:bold');
            node.setAttribute('style', 'color:red; font-weight:bold');

            var message = "DON'T CHARGE GUEST!";
            var sourceText = (sourceType.textContent || '').trim();
            if (sourceText !== 'Agoda' && sourceText !== 'Airbnb (API)') { // alternate collection methods
                message = 'CHARGE CARD ON FILE; ' + message;
            }

            var totals = document.querySelector("[id='rs-totals-container']");
            if (totals) {
                var span = document.createElement('span');
                span.className = 'balance-due-title pull-right bold uppercase';
                span.setAttribute('style', 'color:red; margin-right: 20px');
                span.textContent = message;
                totals.appendChild(span);
            }
        }

        var guestName = document.querySelector('h1.main-guest-name');
        if (guestName) { guestName.setAttribute('data-charge-note', 'enabled'); } // flag booking so we only do this once
    }

    // Match a .big-text element whose text mentions a channel-collect / Airbnb booking.
    function findBigText() {
        var hits = CB.byText(document, "[class='big-text']", 'Channel Collect Booking');
        if (!hits.length) { hits = CB.byText(document, "[class='big-text']", 'Airbnb'); }
        return hits.length ? hits[0] : null;
    }

    CB.onReservation('manipulator', function () {
        return CB.waitFor(findBigText).then(function (node) {
            if (!node) { return false; }
            highlightNoCharge(node);
            return true;
        });
    });
})();
