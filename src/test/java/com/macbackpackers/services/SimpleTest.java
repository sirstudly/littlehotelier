
package com.macbackpackers.services;

import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;

public class SimpleTest {

    private static final Logger LOGGER = LoggerFactory.getLogger( SimpleTest.class );
    public static final FastDateFormat DATE_FORMAT_BOOKED_DATE = FastDateFormat.getInstance( "d MMM yy HH:mm:ss" );
    public static final SimpleDateFormat DATE_FORMAT_SIMPLE = new SimpleDateFormat( "d'th' MMM 'x''o'yy HH:mm:ss" );

    private Gson gson = new GsonBuilder()
            .setFieldNamingPolicy( FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES )
            .setPrettyPrinting()
            .create();

    @Test
    public void testReplace() throws Exception {

        // 2015-07-19 06:10:49 DEBUG HostelworldScraper:190 - Booked: 30th Jun '15 17:16:29
        // 2015-07-19 06:10:49 DEBUG HostelworldScraper:193 - Source: Hostelworld
        // 2015-07-19 06:10:49 DEBUG HostelworldScraper:196 - Arriving: 19th Jul '15
        // 2015-07-19 06:10:49 DEBUG HostelworldScraper:199 - Arrival Time: 15.00
        LOGGER.info( convertHostelworldDate( "9th Jun '15 17:16:29" ).toString() );
        LOGGER.info( convertHostelworldDate( "1st Jun '15 17:16:29" ).toString() );

        String testString = "1st Jun '15 17:16:29";
        Pattern p = Pattern.compile( "([\\d]+)[a-zA-Z]+ ([a-zA-Z]+) '([\\d]+) ([\\d]{2}:[\\d]{2}:[\\d]{2})" );
        Matcher m = p.matcher( testString );
        while ( m.find() ) {
            System.out.println( "date: " + m.group( 1 ) );
            System.out.println( "month: " + m.group( 2 ) );
            System.out.println( "year: " + m.group( 3 ) );
            System.out.println( "time: " + m.group( 4 ) );
        }
    }

    private Date convertHostelworldDate( String dateAsString ) throws ParseException {
        Pattern p = Pattern.compile( "([\\d]+)[a-zA-Z]+ ([a-zA-Z]+) '([\\d]+) ([\\d]{2}:[\\d]{2}:[\\d]{2})" );
        Matcher m = p.matcher( dateAsString );
        while ( m.find() ) {
            return DATE_FORMAT_BOOKED_DATE.parse( m.group( 1 ) + " " + m.group( 2 ) + " " + m.group( 3 ) + " " + m.group( 4 ) );
        }
        throw new ParseException( "Unable to convert " + dateAsString, 0 );
    }

    @Test
    public void testParseCSV() throws IOException {
        String csv = "Booking reference,Booked on date,Property name,Channel name,Promotion code,Guest first name,Guest last name,Guest email,Guest phone number,Guest organisation,Guest address,Guest address2,Guest city,Guest state,Guest country,Guest post code,Check in date,Check out date,Length of Stay (nights),Arrival time,Guest comments,Requested newsletter,Status,Subtotal amount,Extra adult amount,Extra child amount,Extra infant amount,Extras total amount,Credit card surcharge processed amount,Surcharge percentage,Promotional Discount,Payment total,Payment Received,Number of adults,Number of children,Number of infants,Number of Rooms,Custom Property Specific Data,Referral,Payments deposit processed total,Payment outstanding,Mobile booking?,Promotion Description,Enter rates including fees,Fixed Taxes Total,Percentage Taxes Total\r\nEXP-719896551,2016-08-29,Castle Rock Hostel,Expedia,\"\",Nicole,Bahr,nicolebaehr88@gmail.com,49 176 30354397,\"\",\"\",\"\",\"\",\"\",\"\",\"\",2016-10-06,2016-10-13,7,\"\",\"Hotel Collect Booking  Collect Payment From Guest, Non-Smoking, I ll arrive at the airport at 11:00pm, so I ll check-in between 12:00 and 12:30am, 1 bed\",\"\",checked-in,93.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,93.0,93.0,1,0,0,1,\"\",\"\",13.0,0.0,false,\"\",true,0,15.5";
        Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse( new StringReader( csv ) );
        for ( CSVRecord record : records ) {
            String comment = record.get( "Guest comments" );
            LOGGER.info( comment );
        }
    }

