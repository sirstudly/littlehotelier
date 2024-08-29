
package com.macbackpackers.jobs;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.macbackpackers.services.CloudbedsService;

@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateFixedRateLongTermReservationsJob" )
public class CreateFixedRateLongTermReservationsJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean( "webClientForCloudbeds", WebClient.class )) {
            cloudbedsService.createFixedRateLongTermReservations( webClient,
                    getSelectedDate(), getDays(), getRatePerDay() );
        }
    }

    public LocalDate getSelectedDate() {
        return LocalDate.parse( getParameter( "selected_date" ) );
    }

    public void setSelectedDate( LocalDate selectedDate ) {
        setParameter( "selected_date", selectedDate.format( DateTimeFormatter.ISO_LOCAL_DATE ) );
    }

    public int getDays() {
        return Integer.parseInt( getParameter( "days" ) );
    }

    public void setDays( int days ) {
        setParameter( "days", String.valueOf( days ) );
    }

    public void setRatePerDay( BigDecimal ratePerDay ) {
        setParameter( "rate_per_day", new DecimalFormat( "###0.##" ).format( ratePerDay ) );
    }

    public BigDecimal getRatePerDay() {
        return new BigDecimal( getParameter( "rate_per_day" ) );
    }
}
