
package com.macbackpackers.scrapers.matchers;

import org.junit.Assert;
import org.junit.Test;

public class LochsideRoomBedMatcherTest {

    @Test
    public void testRoomBedMatcher() throws Exception {
        assertRoomAndBed( "Room 16(A)", "Room 16", "A" );
        assertRoomAndBed( "Single Room(A)", "Single Room", "A" );
        assertRoomAndBed( "Twin Room 02(A)", "Twin Room 02", "A" );
        assertRoomAndBed( "Twin Room 03 (B )", "Twin Room 03", "B" );
        assertRoomAndBed( "Unallocated", "Unallocated", null );
    }

    private void assertRoomAndBed( String label, String expectedRoom, String expectedBed ) {
        BedAssignment matcher = new LochsideRoomBedMatcher().parse( label );
        Assert.assertEquals( "room not matched", expectedRoom, matcher.getRoom() );
        Assert.assertEquals( "bed not matched", expectedBed, matcher.getBedName() );
    }
}
