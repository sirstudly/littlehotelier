package com.macbackpackers.beans;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Solution returned by 2captcha for an Amazon AWS WAF captcha.
 * <p>
 * Classic responses expose {@code captcha_voucher} + {@code existing_token}. Booking.com's jsapi
 * flow often returns {@code solution.token} as a JSON object of cookies (including
 * {@code existing_token}).
 */
public class AmazonWafSolution {

    private final String taskId;
    private final String captchaVoucher;
    private final String existingToken;
    private final Map<String, String> cookies;

    public AmazonWafSolution( String taskId, String captchaVoucher, String existingToken ) {
        this( taskId, captchaVoucher, existingToken, Collections.emptyMap() );
    }

    public AmazonWafSolution( String taskId, String captchaVoucher, String existingToken,
            Map<String, String> cookies ) {
        this.taskId = taskId;
        this.captchaVoucher = captchaVoucher;
        this.existingToken = existingToken;
        this.cookies = cookies == null ? Collections.emptyMap()
                : Collections.unmodifiableMap( new LinkedHashMap<>( cookies ) );
    }

    public String getTaskId() {
        return taskId;
    }

    public String getCaptchaVoucher() {
        return captchaVoucher;
    }

    public String getExistingToken() {
        return existingToken;
    }

    /**
     * Extra cookies from {@code solution.token} JSON (Booking.jsapi). May be empty.
     */
    public Map<String, String> getCookies() {
        return cookies;
    }

    @Override
    public String toString() {
        return new ToStringBuilder( this )
                .append( "taskId", taskId )
                .append( "captchaVoucher", captchaVoucher == null ? null
                        : captchaVoucher.substring( 0, Math.min( 16, captchaVoucher.length() ) ) + "…" )
                .append( "existingToken", existingToken == null ? null
                        : existingToken.substring( 0, Math.min( 16, existingToken.length() ) ) + "…" )
                .append( "cookieCount", cookies.size() )
                .build();
    }
}
