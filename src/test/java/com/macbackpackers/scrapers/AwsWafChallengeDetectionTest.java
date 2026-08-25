package com.macbackpackers.scrapers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Documents detection rules for Booking.com AWS WAF based on
 * {@code booking.com-captcha.at.login.har} (jsapi / awswaf-captcha) vs silent challenge.js.
 */
public class AwsWafChallengeDetectionTest {

    /**
     * Mirrors the boolean logic in {@link BookingComSeleniumScraper#isAwsWafChallenge}.
     */
    static boolean detect( boolean hasAwsElement, boolean hasContainer, boolean hasGoku,
            boolean hasCaptchaJs, boolean hasJsapi, boolean visualProblem, boolean humanPrompt ) {
        return hasAwsElement
                || ( hasJsapi && ( visualProblem || humanPrompt || hasContainer ) )
                || ( hasContainer && ( hasGoku || hasCaptchaJs || hasJsapi ) )
                || ( hasGoku && ( hasCaptchaJs || hasJsapi || humanPrompt ) );
    }

    @Test
    public void detectsJsapiVisualCaptchaFromLoginHar() {
        // HAR: jsapi.js + problem?kind=visual + awswaf-captcha (no gokuProps / captcha-container)
        assertTrue( detect( true, false, false, false, true, true, false ) );
        assertTrue( detect( false, false, false, false, true, true, false ) );
        assertTrue( detect( true, false, false, false, true, false, false ) );
    }

    @Test
    public void ignoresSilentChallengeJsOnly() {
        assertFalse( detect( false, false, false, false, false, false, false ) );
    }

    @Test
    public void detectsLegacyGokuCaptchaContainer() {
        assertTrue( detect( false, true, true, true, false, false, false ) );
    }

    @Test
    public void filterAmazonWafCookies_dropsFingerprintCookies() {
        Map<String, String> bag = new LinkedHashMap<>();
        bag.put( "existing_token", "tok-abc" );
        bag.put( "aws-waf-token", "tok-abc" );
        bag.put( "thx_guid", "guid123" );
        bag.put( "bkng_bfp", "deadbeef" );
        Map<String, String> filtered = BookingComSeleniumScraper.filterAmazonWafCookies( bag );
        assertEquals( "tok-abc", filtered.get( "aws-waf-token" ) );
        assertEquals( "tok-abc", filtered.get( "existing_token" ) );
        assertFalse( filtered.containsKey( "thx_guid" ) );
        assertFalse( filtered.containsKey( "bkng_bfp" ) );
    }
}
