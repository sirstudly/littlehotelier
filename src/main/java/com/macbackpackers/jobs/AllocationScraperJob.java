package com.macbackpackers.jobs;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.config.LittleHotelierConfig;
import com.macbackpackers.services.BookingAssignmentEnrichService;

/**
 * Heals {@code wp_lh_booking_assignment} via selective REST, dual-writes currents into
 * {@code wp_lh_calendar} under this job id, then queues allocation reports.
 * <p>
 * Replaces the former full ~140-day {@code get_reservation} dump via worker jobs.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.AllocationScraperJob" )
public class AllocationScraperJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient webClient;

    @Autowired
    @Transient
    private BookingAssignmentEnrichService enrichService;

    @Override
    public void processJob() throws Exception {
        LocalDate start = LocalDate.now();
        LocalDate end = getEndLocalDate();
        enrichService.healAndDualWrite( webClient, getId(), start, end );
        insertCreateReportsJob();
    }

    @Override
    public void finalizeJob() {
        webClient.close();
    }

    @Override
    public void resetJob() throws Exception {
        dao.deleteAllocations( getId() );
    }

    private void insertCreateReportsJob() {
        CreateAllocationScraperReportsJob job = new CreateAllocationScraperReportsJob();
        job.setStatus( JobStatus.submitted );
        job.setAllocationScraperJobId( getId() );
        dao.insertJob( job );
    }

    public Date getStartDate() throws ParseException {
        return LittleHotelierConfig.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "start_date" ) );
    }

    public void setStartDate( Date startDate ) {
        setParameter( "start_date", LittleHotelierConfig.DATE_FORMAT_YYYY_MM_DD.format( startDate ) );
    }

    public int getDaysAhead() {
        return Integer.parseInt( getParameter( "days_ahead" ) );
    }

    public void setDaysAhead( int daysAhead ) {
        if ( daysAhead < 0 ) {
            throw new IllegalArgumentException( "Days ahead must be non-negative" );
        }
        setParameter( "days_ahead", String.valueOf( daysAhead ) );
    }

    public Date getEndDate() throws ParseException {
        Calendar cal = Calendar.getInstance();
        cal.setTime( getStartDate() );
        cal.add( Calendar.DATE, getDaysAhead() );
        return cal.getTime();
    }

    public LocalDate getEndLocalDate() {
        return LocalDate.now().plusDays( getDaysAhead() );
    }

}
