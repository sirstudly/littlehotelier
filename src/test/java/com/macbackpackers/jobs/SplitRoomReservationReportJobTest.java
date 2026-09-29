package com.macbackpackers.jobs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.macbackpackers.beans.ConsecutiveBookingPair;
import com.macbackpackers.beans.Job;
import com.macbackpackers.dao.WordPressDAO;

public class SplitRoomReservationReportJobTest {

    private final WordPressDAO dao = mock( WordPressDAO.class );

    @Test
    public void runsReportThenQueuesHintJobsForBothTables() throws Exception {
        when( dao.fetchSplitRoomReservationIdsForShuffleHints( 5 ) ).thenReturn( List.of( 11L ) );
        when( dao.fetchConsecutiveBookingPairsForShuffleHints( 5 ) ).thenReturn( List.of( new ConsecutiveBookingPair( 21L, 22L, 7 ) ) );

        SplitRoomReservationReportJob job = new SplitRoomReservationReportJob();
        job.setAllocationScraperJobId( 5 );
        ReflectionTestUtils.setField( job, "dao", dao );
        job.processJob();

        InOrder order = inOrder( dao );
        order.verify( dao ).runSplitRoomsReservationsReport( 5 );
        ArgumentCaptor<Job> jobs = ArgumentCaptor.forClass( Job.class );
        order.verify( dao, times( 2 ) ).insertJob( jobs.capture() );

        BedShuffleHintJob single = (BedShuffleHintJob) jobs.getAllValues().get( 0 );
        assertThat( single.getAllocationScraperJobId(), is( 5 ) );
        assertThat( single.getReservationId(), is( 11L ) );
        assertThat( single.getNextReservationId(), nullValue() );

        BedShuffleHintJob pair = (BedShuffleHintJob) jobs.getAllValues().get( 1 );
        assertThat( pair.getAllocationScraperJobId(), is( 5 ) );
        assertThat( pair.getReservationId(), is( 21L ) );
        assertThat( pair.getNextReservationId(), is( 22L ) );
        assertThat( pair.getRoomTypeId(), is( 7 ) );
    }
}
