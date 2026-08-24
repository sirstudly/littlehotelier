package com.macbackpackers.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.AmazonWafSolution;
import com.macbackpackers.exceptions.UnrecoverableFault;

public class CaptchaSolverServiceAmazonWafTest {

    private final CaptchaSolverService service = new CaptchaSolverService();

    @Test
    public void applyAmazonTaskProxy_parsesHostPort() {
        JsonObject task = new JsonObject();
        service.applyAmazonTaskProxy( task, "1.2.3.4:8080", "HTTPS" );
        assertEquals( "http", task.get( "proxyType" ).getAsString() );
        assertEquals( "1.2.3.4", task.get( "proxyAddress" ).getAsString() );
        assertEquals( 8080, task.get( "proxyPort" ).getAsInt() );
        assertFalse( task.has( "proxyLogin" ) );
    }

    @Test
    public void applyAmazonTaskProxy_parsesCredentials() {
        JsonObject task = new JsonObject();
        service.applyAmazonTaskProxy( task, "user:p4$$@proxy.example:3128", "socks5" );
        assertEquals( "socks5", task.get( "proxyType" ).getAsString() );
        assertEquals( "proxy.example", task.get( "proxyAddress" ).getAsString() );
        assertEquals( 3128, task.get( "proxyPort" ).getAsInt() );
        assertEquals( "user", task.get( "proxyLogin" ).getAsString() );
        assertEquals( "p4$$", task.get( "proxyPassword" ).getAsString() );
    }

    @Test
    public void applyAmazonTaskProxy_rejectsMissingPort() {
        UnrecoverableFault ex = assertThrows( UnrecoverableFault.class,
                () -> service.applyAmazonTaskProxy( new JsonObject(), "1.2.3.4", "http" ) );
        assertTrue( ex.getMessage().contains( "host:port" ) );
    }

    @Test
    public void parseAmazonWafSolution_acceptsBookingJsapiTokenCookieBag() {
        // Shape returned for Booking.com jsapi solves (from live getTaskResult).
        String responseBody = "{"
                + "\"errorId\":0,\"status\":\"ready\","
                + "\"solution\":{\"token\":\"{"
                + "\\\"existing_token\\\":\\\"tok-abc:def/ghi=\\\","
                + "\\\"bkng_bfp\\\":\\\"deadbeef\\\","
                + "\\\"thx_guid\\\":\\\"guid123\\\""
                + "}\"}}";
        JsonObject solution = JsonParser.parseString( responseBody ).getAsJsonObject().getAsJsonObject( "solution" );
        AmazonWafSolution parsed = service.parseAmazonWafSolution( "123", solution, responseBody );
        assertEquals( "tok-abc:def/ghi=", parsed.getExistingToken() );
        assertEquals( "tok-abc:def/ghi=", parsed.getCookies().get( "aws-waf-token" ) );
        assertEquals( "deadbeef", parsed.getCookies().get( "bkng_bfp" ) );
        assertTrue( parsed.getCookies().size() >= 3 );
    }

    @Test
    public void parseAmazonWafSolution_acceptsClassicVoucherFields() {
        String responseBody = "{\"errorId\":0,\"status\":\"ready\",\"solution\":{"
                + "\"captcha_voucher\":\"voucher1\",\"existing_token\":\"token1\"}}";
        JsonObject solution = JsonParser.parseString( responseBody ).getAsJsonObject().getAsJsonObject( "solution" );
        AmazonWafSolution parsed = service.parseAmazonWafSolution( "99", solution, responseBody );
        assertEquals( "voucher1", parsed.getCaptchaVoucher() );
        assertEquals( "token1", parsed.getExistingToken() );
        assertTrue( parsed.getCookies().isEmpty() );
    }
}
