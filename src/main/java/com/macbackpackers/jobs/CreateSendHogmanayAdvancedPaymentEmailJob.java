
package com.macbackpackers.jobs;

import com.gargoylesoftware.htmlunit.WebClient;
import com.google.common.collect.ImmutableMap;
import com.macbackpackers.scrapers.CloudbedsScraper;
import com.macbackpackers.services.CloudbedsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;
import java.time.LocalDate;

/**
 * Job that creates individual jobs for sending out emails to guests staying during hogmanay.
 *
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateSendHogmanayAdvancedPaymentEmailJob" )
public class CreateSendHogmanayAdvancedPaymentEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Autowired
    @Transient
    private CloudbedsScraper cloudbedsScraper;

    @Override
    public void processJob() throws Exception {
        try( WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class ) ) {
            final LocalDate DEC_31 = LocalDate.of( LocalDate.now().getYear(), 12, 31 );
            cloudbedsService.createSendTemplatedEmailJobs( webClient, "Hogmanay Advanced Payment",
                    DEC_31, DEC_31, null, null, null, null,"confirmed,not_confirmed",
                    r -> false == r.isChannelCollectBooking() && false == r.isPaid(),
                    r -> ImmutableMap.of(
                            "\\[amount due\\]", cloudbedsScraper.getCurrencyFormat().format( r.getBalanceDue() ),
                            "\\[payment URL\\]", cloudbedsService.generateUniquePaymentURL( r.getReservationId(), null ) ) );
        }
    }
}
