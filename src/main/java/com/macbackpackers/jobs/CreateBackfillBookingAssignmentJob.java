package com.macbackpackers.jobs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import org.apache.commons.lang3.StringUtils;

import com.macbackpackers.beans.JobStatus;

/**
 * One-off: queues a {@link BackfillBookingAssignmentJob} per 7-day check-in window between
 * start_date (default 2024-01-01) and end_date (default yesterday). The weekly jobs are independent.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateBackfillBookingAssignmentJob" )
public class CreateBackfillBookingAssignmentJob extends AbstractJob {

    static final LocalDate DEFAULT_START_DATE = LocalDate.of( 2024, 1, 1 );

    @Override
    public void processJob() throws Exception {
        List<LocalDate[]> weeks = weeklyWindows( getStartDate(), getEndDate() );
        for ( LocalDate[] week : weeks ) {
            BackfillBookingAssignmentJob job = new BackfillBookingAssignmentJob();
            job.setStatus( JobStatus.submitted );
            job.setStartDate( week[0] );
            job.setEndDate( week[1] );
            dao.insertJob( job );
        }
        LOGGER.info( "Queued {} BackfillBookingAssignmentJob(s) for {} - {}", weeks.size(), getStartDate(), getEndDate() );
    }

    /**
     * Consecutive inclusive [start, end] windows of 7 days; the last is clipped to {@code endDate}.
     */
    static List<LocalDate[]> weeklyWindows( LocalDate startDate, LocalDate endDate ) {
        List<LocalDate[]> weeks = new ArrayList<>();
        for ( LocalDate from = startDate ; false == from.isAfter( endDate ) ; from = from.plusDays( 7 ) ) {
            LocalDate to = from.plusDays( 6 );
            weeks.add( new LocalDate[] { from, to.isAfter( endDate ) ? endDate : to } );
        }
        return weeks;
    }

    public LocalDate getStartDate() {
        String value = getParameter( "start_date" );
        return StringUtils.isBlank( value ) ? DEFAULT_START_DATE : LocalDate.parse( value );
    }

    public void setStartDate( LocalDate startDate ) {
        setParameter( "start_date", startDate.toString() );
    }

    public LocalDate getEndDate() {
        String value = getParameter( "end_date" );
        return StringUtils.isBlank( value ) ? LocalDate.now().minusDays( 1 ) : LocalDate.parse( value );
    }

    public void setEndDate( LocalDate endDate ) {
        setParameter( "end_date", endDate.toString() );
    }
}
