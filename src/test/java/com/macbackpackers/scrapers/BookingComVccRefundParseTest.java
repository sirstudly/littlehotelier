package com.macbackpackers.scrapers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.bdc.BookingComRefundRequest;

/**
 * Parses {@code vccs_to_refund} from {@code booking.com-manage.virtual.cards.har}
 * (two non-zero refunds).
 */
public class BookingComVccRefundParseTest {

    @Test
    public void parseHarFixture_twoNonZeroRefunds() throws Exception {
        String json;
        try ( InputStream in = getClass().getResourceAsStream( "/vccs_to_refund.json" ) ) {
            json = IOUtils.toString( in, StandardCharsets.UTF_8 );
        }
        BookingComSeleniumScraper scraper = new BookingComSeleniumScraper();
        List<BookingComRefundRequest> refunds = scraper.parseVccsToRefundPage(
                JsonParser.parseString( json ).getAsJsonObject() );
        assertEquals( 2, refunds.size() );
        assertEquals( "5351255482", refunds.get( 0 ).getBookingRef() );
        assertEquals( 0, new BigDecimal( "114.75" ).compareTo( refunds.get( 0 ).getRefundAmount() ) );
        assertEquals( "Refund due by 2026-09-12", refunds.get( 0 ).getReason() );
        assertEquals( "6621784337", refunds.get( 1 ).getBookingRef() );
        assertEquals( 0, new BigDecimal( "219.4" ).compareTo( refunds.get( 1 ).getRefundAmount() ) );
        assertEquals( "Refund due by 2026-09-22", refunds.get( 1 ).getReason() );
    }

    @Test
    public void skipsZeroAmountAndConfirmed() throws Exception {
        String json = "{\"success\":1,\"data\":{\"vccs\":["
                + "{\"hres_id\":\"1\",\"is_action_confirmed\":0,"
                + "\"amount_to_refund\":{\"amount\":0}},"
                + "{\"hres_id\":\"2\",\"is_action_confirmed\":1,"
                + "\"amount_to_refund\":{\"amount\":10.00},"
                + "\"due_date_to_refund_vcc\":\"2026-01-01\"}"
                + "]}}";
        BookingComSeleniumScraper scraper = new BookingComSeleniumScraper();
        JsonObject root = JsonParser.parseString( json ).getAsJsonObject();
        assertEquals( 0, scraper.parseVccsToRefundPage( root ).size() );
    }
}
