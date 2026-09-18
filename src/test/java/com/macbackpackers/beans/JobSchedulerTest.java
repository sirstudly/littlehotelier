package com.macbackpackers.beans;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

public class JobSchedulerTest {

    private final DecimalFormat TIME_FORMAT = new DecimalFormat( "00" );

    @Test
    public void testIsOverdue() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String repeatTimeInPast = getHourAndMinute( now.minusMinutes( 2 ) );

        JobScheduler js = new JobScheduler();
        js.setRepeatDailyAt( repeatTimeInPast );
        Timestamp twoHoursAgo = Timestamp.valueOf( now.minusHours( 2 ) );
        js.setLastRunDate( twoHoursAgo );
        assertThat( js.isOverdue(), is( true ) );

        String repeatTimeInFuture = getHourAndMinute( now.plusMinutes( 2 ) );
        js.setRepeatDailyAt( repeatTimeInFuture );
        assertThat( js.isOverdue(), is( false ) );
    }

    @Test
    public void testIsOverdueOver24Hours() throws Exception {
        JobScheduler js = new JobScheduler();
        js.setRepeatDailyAt( getHourAndMinute( LocalDateTime.now().plusMinutes( 2 ) ) );
        Timestamp yesterday = Timestamp.valueOf( LocalDateTime.now().minusHours( 25 ) );
        js.setLastRunDate( yesterday );
        assertThat( js.isOverdue(), is( true ) );
    }

    /**
     * Reproduces the RMB duplicate CreateChargeNonRefundableBookingJob case: a catch-up enqueue
     * shortly before {@code repeat_daily_at}, then another coordinator cycle after that time.
     */
    @Test
    public void testNewLastRunDatePreventsDoubleFireAcrossDailyBoundary() {
        LocalDateTime now = LocalDateTime.now();
        // Schedule a couple of minutes ahead so "now" is still before today's slot
        LocalDateTime todaysSlot = now.plusMinutes( 2 ).withSecond( 0 ).withNano( 0 );
        JobScheduler js = new JobScheduler();
        js.setRepeatDailyAt( getHourAndMinute( todaysSlot ) );
        js.setLastRunDate( Timestamp.valueOf( now.minusDays( 2 ) ) );

        assertThat( "missed prior day should be overdue before today's slot", js.isOverdue(), is( true ) );

        // What createOverdueScheduledJobs records when enqueueing
        js.setLastRunDate( js.newLastRunDate() );
        assertThat( js.getLastRunDate(), is( Timestamp.valueOf( todaysSlot ) ) );

        // Still before today's slot — must not look overdue again
        assertThat( js.isOverdue(), is( false ) );

        // Simulate clock crossing today's slot: last_run == slot means not before slot
        assertThat( js.getLastRunDate().toLocalDateTime().isBefore( todaysSlot ), is( false ) );
    }

    @Test
    public void testNewLastRunDateAfterDailySlotUsesNow() {
        LocalDateTime now = LocalDateTime.now();
        JobScheduler js = new JobScheduler();
        js.setRepeatDailyAt( getHourAndMinute( now.minusMinutes( 2 ) ) );

        Timestamp stamped = js.newLastRunDate();
        // Should be "now", not rewound to the already-passed slot
        assertThat( stamped.toLocalDateTime().isBefore( now.minusSeconds( 5 ) ), is( false ) );
        assertThat( stamped.toLocalDateTime().isAfter( now.plusSeconds( 5 ) ), is( false ) );
    }

    private String getHourAndMinute( LocalDateTime time ) {
        return TIME_FORMAT.format( time.getHour() ) + ":" +
                TIME_FORMAT.format( time.getMinute() );
    }
}
