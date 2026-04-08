package com.macbackpackers.scrapers.matchers;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HSHRoomBedMatcherTest {

    @Test
    public void testRoomBedMatcher() throws Exception {
        assertRoomAndBed( "6P01AmazonT", "6P", "01AmazonT" );
        assertRoomAndBed( "6P08HudsonB", "6P", "08HudsonB" );
        assertRoomAndBed( "5A01MushroomT", "5A", "01MushroomT" );
        assertRoomAndBed( "7LOTR3MerryT", "LOTR", "3MerryT" );
        assertRoomAndBed( "7T3M4D'ArtagnanB", "T3M", "4D'ArtagnanB" );
        assertRoomAndBed( "3E01AntT", "3E", "01AntT" );
        assertRoomAndBed( "5F02BuddyHollyB", "5F", "02BuddyHollyB" );
        assertRoomAndBed( "7Lady and the Tramp", "TC", "Lady and the Tramp" );
        assertRoomAndBed( "3D03EelT", "3D", "03EelT" );
        assertRoomAndBed( "4Zoo01AnteaterT", "Zoo", "01AnteaterT" );
        assertRoomAndBed( "4Zoo02BearB", "Zoo", "02BearB" );
        assertRoomAndBed( "4F&V02BroccoliB", "F&V", "02BroccoliB" );
        assertRoomAndBed( "5G04VenusB", "5G", "04VenusB" );
        assertRoomAndBed( "7Batman&Robin", "TB", "Batman&Robin" );
        assertRoomAndBed( "7Will&Kate", "TA", "Will&Kate" );
    }

    private void assertRoomAndBed( String label, String expectedRoom, String expectedBed ) {
        BedAssignment matcher = new HSHRoomBedMatcher().parse( label );
        Assertions.assertEquals( expectedRoom, matcher.getRoom(), "room not matched" );
        Assertions.assertEquals( expectedBed, matcher.getBedName(), "bed not matched" );
    }
}
