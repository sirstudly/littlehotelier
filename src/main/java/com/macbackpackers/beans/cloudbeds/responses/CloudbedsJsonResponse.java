
package com.macbackpackers.beans.cloudbeds.responses;

import com.google.gson.annotations.SerializedName;

/**
 * I think all JSON responses from Cloudbeds includes these fields.
 */
public class CloudbedsJsonResponse {

    private Boolean success;
    private String message;
    @SerializedName( "statusMessage" )
    private String statusMessage;
    private String version;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess( Boolean success ) {
        this.success = success;
    }

    /**
     * Returns true unless success == false.
     * 
     * @return true iff success is blank or true
     */
    public boolean isSuccess() {
        return false == Boolean.FALSE.equals( getSuccess() );
    }

    /**
     * Return true iff success is present and true.
     * 
     * @return true only if success = true
     */
    public boolean isFailure() {
        return !isSuccess();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage( String message ) {
        this.message = message;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage( String statusMessage ) {
        this.statusMessage = statusMessage;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion( String version ) {
        this.version = version;
    }

}