    @Test
    public void testFormatDate() throws Exception {
        LOGGER.info( FastDateFormat.getInstance( "dd/MM/yyyy HH:mm:ss" ).format( new Date() ) );
    }

    @Test
    public void testRegex() throws Exception {
        Pattern p = Pattern.compile( "Reservation has a cancellation grace period. Do not charge if cancelled before (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})" );
        Matcher m = p.matcher( "Reservation has a cancellation grace period. Do not charge if cancelled before 2017-08-18 00:51:18\nApproximate time of arrival: between 17:00 and 18:00\nHello," );
        if ( m.find() ) {
            Date d = FastDateFormat.getInstance( "yyyy-MM-dd HH:mm:ss" ).parse( m.group( 1 ) );
            LOGGER.info( "Customer needs to pay on " + d );
        }
        else {
            throw new NumberFormatException( "Unable to find reservation id from data href " );
        }
    }

    @Test
    public void testStringReplace() throws Exception {
        LOGGER.info( "dlkjfsd123456789012345678lkjdlf".replaceAll( "[0-9]{16}", "XXXXXXXXXXXXXXXX" ) );
    }

    @Test
    public void testBigDecimal() {

        DecimalFormat df = new DecimalFormat( "###0.00" );
        LOGGER.info( df.format( new BigDecimal( "13.2" ) ) );
        LOGGER.info( df.format( new BigDecimal( "13" ) ) );
        LOGGER.info( df.format( new BigDecimal( "13.25" ) ) );
        LOGGER.info( df.format( new BigDecimal( "0.2" ) ) );
    }

    @Test
    public void testCompare() {
        LOGGER.info( "Compare: " + new BigDecimal( "2" ).compareTo( BigDecimal.ZERO ) );
    }

    @Test
    public void testOptional() {
        String bedName = "blah";
        LOGGER.info( "15" + Optional.ofNullable( bedName ).map( n -> new String( ": " + n ) ).orElse( "" ) );
    }

    @Test
    public void testGsonLoad() throws Exception {

        Optional<JsonObject> rpt = Optional.ofNullable( gson.fromJson(
                new FileReader( getClass().getClassLoader().getResource( "room_assignments_report.json" ).getPath() ), JsonObject.class ) );

        List<String> staffBeds = rpt.get()
                .get( "rooms" ).getAsJsonObject()
                .get( "2018-06-17" ).getAsJsonObject()
                .entrySet().stream() // now streaming room types...
                .flatMap( e -> e.getValue().getAsJsonObject()
                        .get( "rooms" ).getAsJsonObject()
                        .entrySet().stream() ) // now streaming beds
                // only need beds where type = "Blocked" or "Out of Service"
                .filter( e -> StreamSupport.stream( e.getValue().getAsJsonArray().spliterator(), false )
                        .anyMatch( x -> x.getAsJsonObject().has( "type" )
                                && Arrays.asList( "Blocked Dates", "Out of Service" ).contains( x.getAsJsonObject().get( "type" ).getAsString() ) ) )
                .map( e -> e.getKey() )
                .collect( Collectors.toList() );
        LOGGER.info( StringUtils.join( staffBeds, "," ) );
    }

    @Test
    public void testLoadReservation() throws Exception {

        String json = StreamUtils.copyToString( getClass().getClassLoader().getResourceAsStream(
                "get_reservation_cloudbeds.json" ), StandardCharsets.UTF_8 );
        Reservation r = gson.fromJson( json, Reservation.class );

        Assert.assertEquals( "First name matched", "John", r.getFirstName() );

        // need to parse the credit_cards object manually to check for presence
        JsonObject rootElem = gson.fromJson( json, JsonObject.class );
        JsonElement creditCardsElem = rootElem.get( "credit_cards" );
        Assert.assertEquals( "Credit card object exists", 3, creditCardsElem.getAsJsonObject().entrySet().size() );
        
        String cardId = null;
        for ( Iterator<Entry<String, JsonElement>> it = creditCardsElem.getAsJsonObject().entrySet().iterator() ; it.hasNext() ; ) {
            cardId = it.next().getKey();
        }
        Assert.assertEquals( "Card ID should be the last one", "5819432", cardId );

    }

}
