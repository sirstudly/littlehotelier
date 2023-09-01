
package com.macbackpackers.jobs;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import com.macbackpackers.beans.JobStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.scrapers.AllocationsPageScraper;
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
    private AllocationsPageScraper allocationScraper;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    private ApplicationContext ctx;

    @Transient
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
            cloudbedsService.dumpAllocationsFrom( getWebClient(),
                    getId(), selectedDate.minusDays( 1 ), selectedDate.plusDays( 1 ) );

            // aggregates data from above
            BedCountReportJob j = new BedCountReportJob();
            j.setStatus( JobStatus.submitted );
            j.setBedCountJobId( getId() );
            j.setSelectedDate( selectedDate );
            dao.insertJob( j );
        }
        else {
            // we just need to scrape the data including the given date
            // the PHP form will do the rest
            Date selectedDate = getSelectedDate();
            Calendar dayBefore = Calendar.getInstance();
            dayBefore.setTime( selectedDate );
            dayBefore.add( Calendar.DATE, -7 ); // go back a week
            allocationScraper.dumpAllocationsFrom( getWebClient(), getId(), dayBefore.getTime() );
        }
    }

    /**
     * Gets the date to start scraping the allocation data (inclusive).
     * 
     * @return non-null date parameter
     * @throws ParseException
     */
    private Date getSelectedDate() throws ParseException {
        return AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.parse( getParameter( "selected_date" ) );
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

    private WebClient getWebClient() {
        if ( webClient == null ) {
            webClient = ctx.getBean( dao.isCloudbeds() ? "webClientForCloudbeds" : "webClientForCloudbeds",
                    WebClient.class );
        }
        return webClient;
    }

}
