// Shared blacklist-check logic for the per-property Cloudbeds blacklist userscripts.
//
// @require'd (after cloudbeds-core.js) by CloudbedsHighlightBlacklistHSH.js and
// CloudbedsHighlightBlacklistRMB.js. Each of those supplies its property path and
// API token, then calls window.CBBlacklist.init(propertyPath, token).
(function () {
    'use strict';

    if (window.CBBlacklist) { return; }

    var CB = window.CB;
    var BASE = 'https://backoffice.scotlandstophostels.com/';

    function compareLastName(lastNames1, lastNames2) {
        for (var i = 0; i < lastNames1.length; i++) {
            var name = lastNames1[i];
            // Compare every word in the name longer than 3 letters (skips 'de', 'von', etc.).
            if (name.length > 3 && lastNames2.indexOf(name) !== -1) {
                return true;
            }
        }
        return false;
    }

    function highlight(h3, fullName, propertyPath) {
        h3.innerHTML = fullName + ' !! GUEST NAME IN THE BLACKLIST PLEASE CHECK ' +
            '<a href="' + BASE + propertyPath + '/admin/blacklist/" target="_blank" ' +
            'style="color: red; text-decoration: underline;">[Check Here]</a>';
        h3.style.color = 'red';
    }

    function check(h3, propertyPath, token) {
        fetch(BASE + propertyPath + '/wp-json/hbo-reports/v1/blacklist', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token
            }
        }).then(function (response) {
            return response.json();
        }).then(function (blacklist) {
            var fullName = h3.textContent || '';
            var nameList = fullName.toLowerCase().split(' ');
            var firstName = nameList[0];
            var lastNamesList = nameList.slice(1);

            for (var i = 0; i < blacklist.length; i++) {
                var profile = blacklist[i];
                var profileFirstName = profile.first_name.toLowerCase();
                var profileLastName = profile.last_name.toLowerCase();
                var areLastNamesSame = compareLastName(lastNamesList, profileLastName.split(' '));

                if (firstName === profileFirstName && areLastNamesSame === true) {
                    highlight(h3, fullName, propertyPath);
                }
            }
        }).catch(function (error) {
            console.error('[CB:blacklist]', error);
        });
    }

    function init(propertyPath, token) {
        if (!token) {
            console.error('[CB:blacklist] No API token configured for ' + propertyPath);
            return;
        }
        CB.onReservation('blacklist', function (ctx, meta) {
            return CB.waitFor('.page-title h3', {
                skipExisting: !!(meta && meta.skipExisting)
            }).then(function (h3) {
                if (!h3) { return false; }
                check(h3, propertyPath, token);
                return true;
            });
        });
    }

    window.CBBlacklist = { init: init };
})();
