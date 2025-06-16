
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import com.macbackpackers.beans.JobStatus;
import com.macbackpackers.config.LittleHotelierConfig;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.macbackpackers.services.CloudbedsService;

/**
 * Job that updates the data required for the bedcount report for the given date
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.BedCountJob" )
public class BedCountJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    @Qualifier( "webClientForCloudbeds" )
    private WebClient webClient;

    @Override
    public void resetJob() throws Exception {
        dao.deleteAllocations( getId() );
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
    }

    @Override
    public void processJob() throws Exception {
        if ( dao.isCloudbeds() ) {
            LocalDate selectedDate = getSelectedLocalDate();
            cloudbedsService.dumpAllocationsFrom( webClient,
                    getId(), selectedDate.minusDays( 1 ), selectedDate.plusDays( 1 ) );

            // aggregates data from above
            BedCountReportJob j = new BedCountReportJob();
            j.setStatus( JobStatus.submitted );
            j.setBedCountJobId( getId() );
            j.setSelectedDate( selectedDate );
            dao.insertJob( j );
        }
    }

    /**
     * Gets the date to start scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    private Date getSelectedDate() throws ParseException {
        return LittleHotelierConfig.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "selected_date" ) );
    }

    /**
     * Gets the date to start scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    private LocalDate getSelectedLocalDate() {
        return LocalDate.parse( getParameter( "selected_date" ).substring( 0, 10 ) );
    }

}
