package com.macbackpackers.services;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.macbackpackers.beans.cloudbeds.responses.ActivityLogEntry;

/**
 * Decides which activity log entries can change {@code wp_lh_booking_assignment} data. Anything not
 * on the ignore list that references a reservation triggers a refresh, so new/unknown titles are
 * refreshed rather than skipped.
 */
public final class ActivityLogClassifier {

    /** Titles (lower case) that never change any booking assignment field. */
    private static final Set<String> IGNORED_TITLES = new HashSet<>( Arrays.asList(
            // reservation-level, no booking assignment field affected
            "credit card added",
            "credit card edited",
            "credit card viewed",
            "credit card deleted",
            "credit card status changed",
            "credit card details deleted",
            "long term credit card storage",
            "email manually sent",
            "email resent from log",
            "reservation sensitive data viewed",
            "payment pending approval added to folio.",
            "printed confirmation",
            "printed registration card",
            "printed reservation folio",
            "government receipt opened",
            // no reservation referenced
            "daily rate interval added (via calendar)",
            "daily rate interval removed (via calendar)",
            "daily rate interval modified (via calendar)",
            "user is logged in",
            "user is logged out",
            "user switched property",
            "auto-assign failed",
            "guest accepted the terms & conditions",
            "guest was created",
            "guest linked to person profile",
            "guest sensitive data viewed",
            "single room block created",
            "single room block modified",
            "single room block removed",
            "out-of-service block created",
            "out-of-service block modified",
            "out-of-service block removed" ) );

    /** Title keywords (lower case) for invoice / receipt / credit note documents. */
    private static final String[] IGNORED_TITLE_KEYWORDS = {
            "invoice", "receipt", "credit note", "tax withholding" };

    /** Removing a reservation means a REST fetch will fail; rows are closed locally instead. */
    public static final String TITLE_RESERVATION_REMOVED = "reservation removed";

    public static final String TITLE_GUEST_INFO_MODIFIED = "guest info modified";

    private ActivityLogClassifier() {
        // static methods only
    }

    /**
     * True if the entry can't change any booking assignment data.
     */
    public static boolean isIgnored( ActivityLogEntry entry ) {
        String title = normalisedTitle( entry );
        if ( IGNORED_TITLES.contains( title ) ) {
            return true;
        }
        for ( String keyword : IGNORED_TITLE_KEYWORDS ) {
            if ( title.contains( keyword ) ) {
                return true;
            }
        }
        return false;
    }

    public static boolean isReservationRemoved( ActivityLogEntry entry ) {
        return TITLE_RESERVATION_REMOVED.equals( normalisedTitle( entry ) );
    }

    public static boolean isGuestInfoModified( ActivityLogEntry entry ) {
        return TITLE_GUEST_INFO_MODIFIED.equals( normalisedTitle( entry ) );
    }

    /**
     * Lower-cased title (titles vary in case, eg. "Reservation reassigned/Reassigned to new room type").
     */
    public static String normalisedTitle( ActivityLogEntry entry ) {
        return entry.getTitle() == null ? "" : entry.getTitle().trim().toLowerCase( Locale.ENGLISH );
    }
}
