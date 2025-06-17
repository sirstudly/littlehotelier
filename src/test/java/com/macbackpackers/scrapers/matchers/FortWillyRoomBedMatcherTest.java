package com.macbackpackers.scrapers.matchers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FortWillyRoomBedMatcherTest {

    @Test
    public void testRoomBedMatcher() throws Exception {
        assertRoomAndBed( "6.1 Coo Twisting", "6", "1 Coo Twisting" );
        assertRoomAndBed( "4.3 Ewan Macgregor", "4", "3 Ewan Macgregor" );
        assertRoomAndBed( "18.23 Sean Connery (B)", "18", "23 Sean Connery (B)" );
        assertRoomAndBed( "Room 03", "Room 03", null );
    }

    private void assertRoomAndBed( String label, String expectedRoom, String expectedBed ) {
        BedAssignment matcher = new FortWillyRoomBedMatcher().parse( label );
        assertEquals( expectedRoom, matcher.getRoom(), "room not matched" );
        assertEquals( expectedBed, matcher.getBedName(), "bed not matched" );
    }
}
