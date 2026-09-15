package com.macbackpackers.jobs;

import java.text.ParseException;
import java.time.LocalDate;
import java.util.Date;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

import com.macbackpackers.config.LittleHotelierConfig;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.OccupancyReconcileService;

/**
 * Reconciles {@code wp_lh_occupancy} from Cloudbeds REST for the selected date and recomputes
 * the realtime housekeeping bedsheet projection. Does not write {@code wp_lh_calendar}.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.HousekeepingJob" )
public class HousekeepingJob extends AbstractJob {

    @Autowired
    @Transient
    private OccupancyReconcileService occupancyReconcileService;

    @Autowired
    @Transient
    private ApplicationContext ctx;

    @Transient
    private WebClient webClient;

    @Override
    public void resetJob() throws Exception {
        // Occupancy is SCD2; reconcile upserts/closes versions. No calendar wipe.
    }

    @Override
    public void processJob() throws Exception {
        if ( dao.isCloudbeds() ) {
            LocalDate selectedDate = getSelectedLocalDate();
            occupancyReconcileService.reconcileFromCloudbeds( getWebClient(), selectedDate );
        }
    }

    @Override
    public void finalizeJob() {
        webClient.close(); // cleans up JS threads
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

    private WebClient getWebClient() {
        if ( webClient == null ) {
            webClient = ctx.getBean( "webClientForCloudbeds", WebClient.class );
        }
        return webClient;
    }

}
