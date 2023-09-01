
package com.macbackpackers.jobs;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Job that aggregates the data for the bedcount report from the {@link BedCountJob}
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BedCountReportJob" )
public class BedCountReportJob extends AbstractJob {

    public void processJob() throws Exception {
        dao.runBedCountsReport( getBedCountJobId(), getSelectedDate() );
    }

    public int getBedCountJobId() {
        return Integer.parseInt( getParameter( "job_id" ) );
    }

    public void setBedCountJobId( int jobId ) {
        setParameter( "job_id", String.valueOf( jobId ) );
    }

    public LocalDate getSelectedDate() {
        return LocalDate.parse( getParameter( "selected_date" ) );
    }

    public void setSelectedDate( LocalDate selectedDate ) {
        setParameter( "selected_date", selectedDate.format( DateTimeFormatter.ISO_LOCAL_DATE ) );
    }
}
