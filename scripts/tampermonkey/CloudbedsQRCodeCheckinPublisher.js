// ==UserScript==
// @name         Cloudbeds QR Code Checkin Publisher
// @namespace    http://cloudbeds.com/
// @version      0.2
// @description  Push QR code updates onto backoffice site.
// @author       RONBOT
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://dropbox.macbackpackers.com/cloudbeds-core.js
// @grant        GM_xmlhttpRequest
// @connect      wss.backoffice.macbackpackers.com
// ==/UserScript==

(function () {
    'use strict';

    var CB = window.CB;

    function publishQRCode(statusSelect, ctx) {
        if (statusSelect.parentNode.querySelector('.cb-publish-qr')) { return; } // already added

        // Only offer publishing for confirmed / confirmation-pending bookings.
        var confirmable = CB.byText(statusSelect, 'button', 'Confirmed').length ||
            CB.byText(statusSelect, 'button', 'Confirmation Pending').length;
        if (!confirmable) { return; }

        var titleEl = document.querySelector("div[class='page-title'] h4");
        var bookingRef = titleEl ? (titleEl.textContent || '').trim() : '';

        var link = document.createElement('a');
        link.href = 'javascript:void(0);';
        link.className = 'btn btn-primary blue mr-5 cb-publish-qr';
        link.textContent = 'Publish QR Code';

        link.addEventListener('click', function () {
            if (!ctx.hostelPath) { alert('Unable to locate hostel?'); return; }
            if (!bookingRef) { alert('Unable to locate booking reference?'); return; }
            CB.gmFetch({
                method: 'GET',
                url: 'https://wss.backoffice.macbackpackers.com/' + ctx.hostelPath +
                    '?booking_ref=' + encodeURIComponent(bookingRef)
            }).then(function (r) {
                alert(r.responseText);
            }).catch(function (r) {
                alert((r && r.status ? r.status + ' ' + r.statusText : 'Request failed'));
            });
        });

        statusSelect.parentNode.insertBefore(link, statusSelect.parentNode.firstChild); // prepend
    }

    CB.onReservation('qr-code-publisher', function (ctx) {
        return CB.waitFor("div[class*='res-status-select']").then(function (statusSelect) {
            if (!statusSelect) { return false; }
            publishQRCode(statusSelect, ctx);
            return true;
        });
    });
})();
