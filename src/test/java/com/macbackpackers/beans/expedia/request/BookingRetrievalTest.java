package com.macbackpackers.beans.expedia.request;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.StringWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;

import org.junit.Test;

import com.macbackpackers.beans.expedia.Hotel;

public class BookingRetrievalTest {

    @Test
    public void testSerialise() throws Exception {
        BookingRetrievalRQ testReq = new BookingRetrievalRQ();
        testReq.setAuthentication( new Authentication() );
        testReq.getAuthentication().setUsername( "USER123456" );
        testReq.getAuthentication().setPassword( "PASS123" );
        testReq.setHotel( new Hotel( "HOTEL123456" ) );
        testReq.setParamSet( new ParamSet() );
        testReq.getParamSet().setBooking( new Booking( "9988877766" ) );
        
        StringWriter writer = new StringWriter();
        JAXBContext context = JAXBContext.newInstance(BookingRetrievalRQ.class);
        Marshaller m = context.createMarshaller();
        m.marshal(testReq, writer);
        
        assertThat( writer.toString(), is("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><BookingRetrievalRQ xmlns=\"http://www.expediaconnect.com/EQC/BR/2014/01\"><Authentication username=\"USER123456\" password=\"PASS123\"/><Hotel id=\"HOTEL123456\"/><ParamSet><Booking id=\"9988877766\"/></ParamSet></BookingRetrievalRQ>"));
    }
}
