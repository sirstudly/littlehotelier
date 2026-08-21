// ==UserScript==
// @name         Cloudbeds Add Balance Due (excl. EVL)
// @namespace    http://cloudbeds.com/
// @version      1.1
// @updateURL    https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsAddBalanceDueMinusEVL.js
// @downloadURL  https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/CloudbedsAddBalanceDueMinusEVL.js
// @description  In the reservation totals submenu, show Balance Due excluding exclusive Edinburgh Visitor Levy (for cash-register split). Skips inclusive EVL (e.g. Booking.com).
// @author       RONBOT
// @match        https://hotels.cloudbeds.com/connect/*
// @match        https://macbackpackers.cloudbeds.com/connect/*
// @require      https://raw.githubusercontent.com/sirstudly/littlehotelier/refs/heads/master/scripts/tampermonkey/cloudbeds-core.js
// @grant        unsafeWindow
// ==/UserScript==

(function () {
    'use strict';

    var CB = window.CB;

    var ROW_ATTR = 'data-cb-balance-excl-evl';
    var LABEL = 'Balance Due (excl. EVL)';
    var EVL_MARKER = 'edinburgh visitor levy';
    var INCLUSIVE_MARKER = 'inclusive';

    var keepAliveObserver = null;
    var keepAliveReservationId = null;

    function isExclusiveVisitorLevyLabel(name) {
        if (!name) { return false; }
        var lower = String(name).toLowerCase();
        return lower.indexOf(EVL_MARKER) !== -1 && lower.indexOf(INCLUSIVE_MARKER) === -1;
    }

    // Parse Cloudbeds currency cells like "£256.80" / "-£1.20" / "£1,234.56".
    function parseMoney(text) {
        if (text == null) { return null; }
        var raw = String(text).trim();
        if (!raw) { return null; }
        var negative = /^-/.test(raw) || /\(.*\)/.test(raw);
        var digits = raw.replace(/[^\d.,]/g, '');
        if (!digits) { return null; }
        if (digits.indexOf(',') !== -1 && digits.indexOf('.') !== -1) {
            digits = digits.replace(/,/g, '');
        } else if (digits.indexOf(',') !== -1) {
            digits = digits.replace(',', '.');
        }
        var n = parseFloat(digits);
        if (isNaN(n)) { return null; }
        return negative ? -n : n;
    }

    // Mirror the Balance Due cell's currency symbol / spacing.
    function formatMoney(amount, sampleText) {
        var sample = String(sampleText || '').trim();
        var symbol = '£';
        var m = sample.match(/([^\d\s.,\-()]+)/);
        if (m && m[1]) { symbol = m[1]; }
        var abs = Math.abs(amount).toFixed(2);
        if (amount < 0) { return '-' + symbol + abs; }
        return symbol + abs;
    }

    function round2(n) {
        return Math.round(n * 100) / 100;
    }

    function findTotalsTables() {
        // Dropdown may live under #rs-totals-container or as a Bootstrap menu elsewhere.
        var nodes = document.querySelectorAll(
            '#rs-totals-container table.rs-totals-table, ul.rs-totals-dropdown table.rs-totals-table'
        );
        var seen = [];
        Array.prototype.forEach.call(nodes, function (table) {
            if (seen.indexOf(table) === -1) { seen.push(table); }
        });
        return seen;
    }

    function rowLabel(tr) {
        var td = tr && tr.querySelector('td');
        return td ? (td.textContent || '').trim() : '';
    }

    function rowAmountText(tr) {
        var tds = tr ? tr.querySelectorAll('td') : [];
        if (tds.length < 2) { return ''; }
        return (tds[1].textContent || '').trim();
    }

    function removeInjectedRows(table) {
        var removed = false;
        table.querySelectorAll('tr[' + ROW_ATTR + ']').forEach(function (tr) {
            tr.remove();
            removed = true;
        });
        return removed;
    }

    // Insert / refresh "Balance Due (excl. EVL)" under Balance Due when exclusive EVL > 0.
    // Avoids unnecessary DOM writes so the keep-alive MutationObserver does not loop.
    function injectIntoTable(table) {
        var rows = Array.prototype.slice.call(table.querySelectorAll('tr'));
        var exclusiveEvl = 0;
        var foundExclusive = false;
        var balanceDueRow = null;
        var existing = null;

        rows.forEach(function (tr) {
            if (tr.hasAttribute(ROW_ATTR)) {
                existing = tr;
                return;
            }
            var label = rowLabel(tr);
            if (isExclusiveVisitorLevyLabel(label)) {
                var amt = parseMoney(rowAmountText(tr));
                if (amt != null) {
                    exclusiveEvl += amt;
                    foundExclusive = true;
                }
            }
            if (label === 'Balance Due') {
                balanceDueRow = tr;
            }
        });

        if (!foundExclusive || !balanceDueRow) {
            return removeInjectedRows(table);
        }
        exclusiveEvl = round2(exclusiveEvl);
        if (exclusiveEvl === 0) {
            return removeInjectedRows(table);
        }

        var balanceDueText = rowAmountText(balanceDueRow);
        var balanceDue = parseMoney(balanceDueText);
        if (balanceDue == null) {
            return removeInjectedRows(table);
        }

        // Balance due minus exclusive EVL, floored at 0 (negative "due" is not useful at the till).
        var excl = Math.max(0, round2(balanceDue - exclusiveEvl));
        var formatted = formatMoney(excl, balanceDueText);
        var desiredNext = balanceDueRow.nextElementSibling;

        if (existing) {
            var valueTd = existing.querySelectorAll('td')[1];
            var valueOk = valueTd && (valueTd.textContent || '').trim() === formatted;
            var positionOk = desiredNext === existing;
            if (valueOk && positionOk) { return false; }
            if (!positionOk) {
                existing.remove();
                existing = null;
            } else if (valueTd) {
                valueTd.textContent = formatted;
                valueTd.style.textAlign = 'right';
                return true;
            }
        }

        var tr = document.createElement('tr');
        tr.setAttribute(ROW_ATTR, '1');
        var tdLabel = document.createElement('td');
        tdLabel.textContent = LABEL;
        var tdValue = document.createElement('td');
        tdValue.className = 'bold';
        tdValue.style.textAlign = 'right';
        tdValue.textContent = formatted;
        tr.appendChild(tdLabel);
        tr.appendChild(tdValue);

        if (balanceDueRow.nextSibling) {
            balanceDueRow.parentNode.insertBefore(tr, balanceDueRow.nextSibling);
        } else {
            balanceDueRow.parentNode.appendChild(tr);
        }
        return true;
    }

    function injectAll() {
        var tables = findTotalsTables();
        if (!tables.length) { return false; }
        var any = false;
        tables.forEach(function (table) {
            if (injectIntoTable(table)) { any = true; }
        });
        return any;
    }

    function stopKeepAliveObserver() {
        if (keepAliveObserver) {
            keepAliveObserver.disconnect();
            keepAliveObserver = null;
        }
        keepAliveReservationId = null;
    }

    function clearInjectedRows() {
        document.querySelectorAll('tr[' + ROW_ATTR + ']').forEach(function (tr) {
            tr.remove();
        });
    }

    function stopKeepAlive() {
        stopKeepAliveObserver();
        clearInjectedRows();
    }

    // Vue rewrites .rs-totals-table when folio totals refresh; re-inject after those mutations.
    function startKeepAlive(reservationId) {
        stopKeepAliveObserver();
        keepAliveReservationId = reservationId;

        var pending = false;
        keepAliveObserver = new MutationObserver(function () {
            if (pending) { return; }
            if (CB.context().reservationId !== keepAliveReservationId) {
                stopKeepAlive();
                return;
            }
            pending = true;
            setTimeout(function () {
                pending = false;
                if (CB.context().reservationId !== keepAliveReservationId) {
                    stopKeepAlive();
                    return;
                }
                injectAll();
            }, 100);
        });
        keepAliveObserver.observe(document.body || document.documentElement, {
            childList: true,
            subtree: true
        });
    }

    function findReadyTotals() {
        var container = document.querySelector('#rs-totals-container');
        if (!container) { return null; }
        // Prefer a table that already has Balance Due (dropdown rendered); else container is enough
        // to start observing — inject runs again when the menu/table appears.
        return container;
    }

    CB.onReservation('balance-excl-evl', function (ctx, meta) {
        stopKeepAlive();
        return CB.waitFor(findReadyTotals, {
            skipExisting: !!(meta && meta.skipExisting),
            settle: 500
        }).then(function (container) {
            if (!container) { return false; }
            if (CB.context().reservationId !== ctx.reservationId) { return false; }
            injectAll();
            startKeepAlive(ctx.reservationId);
            return true;
        });
    });
})();
