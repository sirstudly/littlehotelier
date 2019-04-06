
package com.macbackpackers.beans;

import static org.junit.Assert.assertThat;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.LocalDateTime;

import org.hamcrest.Matchers;
import org.junit.Test;

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
        assertThat( js.isOverdue(), Matchers.is( true ) );

        String repeatTimeInFuture = getHourAndMinute( now.plusMinutes( 2 ) );
        js.setRepeatDailyAt( repeatTimeInFuture );
        assertThat( js.isOverdue(), Matchers.is( false ) );
    }

    @Test
    public void testIsOverdueOver24Hours() throws Exception {
        JobScheduler js = new JobScheduler();
        js.setRepeatDailyAt( getHourAndMinute( LocalDateTime.now().plusMinutes( 2 ) ) );
        Timestamp yesterday = Timestamp.valueOf( LocalDateTime.now().minusHours( 25 ) );
        js.setLastRunDate( yesterday );
        assertThat( js.isOverdue(), Matchers.is( true ) );
    }

    private String getHourAndMinute( LocalDateTime time ) {
        return TIME_FORMAT.format( time.getHour() ) + ":" +
                TIME_FORMAT.format( time.getMinute() );
    }
}
