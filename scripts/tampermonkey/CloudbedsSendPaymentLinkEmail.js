// ==UserScript==
// @name         Cloudbeds Send Payment Link Email
// @namespace    http://cloudbeds.com/
// @version      0.5
// @updateURL    https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsSendPaymentLinkEmail.js
// @downloadURL  https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsSendPaymentLinkEmail.js
// @description  Adds a "Send payment link" email option to a booking.
// @author       RONBOT
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/cloudbeds-core.js
// @grant        GM_xmlhttpRequest
// @grant        unsafeWindow
// @connect      pay.macbackpackers.com
// ==/UserScript==

(function () {
    'use strict';

    var CB = window.CB;

    function addEmailPaymentLinkOption(anchor, ctx) {
        var parent = anchor.parentNode;
        if (!parent || parent.parentNode.querySelector('.cb-email-payment-link')) { return; } // already added

        var li = document.createElement('li');
        li.className = 'cb-email-payment-link';
        var link = document.createElement('a');
        link.href = 'javascript:void(0);';
        link.textContent = 'Email Payment Link';
        li.appendChild(link);

        link.addEventListener('click', function () {
            // Re-resolve on click: early onReservation ctx can miss hostelPath when
            // document.title was still generic (common on Firefox cold loads).
            var live = CB.context();
            var hostelPath = live.hostelPath || ctx.hostelPath;
            var reservationId = live.reservationId || ctx.reservationId;
            if (!hostelPath) { alert('Unable to locate hostel?'); return; }
            CB.gmFetch({
                method: 'POST',
                url: 'https://pay.macbackpackers.com/booking/send_payment_email/' + hostelPath,
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                data: 'reservation_id=' + encodeURIComponent(reservationId)
            }).then(function () {
                alert('Email request queued.');
            }).catch(function (r) {
                alert((r && r.status ? r.status + ' ' + r.statusText : 'Request failed'));
            });
        });

        parent.parentNode.insertBefore(li, parent.nextSibling); // insert after the anchor's parent
    }

    CB.onReservation('send-payment-link', function (ctx, meta) {
        return CB.waitFor("a[class*='rs-email-compose']", {
            skipExisting: !!(meta && meta.skipExisting)
        }).then(function (anchor) {
            if (!anchor) { return false; }
            var live = CB.context();
            addEmailPaymentLinkOption(anchor, live.hostelPath ? live : ctx);
            return true;
        });
    });
})();
