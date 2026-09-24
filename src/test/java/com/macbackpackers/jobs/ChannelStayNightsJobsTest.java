package com.macbackpackers.jobs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;

/** Job wiring tests without Mockito (Byte Buddy agent not available in all environments). */
public class ChannelStayNightsJobsTest {

    @Test
    public void backfillEnqueuesOneJobPerMonth() throws Exception {
        List<Job> inserted = new ArrayList<>();
        WordPressDAO dao = recordingDao( inserted );

        CreateBackfillChannelStayNightsJob backfillJob = new CreateBackfillChannelStayNightsJob();
        backfillJob.dao = dao;
        backfillJob.setStartDate( LocalDate.of( 2024, 1, 15 ) );
        backfillJob.setEndDate( LocalDate.of( 2024, 3, 10 ) );
        backfillJob.processJob();

        assertThat( inserted.size(), is( 3 ) );
        SyncChannelStayNightsJob j0 = (SyncChannelStayNightsJob) inserted.get( 0 );
        SyncChannelStayNightsJob j1 = (SyncChannelStayNightsJob) inserted.get( 1 );
        SyncChannelStayNightsJob j2 = (SyncChannelStayNightsJob) inserted.get( 2 );
        assertThat( j0.getStatus(), is( JobStatus.submitted ) );
        assertThat( j0.getStartDate(), is( LocalDate.of( 2024, 1, 15 ) ) );
        assertThat( j0.getEndDate(), is( LocalDate.of( 2024, 1, 31 ) ) );
        assertThat( j1.getStartDate(), is( LocalDate.of( 2024, 2, 1 ) ) );
        assertThat( j1.getEndDate(), is( LocalDate.of( 2024, 2, 29 ) ) );
        assertThat( j2.getStartDate(), is( LocalDate.of( 2024, 3, 1 ) ) );
        assertThat( j2.getEndDate(), is( LocalDate.of( 2024, 3, 10 ) ) );
    }

    @Test
    public void yearMonthSpanForThreeYearsIsThirtySixPlus() {
        LocalDate start = LocalDate.of( 2023, 10, 1 );
        LocalDate end = LocalDate.of( 2026, 9, 30 );
        int months = 0;
        for ( YearMonth y = YearMonth.from( start ); !y.isAfter( YearMonth.from( end ) ); y = y.plusMonths( 1 ) ) {
            months++;
        }
        assertThat( months, greaterThanOrEqualTo( 36 ) );
    }

    @Test
    public void syncJobParametersRoundTrip() {
        SyncChannelStayNightsJob job = new SyncChannelStayNightsJob();
        job.setStartDate( LocalDate.of( 2026, 8, 1 ) );
        job.setEndDate( LocalDate.of( 2026, 8, 31 ) );
        assertThat( job.getStartDate(), is( LocalDate.of( 2026, 8, 1 ) ) );
        assertThat( job.getEndDate(), is( LocalDate.of( 2026, 8, 31 ) ) );
    }

    private static WordPressDAO recordingDao( List<Job> inserted ) {
        return (WordPressDAO) Proxy.newProxyInstance(
                WordPressDAO.class.getClassLoader(),
                new Class<?>[] { WordPressDAO.class },
                ( proxy, method, args ) -> {
                    if ( "isCloudbeds".equals( method.getName() ) ) {
                        return true;
                    }
                    if ( "insertJob".equals( method.getName() ) ) {
                        inserted.add( (Job) args[0] );
                        return inserted.size();
                    }
                    Class<?> rt = method.getReturnType();
                    if ( rt == boolean.class ) {
                        return false;
                    }
                    if ( rt == int.class || rt == long.class ) {
                        return 0;
                    }
                    if ( rt == void.class ) {
                        return null;
                    }
                    return null;
                } );
    }
}
