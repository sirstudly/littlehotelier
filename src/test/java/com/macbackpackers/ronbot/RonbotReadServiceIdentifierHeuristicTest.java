package com.macbackpackers.ronbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RonbotReadServiceIdentifierHeuristicTest {

    @Test
    public void treatsNumericAndAlphanumericRefsAsIdentifiers() {
        assertTrue( RonbotReadService.looksLikeReservationIdentifier( "38059804" ) );
        assertTrue( RonbotReadService.looksLikeReservationIdentifier( "4586958844219" ) );
        assertTrue( RonbotReadService.looksLikeReservationIdentifier( "123-456789" ) );
        assertTrue( RonbotReadService.looksLikeReservationIdentifier( "HW12345678" ) );
    }

    @Test
    public void treatsGuestNamesAsNonIdentifiers() {
        assertFalse( RonbotReadService.looksLikeReservationIdentifier( "Jane Smith" ) );
        assertFalse( RonbotReadService.looksLikeReservationIdentifier( "Smith" ) );
        assertFalse( RonbotReadService.looksLikeReservationIdentifier( "O'Brien" ) );
        assertFalse( RonbotReadService.looksLikeReservationIdentifier( "" ) );
        assertFalse( RonbotReadService.looksLikeReservationIdentifier( "   " ) );
    }
}
