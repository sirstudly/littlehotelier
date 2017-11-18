
package com.macbackpackers.scrapers.matchers;

import org.junit.Assert;
import org.junit.Test;

public class HSHRoomBedMatcherTest {

    @Test
    public void testRoomBedMatcher() throws Exception {
        assertRoomAndBed( "601AmazonT", "6", "01AmazonT" );
        assertRoomAndBed( "608HudsonB", "6", "08HudsonB" );
        assertRoomAndBed( "5A01MushroomT", "5A", "01MushroomT" );
        assertRoomAndBed( "Unallocated", "Unallocated", null );
        assertRoomAndBed( "LOTR3MerryT", "LOTR", "3MerryT" );
        assertRoomAndBed( "T3M4D'ArtagnanB", "T3M", "4D'ArtagnanB" );
        assertRoomAndBed( "3E01AntT", "3E", "01AntT" );
        assertRoomAndBed( "5F02BuddyHollyB", "5F", "02BuddyHollyB" );
        assertRoomAndBed( "TCLady and the Tramp", "TC", "Lady and the Tramp" );
        assertRoomAndBed( "3D03EelT", "3D", "03EelT" );
        assertRoomAndBed( "Zoo01AnteaterT", "Zoo", "01AnteaterT" );
        assertRoomAndBed( "F&V02BroccoliB", "F&V", "02BroccoliB" );
        assertRoomAndBed( "5G04VenusB", "5G", "04VenusB" );
        assertRoomAndBed( "TBBatman&Robin", "TB", "Batman&Robin" );
        assertRoomAndBed( "TAWill&Kate", "TA", "Will&Kate" );
        assertRoomAndBed( "Unrecognized entry", "Unrecognized entry", null );
    }

    private void assertRoomAndBed( String label, String expectedRoom, String expectedBed ) {
        BedAssignment matcher = new HSHRoomBedMatcher().parse( label );
        Assert.assertEquals( "room not matched", expectedRoom, matcher.getRoom() );
        Assert.assertEquals( "bed not matched", expectedBed, matcher.getBedName() );
    }
}
