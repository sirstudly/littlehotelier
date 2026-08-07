package com.macbackpackers.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.config.PropertiesFactoryBean;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.macbackpackers.beans.Allocation;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Verifies unpaid-deposit report inputs: folio EVL snapshot onto calendar rows,
 * and hotel-collect / EVL filters in report SQL.
 */
public class UnpaidDepositReportCriteriaTest {

    private Gson gson;
    private String unpaidDepositReportSql;

    @BeforeEach
    public void setUp() throws Exception {
        gson = new GsonBuilder()
                .setFieldNamingPolicy( FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES )
                .create();

        PropertiesFactoryBean factory = new PropertiesFactoryBean();
        factory.setLocation( new ClassPathResource( "report_sql.xml" ) );
        factory.afterPropertiesSet();
        Properties sql = factory.getObject();
        unpaidDepositReportSql = sql.getProperty( "unpaid.deposit.report" );
    }

    @Test
    public void testAllocationMapsVisitorLevyFromReservationFolio() throws IOException {
        String json = IOUtils.toString(
                getClass().getClassLoader().getResourceAsStream( "get_reservation_with_evl.json" ),
                StandardCharsets.UTF_8 );
        Reservation reservation = gson.fromJson( json, Reservation.class );

        BigDecimal visitorLevy = EdinburghVisitorLevyCalculator.getVisitorLevyTotal( reservation );

        Allocation allocation = new Allocation();
        allocation.setPaymentOutstanding( reservation.getBalanceDue() );
        allocation.setPaymentTotal( reservation.getGrandTotal() );
        allocation.setVisitorLevyTotal( visitorLevy );
        allocation.setBookingSource( reservation.getSourceName() );

        assertThat( visitorLevy, comparesEqualTo( new BigDecimal( "2.40" ) ) );
        assertThat( allocation.getVisitorLevyTotal(), comparesEqualTo( new BigDecimal( "2.40" ) ) );
        // outstanding equals grand total on this fixture; still greater than EVL alone
        assertThat( allocation.getPaymentOutstanding().compareTo( allocation.getVisitorLevyTotal() ) > 0, is( true ) );
    }

    @Test
    public void testUnpaidDepositReportSqlUsesBareSourcesHotelCollectAndEvlFilter() {
        assertThat( unpaidDepositReportSql,
                containsString( "payment_outstanding > COALESCE(f.visitor_levy_total, 0)" ) );
        assertThat( unpaidDepositReportSql, containsString( "hotel_collect_yn = 'Y'" ) );

        // reservation detail API stores bare source names
        assertThat( unpaidDepositReportSql, containsString( "IN ('Booking.com', 'Expedia')" ) );
        assertThat( unpaidDepositReportSql,
                containsString( "IN ('Hostelworld & Hostelbookers', 'Booking.com', 'Hostelworld')" ) );
        assertThat( unpaidDepositReportSql,
                containsString( "rate_plan_name LIKE '%Non-refundable%' OR f.rate_plan_name = 'nonref'" ) );

        assertThat( unpaidDepositReportSql, not( containsString( "Hotel Collect Booking" ) ) );
        assertThat( unpaidDepositReportSql, not( containsString( "Channel Collect Booking" ) ) );
    }

    @Test
    public void testAllocationMapsHotelCollectFromReservation() throws IOException {
        String json = IOUtils.toString(
                getClass().getClassLoader().getResourceAsStream( "get_reservation_cloudbeds_hotel_collect.json" ),
                StandardCharsets.UTF_8 );
        Reservation reservation = gson.fromJson( json, Reservation.class );

        Allocation allocation = new Allocation();
        allocation.setBookingSource( reservation.getSourceName() );
        allocation.setHotelCollect( reservation.isHotelCollectBooking() );

        assertThat( allocation.getBookingSource(), is( "Booking.com" ) );
        assertThat( allocation.isHotelCollect(), is( true ) );
    }
}
