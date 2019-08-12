
package com.macbackpackers.beans;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Holds parameters required to solve a Captcha.
 */
public class CaptchaSolveRequest {

    private String v2Key;
    private String v3Key;
    private String action;

    public CaptchaSolveRequest( String v2Key, String v3Key, String action ) {
        this.v2Key = v2Key;
        this.v3Key = v3Key;
        this.action = action;
    }

    public String getV2Key() {
        return v2Key;
    }

    public String getV3Key() {
        return v3Key;
    }

    public String getAction() {
        return action;
    }

    @Override
    public String toString() {
        return new ToStringBuilder( this )
                .append( "key (v2)", getV2Key() )
                .append( "key (v3)", getV3Key() )
                .append( "action", getAction() )
                .build();
    }
}
