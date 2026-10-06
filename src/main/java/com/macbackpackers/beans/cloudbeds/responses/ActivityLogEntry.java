
package com.macbackpackers.beans.cloudbeds.responses;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents an entry on the "Activity" log (property-wide or for a single reservation).
 */
public class ActivityLogEntry {

    private LocalDateTime createdDate;
    private String createdBy;
    private String contents;

    /** suffix of the {@code activityLog_*} css class, eg. create, modify, delete, delete_details */
    private String action;

    /** event title, eg. "Reservation status modified" */
    private String title;

    /** reservation identifiers (the number shown under the guest name) referenced by this entry */
    private Set<String> reservationIdentifiers = new LinkedHashSet<>();

    /** numeric reservation ids referenced by this entry */
    private Set<String> reservationIds = new LinkedHashSet<>();

    /** guest (reservation_record) ids referenced by this entry */
    private Set<String> guestIds = new LinkedHashSet<>();

    /** guest email for "Guest Info modified" entries */
    private String guestEmail;

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate( LocalDateTime createdDate ) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy( String createdBy ) {
        this.createdBy = createdBy;
    }

    public String getContents() {
        return contents;
    }

    public void setContents( String contents ) {
        this.contents = contents;
    }

    public String getAction() {
        return action;
    }

    public void setAction( String action ) {
        this.action = action;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle( String title ) {
        this.title = title;
    }

    public Set<String> getReservationIdentifiers() {
        return reservationIdentifiers;
    }

    public void setReservationIdentifiers( Set<String> reservationIdentifiers ) {
        this.reservationIdentifiers = reservationIdentifiers;
    }

    public Set<String> getReservationIds() {
        return reservationIds;
    }

    public void setReservationIds( Set<String> reservationIds ) {
        this.reservationIds = reservationIds;
    }

    public Set<String> getGuestIds() {
        return guestIds;
    }

    public void setGuestIds( Set<String> guestIds ) {
        this.guestIds = guestIds;
    }

    public String getGuestEmail() {
        return guestEmail;
    }

    public void setGuestEmail( String guestEmail ) {
        this.guestEmail = guestEmail;
    }

    @Override
    public String toString() {
        return createdDate + " [" + createdBy + "] " + title + " " + reservationIdentifiers;
    }

}
