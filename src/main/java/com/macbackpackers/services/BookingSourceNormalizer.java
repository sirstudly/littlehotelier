package com.macbackpackers.services;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Maps Cloudbeds reservation source labels to the shorter names used in channel reports.
 */
@Component
public class BookingSourceNormalizer {

    private static final String CHANNEL_COLLECT = " (Channel Collect Booking)";
    private static final String HOTEL_COLLECT = " (Hotel Collect Booking)";

    /**
     * @param raw Cloudbeds source name (may be blank)
     * @return normalized label, or null if blank
     */
    public String normalize( String raw ) {
        if ( StringUtils.isBlank( raw ) || "-".equals( raw.trim() ) ) {
            return null;
        }
        String s = raw.trim();
        if ( s.endsWith( CHANNEL_COLLECT ) ) {
            s = s.substring( 0, s.length() - CHANNEL_COLLECT.length() );
        }
        else if ( s.endsWith( HOTEL_COLLECT ) ) {
            s = s.substring( 0, s.length() - HOTEL_COLLECT.length() );
        }
        if ( "Website/Booking Engine".equals( s ) ) {
            return "Website";
        }
        if ( "RECEPTION".equalsIgnoreCase( s ) ) {
            return "Reception";
        }
        return s;
    }
}
