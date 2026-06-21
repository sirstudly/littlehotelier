
package com.macbackpackers.scrapers.cloudbedsws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class CloudbedsEventIdParserTest {

    @Test
    public void parseBookingIdFromEventId_extractsNineDigitPrefix() {
        assertEquals( "178177230", CloudbedsEventIdParser.parseBookingIdFromEventId( "178177230140204791" ) );
        assertEquals( "177809254", CloudbedsEventIdParser.parseBookingIdFromEventId( "17780925437489697" ) );
        assertEquals( "178203082", CloudbedsEventIdParser.parseBookingIdFromEventId( "178203082720008110" ) );
    }

    @Test
    public void parseBookingIdFromEventId_rejectsTooShortOrNonReservationPrefix() {
        assertNull( CloudbedsEventIdParser.parseBookingIdFromEventId( null ) );
        assertNull( CloudbedsEventIdParser.parseBookingIdFromEventId( "" ) );
        assertNull( CloudbedsEventIdParser.parseBookingIdFromEventId( "123456789" ) );
        assertNull( CloudbedsEventIdParser.parseBookingIdFromEventId( "abc1234567890" ) );
        assertNull( CloudbedsEventIdParser.parseBookingIdFromEventId( "0123456789012345" ) );
    }
}
