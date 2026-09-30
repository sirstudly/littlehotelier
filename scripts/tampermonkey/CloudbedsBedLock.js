// ==UserScript==
// @name         Cloudbeds Manipulator: bed lock
// @namespace    http://cloudbeds.com/
// @version      0.2
// @updateURL    https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsBedLock.js
// @downloadURL  https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsBedLock.js
// @description  Lock a booking to its bed from the calendar pop-up; locks live on the backoffice and moves are flagged.
// @author       RONBOT
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/cloudbeds-core.js
// @grant        GM_xmlhttpRequest
// @grant        GM_getValue
// @grant        GM_setValue
// @grant        GM_deleteValue
// @grant        unsafeWindow
// @connect      backoffice.scotlandstophostels.com
// ==/UserScript==

(function () {
    'use strict';

    var CB = window.CB;
    var pageWindow = (typeof unsafeWindow !== 'undefined') ? unsafeWindow : window;

    var BASE = 'https://backoffice.scotlandstophostels.com/';
    var BACKOFFICE_PATHS = { CRH: 'castlerock', HSH: 'highstreethostel', RMB: 'royalmile', LSH: 'lochside' };
    var LOCKABLE_STATUSES = ['confirmed', 'not_confirmed', 'checked_in'];
    var REFRESH_MS = 60000;
    // Swallow mousedown on locked tiles so they can't be dragged (only for users running this script).
    var BLOCK_DRAG = true;

    var locks = [];            // active locks from the backoffice
    var lockByKey = {};        // "reservationId|roomId" -> lock (the bed it's locked to)
    var violationByKey = {};   // "reservationId|toRoomId" -> lock (where a locked booking was moved to)
    var lastRefresh = 0;
    var refreshing = null;

    function key(reservationId, roomId) {
        return reservationId + '|' + (roomId || '');
    }

    function backofficePath() {
        var ctx = CB.context();
        return ctx.hostelPath ? BACKOFFICE_PATHS[ctx.hostelPath] : null;
    }

    function apiToken(path, promptIfMissing) {
        var token = GM_getValue('bedLockToken:' + path, '');
        if (!token && promptIfMissing) {
            token = (prompt('Bed lock: enter the backoffice API key for ' + path +
                ' (backoffice admin/report-settings page, "Internal API Key")') || '').trim();
            if (token) { GM_setValue('bedLockToken:' + path, token); }
        }
        return token;
    }

    function api(method, endpoint, body, promptIfMissing) {
        var path = backofficePath();
        if (!path) { return Promise.reject(new Error('Unable to work out which hostel this is')); }
        var token = apiToken(path, promptIfMissing);
        if (!token) { return Promise.reject(new Error('No backoffice API key for ' + path)); }
        return CB.gmFetch({
            method: method,
            url: BASE + path + '/wp-json/hbo-reports/v1/' + endpoint,
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
            data: body ? JSON.stringify(body) : null
        }).then(function (r) {
            if (r.status === 401 || r.status === 403) {
                GM_deleteValue('bedLockToken:' + path);
                throw new Error('Backoffice rejected the API key for ' + path + '; you will be asked again.');
            }
            var json = null;
            try { json = JSON.parse(r.responseText); } catch (e) { /* non-JSON error page */ }
            if (r.status >= 400) {
                throw new Error((json && (json.error || json.message)) || (r.status + ' ' + r.statusText));
            }
            return json;
        });
    }

    function setLocks(rows) {
        locks = rows || [];
        lockByKey = {};
        violationByKey = {};
        locks.forEach(function (l) {
            lockByKey[key(l.reservation_id, l.room_id)] = l;
            if (l.violation_id) {
                violationByKey[key(l.reservation_id, l.to_room_id)] = l;
            }
        });
    }

    function refreshLocks(promptIfMissing) {
        if (refreshing) { return refreshing; }
        refreshing = api('GET', 'bed-locks', null, promptIfMissing).then(function (rows) {
            lastRefresh = Date.now();
            setLocks(rows);
            decorateTiles();
        }).catch(function (e) {
            lastRefresh = Date.now(); // don't hammer the backoffice on repeated failures
            console.warn('[CB:bed-lock] refresh failed: ' + e.message);
        }).then(function () {
            refreshing = null;
        });
        return refreshing;
    }

    function describe(room, bedName, roomId) {
        if (!roomId) { return 'Unassigned'; }
        if (!room) { return roomId; }
        return bedName ? room + ' - ' + bedName : room;
    }

    function currentUserName() {
        try {
            var data = pageWindow.BET_HBS_TMP && pageWindow.BET_HBS_TMP.data;
            return (data && data.user_name) || null;
        } catch (e) {
            return null;
        }
    }

    // ---- Calendar tiles ------------------------------------------------------------------

    function tileState(tile) {
        var resId = tile.getAttribute('data-reservation-id');
        var roomId = tile.getAttribute('data-room-id');
        if (!resId || resId === '0' || !roomId) { return { state: '' }; }
        var moved = violationByKey[key(resId, roomId)];
        if (moved) {
            return {
                state: 'violation',
                title: 'Moved off locked bed ' + describe(moved.room, moved.bed_name, moved.room_id) +
                    (moved.locked_by ? ' (locked by ' + moved.locked_by + ')' : '')
            };
        }
        var lock = lockByKey[key(resId, roomId)];
        if (lock && !lock.violation_id) {
            return {
                state: 'locked',
                title: 'Locked to this bed' + (lock.locked_by ? ' by ' + lock.locked_by : '')
            };
        }
        return { state: '' };
    }

    // Calendar tiles are skewed parallelograms; undo the tile's skew so the icon stands upright.
    function uprightTransform(tile) {
        var m = /matrix\(([^)]+)\)/.exec(getComputedStyle(tile).transform || '');
        if (!m) { return 'translateY(-50%)'; }
        var v = m[1].split(',').map(parseFloat);
        var skew = Math.atan2(v[2], v[3]);
        return 'translateY(-50%)' + (skew ? ' skewX(' + (-skew) + 'rad)' : '');
    }

    function decorateTile(tile) {
        var s = tileState(tile);
        var icon = tile.querySelector('.cb-bed-lock-icon');
        // Cloudbeds may re-render a tile's contents in place, dropping the icon but keeping our attribute
        if ((tile.getAttribute('data-cb-bed-lock') || '') === s.state && (!s.state || icon)) { return; }
        if (icon) { icon.remove(); }
        if (!s.state) {
            tile.removeAttribute('data-cb-bed-lock');
            return;
        }
        tile.setAttribute('data-cb-bed-lock', s.state);
        icon = document.createElement('i');
        icon.className = 'fa fa-lock cb-bed-lock-icon' + (s.state === 'violation' ? ' cb-violation' : '');
        icon.title = s.title;
        icon.style.transform = uprightTransform(tile);
        tile.appendChild(icon);
    }

    function decorateTilesIn(node) {
        if (node.matches('.calendar-slot[data-reservation-id]')) {
            decorateTile(node);
            return;
        }
        node.querySelectorAll('.calendar-slot[data-reservation-id]').forEach(decorateTile);
    }

    function decorateTiles() {
        document.querySelectorAll('.calendar-slot[data-reservation-id]').forEach(decorateTile);
    }

    // ---- Booking pop-up (tooltipster) ----------------------------------------------------

    function selectedSlot() {
        var tile = document.querySelector('.calendar-slot.selected[data-reservation-id]');
        if (!tile) { return null; }
        return {
            tile: tile,
            reservationId: tile.getAttribute('data-reservation-id'),
            roomId: tile.getAttribute('data-room-id'),
            eventId: tile.getAttribute('data-id'),
            status: tile.getAttribute('data-slot'),
            checkin: tile.getAttribute('data-start-date'),
            checkout: tile.getAttribute('data-end-date'),
            guest: ((tile.querySelector('.calendar-slot-text') || {}).textContent || '').trim()
        };
    }

    function renderFooter(footer, slot) {
        footer.innerHTML = '';
        var moved = violationByKey[key(slot.reservationId, slot.roomId)];
        var lock = lockByKey[key(slot.reservationId, slot.roomId)];

        var text = document.createElement('p');
        text.className = 'mb-0';
        var button = document.createElement('button');
        button.type = 'button';
        button.className = 'cb-bed-lock-btn';

        if (moved) {
            footer.classList.add('cb-violation');
            text.textContent = 'Moved off locked bed ' + describe(moved.room, moved.bed_name, moved.room_id) +
                (moved.locked_by ? ' (locked by ' + moved.locked_by + ')' : '') + '. Move it back, or unlock to clear.';
            button.innerHTML = '<i class="fa fa-unlock"></i> Unlock';
            button.addEventListener('click', function () { unlock(moved, footer, slot, button); });
        } else if (lock) {
            footer.classList.remove('cb-violation');
            text.textContent = 'Locked to this bed' + (lock.locked_by ? ' by ' + lock.locked_by : '') + '.';
            button.innerHTML = '<i class="fa fa-unlock"></i> Unlock';
            button.addEventListener('click', function () { unlock(lock, footer, slot, button); });
        } else {
            footer.classList.remove('cb-violation');
            text.textContent = 'Not locked. Lock to get an alert if this booking is moved off this bed.';
            button.innerHTML = '<i class="fa fa-lock"></i> Lock to this bed';
            button.addEventListener('click', function () { lockSlot(footer, slot, button); });
        }
        footer.appendChild(text);
        footer.appendChild(button);
    }

    function lockSlot(footer, slot, button) {
        button.disabled = true;
        api('POST', 'bed-locks', {
            reservation_id: slot.reservationId,
            room_id: slot.roomId,
            calendar_event_id: slot.eventId,
            guest_name: slot.guest,
            checkin_date: slot.checkin,
            checkout_date: slot.checkout,
            locked_by: currentUserName()
        }, true).then(function () {
            return refreshLocks(false);
        }).then(function () {
            renderFooter(footer, slot);
        }).catch(function (e) {
            button.disabled = false;
            alert('Unable to lock: ' + e.message);
        });
    }

    function unlock(lock, footer, slot, button) {
        button.disabled = true;
        api('POST', 'bed-locks/' + lock.id + '/unlock', { unlocked_by: currentUserName() }, true).then(function () {
            return refreshLocks(false);
        }).then(function () {
            renderFooter(footer, slot);
        }).catch(function (e) {
            button.disabled = false;
            alert('Unable to unlock: ' + e.message);
        });
    }

    function onTooltip(content) {
        if (content.querySelector('.cb-bed-lock-footer')) { return; }
        var slot = selectedSlot();
        if (!slot || !slot.roomId || LOCKABLE_STATUSES.indexOf(slot.status) === -1) { return; }
        var body = content.querySelector('.slot-tt-body');
        if (!body) { return; }
        var footer = document.createElement('div');
        footer.className = 'cb-bed-lock-footer';
        body.parentNode.insertBefore(footer, body.nextSibling);
        renderFooter(footer, slot);
        // pick up locks set from another PC since the last refresh
        if (Date.now() - lastRefresh > 10000) {
            refreshLocks(false).then(function () {
                if (footer.isConnected) { renderFooter(footer, slot); }
            });
        }
    }

    // ---- Wiring --------------------------------------------------------------------------

    function injectStyle() {
        var style = document.createElement('style');
        style.textContent = [
            '.cb-bed-lock-icon { position: absolute; right: 4px; top: 50%; transform: translateY(-50%);',
            '  font-size: 12px; font-style: normal; color: #333; pointer-events: none; z-index: 2; }',
            '.cb-bed-lock-icon.cb-violation { color: #d00; }',
            '.calendar-slot[data-cb-bed-lock="violation"] { box-shadow: inset 0 0 0 2px #d00; }',
            '.cb-bed-lock-footer { display: flex; align-items: center; gap: 10px; padding: 8px 10px;',
            '  border-top: 1px solid #eee; font-size: 12px; }',
            '.cb-bed-lock-footer.cb-violation { background: #fdecea; color: #a00; }',
            '.cb-bed-lock-footer p { flex: 1; }',
            '.cb-bed-lock-btn { white-space: nowrap; border: 1px solid #ccc; border-radius: 3px;',
            '  background: #fff; padding: 3px 8px; cursor: pointer; }',
            '.cb-bed-lock-btn[disabled] { opacity: 0.5; cursor: wait; }'
        ].join('\n');
        document.head.appendChild(style);
    }

    function blockLockedDrags(e) {
        var tile = e.target && e.target.closest && e.target.closest('.calendar-slot[data-cb-bed-lock="locked"]');
        if (tile) { e.stopImmediatePropagation(); }
    }

    function start() {
        injectStyle();
        if (BLOCK_DRAG) {
            window.addEventListener('mousedown', blockLockedDrags, true);
            window.addEventListener('touchstart', blockLockedDrags, true);
        }

        var pending = null;
        new MutationObserver(function (mutations) {
            mutations.forEach(function (m) {
                // re-rendered tiles lose their icon; redraw from cached locks before the browser paints
                if (m.target.nodeType === 1 && m.target.closest('.calendar-slot')) {
                    decorateTile(m.target.closest('.calendar-slot'));
                }
                m.addedNodes.forEach(function (node) {
                    if (node.nodeType !== 1) { return; }
                    decorateTilesIn(node);
                    var content = node.matches('.slot-reservation') ? node : node.querySelector('.slot-reservation');
                    // tooltipster renders before the click handler marks the tile .selected
                    if (content) { setTimeout(function () { onTooltip(content); }, 0); }
                });
            });
            if (pending) { return; }
            pending = setTimeout(function () {
                pending = null;
                if (!document.querySelector('.c-room-line')) { return; } // not on the calendar
                decorateTiles();
                if (Date.now() - lastRefresh > REFRESH_MS) {
                    refreshLocks(false);
                }
            }, 250);
        }).observe(document.body, { childList: true, subtree: true });

        setInterval(function () {
            if (document.visibilityState === 'visible' && document.querySelector('.c-room-line')) {
                refreshLocks(false);
            }
        }, REFRESH_MS);
    }

    if (document.body) {
        start();
    } else {
        document.addEventListener('DOMContentLoaded', start);
    }
})();
