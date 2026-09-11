package com.macbackpackers.ronbot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class RonbotReadServiceAvailabilityFilterTest {

    @Test
    public void excludesPaidBedVariants() {
        assertTrue( RonbotReadService.isExcludedFromAvailability( "crh", "PAID BED" ) );
        assertTrue( RonbotReadService.isExcludedFromAvailability( "hsh", "PAID BEDS" ) );
        assertTrue( RonbotReadService.isExcludedFromAvailability( "lsh", "PAID BED - NOT FOR SALE" ) );
        assertTrue( RonbotReadService.isExcludedFromAvailability( "rmb", "paid bed" ) );
    }

    @Test
    public void excludesSplits() {
        assertTrue( RonbotReadService.isExcludedFromAvailability( "crh", "Splits" ) );
        assertTrue( RonbotReadService.isExcludedFromAvailability( "hsh", "splits" ) );
        assertFalse( RonbotReadService.isExcludedFromAvailability( "crh", "Split inventory twin" ) );
    }

    @Test
    public void excludesRoom52OnlyAtCrh() {
        assertTrue( RonbotReadService.isExcludedFromAvailability( "crh", "Room 52" ) );
        assertTrue( RonbotReadService.isExcludedFromAvailability( "CRH", "ROOM 52" ) );
        assertFalse( RonbotReadService.isExcludedFromAvailability( "hsh", "Room 52" ) );
        assertFalse( RonbotReadService.isExcludedFromAvailability( "lsh", "Room 52" ) );
    }

    @Test
    public void keepsNormalSellableTypes() {
        assertFalse( RonbotReadService.isExcludedFromAvailability( "crh", "8 Bed Mixed Dormitory" ) );
        assertFalse( RonbotReadService.isExcludedFromAvailability( "lsh", "Entire Lochside Hostel" ) );
        assertFalse( RonbotReadService.isExcludedFromAvailability( "hsh", "Basic Double Room" ) );
    }
}
