// ==UserScript==
// @name         Cloudbeds Send Payment Link Email
// @namespace    http://cloudbeds.com/
// @version      0.3
// @description  Adds a "Send payment link" email option to a booking.
// @author       RONBOT
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/cloudbeds-core.js
// @grant        GM_xmlhttpRequest
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
            if (!ctx.hostelPath) { alert('Unable to locate hostel?'); return; }
            CB.gmFetch({
                method: 'POST',
                url: 'https://pay.macbackpackers.com/booking/send_payment_email/' + ctx.hostelPath,
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                data: 'reservation_id=' + encodeURIComponent(ctx.reservationId)
            }).then(function () {
                alert('Email request queued.');
            }).catch(function (r) {
                alert((r && r.status ? r.status + ' ' + r.statusText : 'Request failed'));
            });
        });

        parent.parentNode.insertBefore(li, parent.nextSibling); // insert after the anchor's parent
    }

    CB.onReservation('send-payment-link', function (ctx) {
        return CB.waitFor("a[class*='rs-email-compose']").then(function (anchor) {
            if (!anchor) { return false; }
            addEmailPaymentLinkOption(anchor, ctx);
            return true;
        });
    });
})();
