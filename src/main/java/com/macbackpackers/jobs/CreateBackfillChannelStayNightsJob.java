package com.macbackpackers.jobs;

import java.time.LocalDate;
import java.time.YearMonth;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.macbackpackers.beans.JobStatus;

/**
 * Enqueues one {@link SyncChannelStayNightsJob} per calendar month covering
 * {@code start_date} .. {@code end_date} (for historical Channel Production backfill).
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateBackfillChannelStayNightsJob" )
public class CreateBackfillChannelStayNightsJob extends AbstractJob {

    @Override
    public void processJob() throws Exception {
        if ( false == dao.isCloudbeds() ) {
            return;
        }
        LocalDate start = getStartDate();
        LocalDate end = getEndDate();
        if ( end.isBefore( start ) ) {
            throw new IllegalArgumentException( "end_date before start_date" );
        }

        YearMonth cursor = YearMonth.from( start );
        YearMonth last = YearMonth.from( end );
        int created = 0;
        while ( false == cursor.isAfter( last ) ) {
            LocalDate monthStart = cursor.atDay( 1 );
            LocalDate monthEnd = cursor.atEndOfMonth();
            if ( monthStart.isBefore( start ) ) {
                monthStart = start;
            }
            if ( monthEnd.isAfter( end ) ) {
                monthEnd = end;
            }

            SyncChannelStayNightsJob job = new SyncChannelStayNightsJob();
            job.setStatus( JobStatus.submitted );
            job.setStartDate( monthStart );
            job.setEndDate( monthEnd );
            dao.insertJob( job );
            created++;
            LOGGER.info( "Enqueued SyncChannelStayNightsJob for {} .. {}", monthStart, monthEnd );
            cursor = cursor.plusMonths( 1 );
        }
        LOGGER.info( "CreateBackfillChannelStayNightsJob created {} monthly sync job(s)", created );
    }

    public LocalDate getStartDate() {
        return LocalDate.parse( getParameter( "start_date" ) );
    }

    public void setStartDate( LocalDate startDate ) {
        setParameter( "start_date", startDate.toString() );
    }

    public LocalDate getEndDate() {
        return LocalDate.parse( getParameter( "end_date" ) );
    }

    public void setEndDate( LocalDate endDate ) {
        setParameter( "end_date", endDate.toString() );
    }
}
