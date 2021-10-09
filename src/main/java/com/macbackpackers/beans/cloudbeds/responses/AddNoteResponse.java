
package com.macbackpackers.beans.cloudbeds.responses;

public class AddNoteResponse extends CloudbedsJsonResponse {

    // the ID of the note which was added on success
    private String id;

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }
}
