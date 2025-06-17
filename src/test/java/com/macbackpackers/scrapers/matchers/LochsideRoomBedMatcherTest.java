package com.macbackpackers.scrapers.matchers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class LochsideRoomBedMatcherTest {

    @Test
    public void testRoomBedMatcher() {
        assertRoomAndBed( "Room 16(A)", "Room 16", "A" );
        assertRoomAndBed( "Single Room(A)", "Single Room", "A" );
        assertRoomAndBed( "Twin Room 02(A)", "Twin Room 02", "A" );
        assertRoomAndBed( "Twin Room 03 (B )", "Twin Room 03", "B" );
        assertRoomAndBed( "Unallocated", "Unallocated", null );
        assertRoomAndBed( "3 - (LL) Nessie", "3", "(LL) Nessie" );
        assertRoomAndBed( "1 - (FG) Baobhan-Sith", "1", "(FG) Baobhan-Sith" );
    }

    private void assertRoomAndBed( String label, String expectedRoom, String expectedBed ) {
        BedAssignment matcher = new LochsideRoomBedMatcher().parse( label );
        assertEquals( expectedRoom, matcher.getRoom(), "room not matched" );
        assertEquals( expectedBed, matcher.getBedName(), "bed not matched" );
    }
}
