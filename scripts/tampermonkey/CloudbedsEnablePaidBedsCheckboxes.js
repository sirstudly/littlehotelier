// ==UserScript==
// @name         Cloudbeds Manipulator: enable paid bed checkboxes
// @namespace    http://cloudbeds.com/
// @version      0.7
// @description  Re-enable the disabled paid-bed room-type checkboxes on a booking.
// @author       Donovan
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/cloudbeds-core.js
// @grant        GM_xmlhttpRequest
// ==/UserScript==

(function () {
    'use strict';

    var CB = window.CB;

    // NOTE: data-room-type '120456' is property-specific; confirm before rolling out
    // to other properties (may need to be configurable per install).
    var SELECTOR = "[type='checkbox'][data-room-type='120456']";

    CB.onReservation('enable-paid-beds', function () {
        return CB.waitFor(SELECTOR).then(function (checkbox) {
            if (!checkbox) { return false; }
            // Enable every matching checkbox, not just the first that appeared.
            document.querySelectorAll(SELECTOR).forEach(function (el) {
                el.removeAttribute('disabled');
            });
            return true;
        });
    });
})();
