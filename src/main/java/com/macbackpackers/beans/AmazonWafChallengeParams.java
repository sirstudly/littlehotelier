package com.macbackpackers.beans;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Parameters extracted from an AWS WAF challenge page for 2captcha AmazonTask.
 */
public class AmazonWafChallengeParams {

    private final String websiteUrl;
    private final String websiteKey;
    private final String iv;
    private final String context;
    private final String challengeScript;
    private final String captchaScript;
    private final String jsapiScript;
    private final String userAgent;

    public AmazonWafChallengeParams( String websiteUrl, String websiteKey, String iv, String context,
            String challengeScript, String captchaScript, String jsapiScript ) {
        this( websiteUrl, websiteKey, iv, context, challengeScript, captchaScript, jsapiScript, null );
    }

    public AmazonWafChallengeParams( String websiteUrl, String websiteKey, String iv, String context,
            String challengeScript, String captchaScript, String jsapiScript, String userAgent ) {
        this.websiteUrl = websiteUrl;
        this.websiteKey = websiteKey;
        this.iv = iv;
        this.context = context;
        this.challengeScript = challengeScript;
        this.captchaScript = captchaScript;
        this.jsapiScript = jsapiScript;
        this.userAgent = userAgent;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getWebsiteKey() {
        return websiteKey;
    }

    public String getIv() {
        return iv;
    }

    public String getContext() {
        return context;
    }

    public String getChallengeScript() {
        return challengeScript;
    }

    public String getCaptchaScript() {
        return captchaScript;
    }

    public String getJsapiScript() {
        return jsapiScript;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public boolean isJsapi() {
        return jsapiScript != null && !jsapiScript.isEmpty();
    }

    @Override
    public String toString() {
        return new ToStringBuilder( this )
                .append( "websiteUrl", websiteUrl )
                .append( "websiteKey", websiteKey == null ? null : websiteKey.substring( 0, Math.min( 24, websiteKey.length() ) ) + "…" )
                .append( "iv", iv )
                .append( "jsapi", isJsapi() )
                .append( "challengeScript", challengeScript )
                .append( "captchaScript", captchaScript )
                .append( "jsapiScript", jsapiScript )
                .append( "userAgent", userAgent )
                .build();
    }
}
