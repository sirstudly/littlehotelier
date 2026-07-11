// ==UserScript==
// @name         Cloudbeds Manipulator: Highlight blacklist (Royal Mile)
// @namespace    http://cloudbeds.com/
// @version      0.7
// @description  Highlight the guest name on a booking if it appears in the shared hostel blacklist.
// @author       RONBOT
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://dropbox.macbackpackers.com/cloudbeds-core.js
// @require      https://dropbox.macbackpackers.com/blacklist-common.js
// @grant        GM_xmlhttpRequest
// ==/UserScript==

(function () {
    'use strict';
    window.CBBlacklist.init('royalmile', 'DEFINE_YOUR_TOKEN_HERE');
})();
