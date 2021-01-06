package com.macbackpackers.jobs;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.CloudbedsService;

@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.CreateFixedRateReservationJob" )
public class CreateFixedRateReservationJob extends AbstractJob {

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {
        cloudbedsService.createFixedRateReservation(
                getReservationId(), getCheckinDate(), getCheckoutDate(), getRatePerDay() );
    }

    public String getReservationId() {
        return getParameter( "reservation_id" );
    }

    public void setReservationId( String reservationId ) {
        setParameter( "reservation_id", reservationId );
    }

    public void setCheckinDate( LocalDate checkinDate ) {
        setParameter( "checkin_date", checkinDate.format( DateTimeFormatter.ISO_LOCAL_DATE ) );
    }

    public LocalDate getCheckinDate() {
        return LocalDate.parse( getParameter( "checkin_date" ) );
    }

    public void setCheckoutDate( LocalDate checkoutDate ) {
        setParameter( "checkout_date", checkoutDate.format( DateTimeFormatter.ISO_LOCAL_DATE ) );
    }

    public LocalDate getCheckoutDate() {
        return LocalDate.parse( getParameter( "checkout_date" ) );
    }

    public void setRatePerDay( BigDecimal ratePerDay ) {
        setParameter( "rate_per_day", new DecimalFormat( "###0.##" ).format( ratePerDay ) );
    }

    public BigDecimal getRatePerDay() {
        return new BigDecimal( getParameter( "rate_per_day" ) );
    }
}
