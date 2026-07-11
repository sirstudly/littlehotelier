// ==UserScript==
// @name         Cloudbeds Manipulator: Highlight blacklist (Royal Mile)
// @namespace    http://cloudbeds.com/
// @version      0.8
// @description  Highlight the guest name on a booking if it appears in the shared hostel blacklist.
// @author       RONBOT
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/cloudbeds-core.js
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/blacklist-common.js
// @grant        GM_xmlhttpRequest
// ==/UserScript==

(function () {
    'use strict';
    window.CBBlacklist.init('royalmile', 'DEFINE_YOUR_TOKEN_HERE');
})();
