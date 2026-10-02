package com.macbackpackers.scrapers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.macbackpackers.beans.Job;
import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.jobs.BookingAssignmentEnrichJob;

public class CloudbedsScraperAddNoteEnrichTest {

    private final WordPressDAO dao = mock( WordPressDAO.class );

    private CloudbedsScraper scraper() {
        CloudbedsScraper scraper = new CloudbedsScraper();
        ReflectionTestUtils.setField( scraper, "dao", dao );
        return scraper;
    }

    @Test
    public void queuesEnrichJobWhenNonePending() {
        when( dao.hasBookingAssignmentEnrichJobForReservation( "187752689" ) ).thenReturn( false );

        ReflectionTestUtils.invokeMethod( scraper(), "queueBookingAssignmentEnrich", "187752689" );

        ArgumentCaptor<Job> job = ArgumentCaptor.forClass( Job.class );
        verify( dao ).insertJob( job.capture() );
        BookingAssignmentEnrichJob enrich = (BookingAssignmentEnrichJob) job.getValue();
        assertThat( enrich.getReservationId(), is( "187752689" ) );
        assertThat( enrich.getStatus(), is( JobStatus.submitted ) );
    }

    @Test
    public void skipsWhenEnrichJobAlreadyPending() {
        when( dao.hasBookingAssignmentEnrichJobForReservation( "187752689" ) ).thenReturn( true );

        ReflectionTestUtils.invokeMethod( scraper(), "queueBookingAssignmentEnrich", "187752689" );

        verify( dao, never() ).insertJob( any() );
    }
}
