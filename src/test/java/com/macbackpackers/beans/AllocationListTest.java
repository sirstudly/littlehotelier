package com.macbackpackers.beans;

import java.math.BigDecimal;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllocationListTest {

    final Logger LOGGER = LoggerFactory.getLogger( getClass() );
    static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );

    @Test
    public void testGetBulkInsertStatement() throws Exception {
        Allocation alloc = new Allocation();
        alloc.setJobId( 2 );
        alloc.setRoomId( "15" );
        alloc.setRoomTypeId( 8 );
        alloc.setRoom( "the room" );
        alloc.setBedName( "the bed" );
        alloc.setReservationId( 4 );
        alloc.setBookingReference( "MY-1234567894" );
        alloc.setBookedDate( DATE_FORMAT_YYYY_MM_DD.parse( "2014-04-21" ) );
        alloc.setCheckinDate( DATE_FORMAT_YYYY_MM_DD.parse( "2014-05-01" ) );
        alloc.setCheckoutDate( DATE_FORMAT_YYYY_MM_DD.parse( "2014-05-03" ) );
        alloc.setBookingSource( "MyAllocator" );
        alloc.setGuestName( "Rick Sanchez" );
        alloc.setPaymentOutstanding( new BigDecimal( "14.22" ) );
        alloc.setPaymentTotal( new BigDecimal( "28.81" ) );
        alloc.setViewed( true );
        LOGGER.info( alloc.getAsParameters().length + " parameters." );
        LOGGER.info( ToStringBuilder.reflectionToString( alloc.getAsParameters() ) );

        AllocationList al = new AllocationList();
        al.add( alloc );
        al.add( alloc );
        LOGGER.info( al.getBulkInsertStatement() );
    }
}
