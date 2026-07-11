// ==UserScript==
// @name         Cloudbeds Manipulator: enable paid bed checkboxes
// @namespace    http://cloudbeds.com/
// @version      0.8
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

    // NOTE: data-room-type ids are property-specific.
    var SELECTOR = "[type='checkbox'][data-room-type='120456'], [type='checkbox'][data-room-type='645200']";

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
