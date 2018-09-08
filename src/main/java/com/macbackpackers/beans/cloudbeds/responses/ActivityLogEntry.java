
package com.macbackpackers.beans.cloudbeds.responses;

import java.time.LocalDateTime;

/**
 * Represents an entry on the "Reservation Activity" tab. 
 */
public class ActivityLogEntry {

    private LocalDateTime createdDate;
    private String createdBy;
    private String contents;

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

}
