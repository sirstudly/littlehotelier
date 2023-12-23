
package com.macbackpackers.beans.cloudbeds.responses;

/**
 * A single note attached to a booking.
 */
public class BookingNote {

    private String id;
    private String notes;
    private String created;
    private String ownerName;
    private String archived;

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes( String notes ) {
        this.notes = notes;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated( String created ) {
        this.created = created;
    }

    public String getArchived() {
        return archived;
    }

    public void setArchived( String archived ) {
        this.archived = archived;
    }

    public boolean isArchived() {
        return getArchived() != null;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName( String ownerName ) {
        this.ownerName = ownerName;
    }

    @Override
    public String toString() {
        return getCreated() + ": (" + getOwnerName() + ")\n" + getNotes();
    }
}
