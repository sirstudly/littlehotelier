
package com.macbackpackers.beans.cloudbeds.responses;

/**
 * I think all JSON responses from Cloudbeds includes these fields.
 */
public class CloudbedsJsonResponse {

    private Boolean success;
    private String message;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess( Boolean success ) {
        this.success = success;
    }

    /**
     * Returns true unless success == false.
     * @return true iff success is blank or true
     */
    public boolean isSuccess() {
        return false == Boolean.FALSE.equals( getSuccess() );
    }

    public String getMessage() {
        return message;
    }

    public void setMessage( String message ) {
        this.message = message;
    }

}
