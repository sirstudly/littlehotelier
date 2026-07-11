// ==UserScript==
// @name         Cloudbeds Manipulator: highlight bottom bunks
// @namespace    http://cloudbeds.com/
// @version      0.51
// @description  try to take over the world! @grant must be set to keep sandboxed environment
// @author       Donovan
// @match      https://hotels.cloudbeds.com/connect/*
// @match     https://macbackpackers.cloudbeds.com/connect/*
// @require      http://ajax.googleapis.com/ajax/libs/jquery/2.1.0/jquery.min.js
// @require      https://gist.github.com/raw/2625891/waitForKeyElements.js
// @grant        GM_xmlhttpRequest
// ==/UserScript==

function getLastDigits (string) {
     return string.split("-")[1];
};

function isOdd (x) {
    if (x%2==1) {
        return true;
    };
};


(function() {
    'use strict';

    waitForKeyElements ("[class='c-room-line ']", reeeeeeee );

    function reeeeeeee (jNode) {
        if (isOdd(getLastDigits(jNode.attr("data-room-id")))) {
            jNode.attr( 'style', 'border-bottom: 2px solid; border-bottom-color: var(--nb-colors-gray-500)');
        };
    }

})();