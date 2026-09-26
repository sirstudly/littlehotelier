package com.macbackpackers.ronbot.dto;

/**
 * Query-string criteria for the Cloudbeds reservation list search. All fields are optional
 * YYYY-MM-DD dates / text; date ranges are inclusive and a lone {@code *From} or {@code *To}
 * means that single day.
 */
public class ReservationSearchCriteria {

    /** Free-text search (guest name, reservation number, third-party reference, ...). */
    private String query;
    private String stayFrom;
    private String stayTo;
    private String checkinFrom;
    private String checkinTo;
    private String checkoutFrom;
    private String checkoutTo;
    private String bookedFrom;
    private String bookedTo;
    /** Comma-delimited Cloudbeds statuses (confirmed, not_confirmed, checked_in, checked_out, canceled, no_show). */
    private String statuses;
    /** Comma-delimited OTA source names as shown in Cloudbeds (e.g. Booking.com, Hostelworld). */
    private String sources;
    /** Maximum rows to return (default and hard max 1000). */
    private Integer limit;

    public Integer getLimit() {
        return limit;
    }

    public void setLimit( Integer limit ) {
        this.limit = limit;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery( String query ) {
        this.query = query;
    }

    public String getStayFrom() {
        return stayFrom;
    }

    public void setStayFrom( String stayFrom ) {
        this.stayFrom = stayFrom;
    }

    public String getStayTo() {
        return stayTo;
    }

    public void setStayTo( String stayTo ) {
        this.stayTo = stayTo;
    }

    public String getCheckinFrom() {
        return checkinFrom;
    }

    public void setCheckinFrom( String checkinFrom ) {
        this.checkinFrom = checkinFrom;
    }

    public String getCheckinTo() {
        return checkinTo;
    }

    public void setCheckinTo( String checkinTo ) {
        this.checkinTo = checkinTo;
    }

    public String getCheckoutFrom() {
        return checkoutFrom;
    }

    public void setCheckoutFrom( String checkoutFrom ) {
        this.checkoutFrom = checkoutFrom;
    }

    public String getCheckoutTo() {
        return checkoutTo;
    }

    public void setCheckoutTo( String checkoutTo ) {
        this.checkoutTo = checkoutTo;
    }

    public String getBookedFrom() {
        return bookedFrom;
    }

    public void setBookedFrom( String bookedFrom ) {
        this.bookedFrom = bookedFrom;
    }

    public String getBookedTo() {
        return bookedTo;
    }

    public void setBookedTo( String bookedTo ) {
        this.bookedTo = bookedTo;
    }

    public String getStatuses() {
        return statuses;
    }

    public void setStatuses( String statuses ) {
        this.statuses = statuses;
    }

    public String getSources() {
        return sources;
    }

    public void setSources( String sources ) {
        this.sources = sources;
    }
}
