// ==UserScript==
// @name         Cloudbeds Manipulator: Highlight blacklist (Castle Rock)
// @namespace    http://cloudbeds.com/
// @version      0.9
// @updateURL    https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsHighlightBlacklistCRH.js
// @downloadURL  https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsHighlightBlacklistCRH.js
// @description  Highlight the guest name on a booking if it appears in the shared hostel blacklist.
// @author       RONBOT
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/cloudbeds-core.js
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/blacklist-common.js
// @grant        GM_xmlhttpRequest
// @grant        unsafeWindow
// ==/UserScript==

(function () {
    'use strict';
    window.CBBlacklist.init('castlerock', 'DEFINE_YOUR_TOKEN_HERE');
})();
