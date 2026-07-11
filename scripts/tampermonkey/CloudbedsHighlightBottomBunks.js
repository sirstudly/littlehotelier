// ==UserScript==
// @name         Cloudbeds Manipulator: highlight bottom bunks
// @namespace    http://cloudbeds.com/
// @version      0.6
// @description  Draws a subtle separator to separate bunks in the Cloudbeds calendar view.
// @author       Donovan
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/cloudbeds-core.js
// @grant        GM_xmlhttpRequest
// ==/UserScript==

(function () {
    'use strict';

    var ROOM_LINE_SELECTOR = "[class='c-room-line '], .c-room-line";
    var BOTTOM_BUNK_STYLE = 'border-bottom: 2px solid; border-bottom-color: var(--nb-colors-gray-500)';

    function getLastDigits(string) {
        var parts = string.split('-');
        return parts.length > 1 ? parts[1] : null;
    }

    function isOdd(x) {
        return x % 2 === 1;
    }

    function highlightBottomBunk(el) {
        if (!el || el.getAttribute('data-cb-bottom-bunk') === '1') { return; }

        var roomId = el.getAttribute('data-room-id');
        if (!roomId) { return; }

        var lastDigits = getLastDigits(roomId);
        if (lastDigits == null) { return; }

        if (isOdd(parseInt(lastDigits, 10))) {
            el.setAttribute('style', BOTTOM_BUNK_STYLE);
            el.setAttribute('data-cb-bottom-bunk', '1');
        }
    }

    function processRoomLines(root) {
        var scope = root && root.querySelectorAll ? root : document;
        var lines = scope.querySelectorAll
            ? scope.querySelectorAll(ROOM_LINE_SELECTOR)
            : [];
        Array.prototype.forEach.call(lines, highlightBottomBunk);
    }

    function watchRoomLines() {
        processRoomLines(document);

        var observer = new MutationObserver(function (mutations) {
            mutations.forEach(function (mutation) {
                Array.prototype.forEach.call(mutation.addedNodes, function (node) {
                    if (node.nodeType !== 1) { return; }
                    if (node.matches && node.matches(ROOM_LINE_SELECTOR)) {
                        highlightBottomBunk(node);
                    }
                    processRoomLines(node);
                });
            });
        });

        var within = document.body || document.documentElement;
        observer.observe(within, { childList: true, subtree: true });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', watchRoomLines);
    } else {
        watchRoomLines();
    }
})();
