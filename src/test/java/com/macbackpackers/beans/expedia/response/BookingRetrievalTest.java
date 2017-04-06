
package com.macbackpackers.beans.expedia.response;

import java.io.FileReader;
import java.math.BigDecimal;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

import com.macbackpackers.beans.expedia.response.BookingRetrievalRS;

public class BookingRetrievalTest {

    @Test
    public void testDeserialise() throws Exception {
        JAXBContext context = JAXBContext.newInstance( BookingRetrievalRS.class );
        Unmarshaller m = context.createUnmarshaller();
        BookingRetrievalRS rec = (BookingRetrievalRS) m.unmarshal( new FileReader(
                getClass().getResource( "/BookingRetrievalRS.xml" ).getFile() ) );

        assertThat( rec, is( notNullValue() ) );
        assertThat( rec.getBookings(), is( notNullValue() ) );
        assertThat( rec.getBookings().getBookingList(), is( notNullValue() ) );
        assertThat( rec.getBookings().getBookingList().isEmpty(), is( false ) );

        Booking booking = rec.getBookings().getBookingList().get( 0 );
        assertThat( booking.getId(), is( "818593204" ) );
        assertThat( booking.getType(), is( "Book" ) );
        assertThat( booking.getCreateDateTime(), is( "2017-04-01T08:18:00Z" ) );
        assertThat( booking.getSource(), is( "A-Expedia" ) );
        assertThat( booking.getStatus(), is( "confirmed" ) );
        assertThat( booking.getConfirmNumber(), is( "EXP-818593204" ) );
        assertThat( booking.getHotel().getId(), is( "9741780" ) );

        RoomStay roomStay = booking.getRoomStay();
        assertThat( roomStay, is( notNullValue() ) );
        assertThat( roomStay.getRoomTypeID(), is( "200878754" ) );
        assertThat( roomStay.getRatePlanID(), is( "204461494A" ) );
        assertThat( roomStay.getStayDate().getArrival(), is( "2017-04-21" ) );
        assertThat( roomStay.getStayDate().getDeparture(), is( "2017-04-23" ) );
        assertThat( roomStay.getGuestCount().getAdult(), is( 1 ) );

        assertThat( roomStay.getPerDayRates().getCurrency(), is( "GBP" ) );
        List<PerDayRate> rates = roomStay.getPerDayRates().getRates();
        assertThat( rates.size(), is( 2 ) );
        assertThat( rates.get( 0 ).getStayDate(), is( "2017-04-21" ) );
        assertThat( rates.get( 0 ).getBaseRate(), is( new BigDecimal( "8.00" ) ) );
        assertThat( rates.get( 1 ).getStayDate(), is( "2017-04-22" ) );
        assertThat( rates.get( 1 ).getBaseRate(), is( new BigDecimal( "6.00" ) ) );

        assertThat( roomStay.getTotal(), is( notNullValue() ) );
        assertThat( roomStay.getTotal().getAmountAfterTaxes(), is( new BigDecimal( "14.00" ) ) );
        assertThat( roomStay.getTotal().getCurrency(), is( "GBP" ) );

        PaymentCard pc = roomStay.getPaymentCard();
        assertThat( pc, is( notNullValue() ) );
        assertThat( pc.getCardCode(), is( "MC" ) );
        assertThat( pc.getCardNumber(), is( "1234567890123456" ) );
        assertThat( pc.getSeriesCode(), is( "345" ) );
        assertThat( pc.getExpireDate(), is( "1120" ) );

        CardHolder ch = pc.getCardHolder();
        assertThat( ch, is( notNullValue() ) );
        assertThat( ch.getName(), is( "lorena fonti" ) );
        assertThat( ch.getAddress(), is( "Any street1 Any street2" ) );
        assertThat( ch.getCity(), is( "Any city" ) );
        assertThat( ch.getStateProv(), is( "MA" ) );
        assertThat( ch.getCountry(), is( "US" ) );
        assertThat( ch.getPostalCode(), is( "98004" ) );

        assertThat( booking.getPrimaryGuest(), is( notNullValue() ) );
        assertThat( booking.getPrimaryGuest().getName().getGivenName(), is( "lorena" ) );
        assertThat( booking.getPrimaryGuest().getName().getSurname(), is( "fonti" ) );
        assertThat( booking.getPrimaryGuest().getEmail(), is( "lorena.fonti@test.it" ) );
    }
}
