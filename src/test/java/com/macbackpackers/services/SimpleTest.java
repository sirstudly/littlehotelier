package com.macbackpackers.services;

import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.mail.internet.MimeUtility;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.cloudbeds.responses.ActivityLogEntry;
import com.macbackpackers.beans.cloudbeds.responses.EmailTemplateInfo;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals( "John", r.getFirstName(), "First name matched" );

        // need to parse the credit_cards object manually to check for presence
        JsonObject rootElem = gson.fromJson( json, JsonObject.class );
        JsonElement creditCardsElem = rootElem.get( "credit_cards" );
        assertEquals( 3, creditCardsElem.getAsJsonObject().entrySet().size(), "Credit card object exists" );

        String cardId = null;
        for ( Iterator<Entry<String, JsonElement>> it = creditCardsElem.getAsJsonObject().entrySet().iterator(); it.hasNext(); ) {
            cardId = it.next().getKey();
        }
        assertEquals( "5819432", cardId, "Card ID should be the last one" );

        // first night is accumulated correctly
        assertEquals( new BigDecimal( "27.58" ), r.getRateFirstNight( gson ) );
    }

    @Test
    public void testGetActivityLog() throws Exception {

        final DateTimeFormatter DD_MM_YYYY_HH_MM = DateTimeFormatter.ofPattern( "dd/MM/yyyy hh:mm a", new Locale( "en" ) );
        String json = StreamUtils.copyToString( getClass().getClassLoader().getResourceAsStream(
                "activity_log.json" ), StandardCharsets.UTF_8 );
        JsonElement rootElem = gson.fromJson( json, JsonElement.class );
        JsonArray logArray = rootElem.getAsJsonObject().get( "aaData" ).getAsJsonArray();
        List<ActivityLogEntry> logEntries = new ArrayList<ActivityLogEntry>();
        logArray.forEach( e -> {
            ActivityLogEntry ent = new ActivityLogEntry();
            try {
                ent.setCreatedDate( LocalDateTime.parse( e.getAsJsonArray().get( 0 ).getAsString(), DD_MM_YYYY_HH_MM ) );
                ent.setCreatedBy( e.getAsJsonArray().get( 1 ).getAsString() );
            }
            catch ( DateTimeParseException ex ) {
                throw new RuntimeException( "Failed to parse activity log entry: " + e.getAsJsonArray().get( 0 ).getAsString() );
            }
            ent.setContents( e.getAsJsonArray().get( 2 ).getAsString() );
            logEntries.add( ent );
            LOGGER.info( ToStringBuilder.reflectionToString( ent ) );
        } );

        assertEquals( 8, logEntries.size() );
    }

    @Test
    public void testGetEmailTemplate() throws Exception {
        String json = StreamUtils.copyToString( getClass().getClassLoader().getResourceAsStream(
                "get_email_template_info.json" ), StandardCharsets.UTF_8 );
        JsonObject elem = gson.fromJson( json, JsonElement.class ).getAsJsonObject().get( "email_template" ).getAsJsonObject();

        EmailTemplateInfo template = new EmailTemplateInfo();
        template.setId( elem.get( "id" ).getAsString() );
        template.setEmailType( elem.get( "email_type" ).getAsString() );
        template.setDesignType( elem.get( "design_type" ).getAsString() );
        template.setTemplateName( elem.get( "template_name" ).getAsString() );
        template.setSendFromAddress( elem.get( "send_from" ).getAsString() );
        template.setSubject( elem.get( "subject" ).getAsString() );
        template.setEmailBody( elem.get( "email_body" ).getAsString() );
        if ( elem.get( "top_image" ) != null ) {
            elem = elem.get( "top_image" ).getAsJsonObject();
            template.setTopImageId( elem.get( "original_id" ).getAsString() );
            template.setTopImageSrc( elem.get( "original_src" ).getAsString() );
            template.setTopImageAlign( elem.get( "image_align" ).getAsString() );
        }
        LOGGER.info( ToStringBuilder.reflectionToString( template ) );
    }

    @Test
    public void testGetEmailTemplateId() throws Exception {
        String json = StreamUtils.copyToString( getClass().getClassLoader().getResourceAsStream(
                "get_content.json" ), StandardCharsets.UTF_8 );

        final String templateNameToMatch = "Hostelworld Cancellation Charge";
        Optional<JsonElement> emailTemplate = StreamSupport.stream(
                        gson.fromJson( json, JsonElement.class ).getAsJsonObject()
                                .get( "email_templates" ).getAsJsonArray().spliterator(), false )
                .filter( t -> templateNameToMatch.equals( t.getAsJsonObject().get( "template_name" ).getAsString() ) )
                .findFirst();

        assertTrue( emailTemplate.isPresent() );
        assertEquals( "131832", emailTemplate.get().getAsJsonObject().get( "email_template_id" ).getAsString() );
    }

    @Test
    public void testJsonParse() throws Exception {
        JsonObject rootElem = gson.fromJson( "{\"status\":1,\"request\":\"61928409390\"}", JsonObject.class );
        assertEquals( 1, rootElem.get( "status" ).getAsInt() );
        assertEquals( "61928409390", rootElem.get( "request" ).getAsString() );

        FastDateFormat DATETIME_STANDARD = FastDateFormat.getInstance( "MMM dd, yyyy h:mm:ss a" );
        JsonArray cookies = gson.fromJson( StreamUtils.copyToString( getClass().getClassLoader().getResourceAsStream(
                "cookies.json" ), StandardCharsets.UTF_8 ), JsonArray.class );
        assertEquals( 15, cookies.size() );
        assertEquals( "ux", cookies.get( 0 ).getAsJsonObject().get( "name" ).getAsString() );
        assertEquals( "Mar 11, 2021 2:26:16 PM", cookies.get( 1 ).getAsJsonObject().get( "expiry" ).getAsString() );
        LOGGER.info( DATETIME_STANDARD.parse( "Mar 11, 2021 2:26:16 PM" ).toString() );

        cookies.spliterator().forEachRemaining( c -> LOGGER.info( c.getAsJsonObject().get( "name" ).getAsString() ) );
    }

    @Test
    public void patternMatching() throws Exception {
        String html = StreamUtils.copyToString( getClass().getClassLoader().getResourceAsStream(
                "ipaddress_details.html" ), StandardCharsets.UTF_8 );
        Properties props = new Properties();
        props.load( getClass().getClassLoader().getResourceAsStream( "ipaddress_details.properties" ) );

        Pattern p = Pattern.compile( props.getProperty( "monitor.ip.regex" ), Pattern.DOTALL );
        Matcher m = p.matcher( html );
        if ( m.find() ) {
            LOGGER.debug( "MATCHING GROUP: " + m.group( 1 ) );
            if ( m.group( 1 ).matches( props.getProperty( "monitor.ip.regex.match" ) ) ) {
                LOGGER.info( "MATCHED: " + m.group( 1 ) );
            }
            else {
                LOGGER.info( "NOT MATCHED" );
            }
        }
        else {
            LOGGER.info( "NOT FOUND" );
        }
    }

    @Test
    public void testJsonCloudbeds() throws Exception {
        JsonObject rootElem = gson.fromJson( "{\"guarantee\":\"5f32c1261f6b40.80921016\",\"payload\":\"{\\\"action\\\":\\\"changes\\\",\\\"data\\\":false,\\\"rates\\\":false,\\\"announcements\\\":[],\\\"delete\\\":[],\\\"id\\\":\\\"159716176420801117\\\",\\\"time\\\":1597161766}\"}", JsonObject.class );
        assertEquals( "5f32c1261f6b40.80921016", rootElem.get( "guarantee" ).getAsString() );
        LOGGER.info( rootElem.get( "payload" ).getAsString() );

        rootElem = new JsonObject();
        rootElem.addProperty( "action", "guarantee" );
        rootElem.addProperty( "guarantee", "FROM REQ" );
        rootElem.addProperty( "property_id", "17363" );
        rootElem.addProperty( "version", "https://front.cloudbeds.com/front/mfd-front--v9.0.0/app.js.gz" );
        LOGGER.info( rootElem.toString() );

        JsonObject pong = new JsonObject();
        String uniqueId = System.currentTimeMillis() + StringUtils.leftPad( String.valueOf( new Random().nextInt( 99999 ) ), 5, "0" );
        pong.addProperty( "action", "migrate" );
        pong.addProperty( "id", uniqueId );
        pong.addProperty( "__created", System.currentTimeMillis() );
        pong.addProperty( "announcementsLast", "" );
        pong.addProperty( "property_id", "17363" );
        pong.addProperty( "version", "https://front.cloudbeds.com/front/mfd-front--v9.0.0/app.js.gz" );
        LOGGER.info( "Sending initial message: " + pong.toString() );

        String data = "7X1tkyO3keZf6eCnuwhNHYDEKz+dZMny2pLs0\\/hu4269oaBmKA2tnqaC3a298Yb\\/+2UCVUWgABSLLLKH0lGW1T3AVAGFfBLITOTLfy6++GX98PS4WP7n4qf1B\\/z5b4vN28Uni59325\\/Xu6cP3\\/k\\/fb\\/d\\/rR5+DH84enDz2v88fi02j1993b1RH9YP7ztft1tt+\\/Tp9qO1ePj5seH9zhc17J5i3\\/Y\\/LBZ7+i17za7t9\\/9jC\\/9EN7+9PyYPtUOvFs\\/fvfjboUjPm2fVvf0aPvTD\\/3zbvOG\\/tr3q\\/vVw5v1d2+f6U9vnh+ftu\\/XuzCzHza7x6fvHlbvqet+tf+d3o1flUyf3voYmt6un1ab+\\/Xb73b4AY\\/diPSHMJfvVm\\/\\/jgPRbGnqb5\\/vn+hv\\/bR5+7hfmeibfcuPu+3zz7Tkqzc\\/rX4Ms1g9bh9oWd\\/jcNT1bvtAHe929J432P+0fttN\\/c271YN\\/7GEbJuVfSJOkiUcfsn582rynJ79b7XabX3C6+Od19Bcet8+7N3HD5pFe8rTvwIZ3OMr9d2+29\\/frN09Irw\\/t10b0Sz\\/STwfJsv0Bly6s4+r+futX6bvv77dvfgqNYbD331GL\\/7r2O79b0euff367\\/0P6Sk+7f6fV\\/A8C8L8tuDIGrNDcKKaY00LhM9yABvzJ8P\\/b56fvtj9897je\\/RLAIphgrxh\\/xVz\\/B\\/MKOD3G8T3uFf768Hx\\/P\\/k\\/C9Ywlj3Tvm0x9hcXNGg7z2+QZNvd3eu\\/fvr739\\/lbxOMZiZeCX3HzVKxJTf0cP\\/17VvKU8+b2w9X+Mo7LpZSLoVphNHMmsWBzm\\/+9t8+RRLQ0jvlwCoQ1ip8wvBpS29fcRf9QbCw9ErLV93q9HQRlmbA9ZKr\\/Bverx6eaTOYTA8cYjI9Pv\\/z3Td\\/\\/uvd6y+++uru1R\\/\\/UlvBfopqCcJTJCyKUadRJPnkxjorQC8OdGYUkUpIzphxMJsivE4RffUUkerMFNENN0wqW6RI1JlRREmtOFPCzqeI+DVTRMmzUwSRbliZR6LOjCK4ZznLtJFmNkWgShFhr5wiYYrRosymiLANcCEllCgSd8YUkUwpgxwigEkXHyMgAX9RqhVUvKwwSg\\/NGHFIPzTz5yUOzWSxld6sNQcnuDAMZPv9b7YPKDi+9+N1BFso5Vd+IXHvBYCoJZBkAcI5K70I8ofnH7f443ern+83D\\/3U6BHGreN+uv\\/2n39bkKTzt8Xyb9FX\\/W3xyd8Wu9CuFP4ehEv8E8M\\/kIBJv\\/7z3\\/fjinbWgw9pQVGkZHklOlIYN3zUv6z7fx0G0esaDsh9xVVHxgTppB6KE9oiDK20lisJKQycdtxNhwHutBEMLP57x+WS86VyeSsEcCjLjQbGRKAgr8EAyS\\/p3RIaoYLQw5uAnBYGoBwdBfjrH1e7Df54u7r7YfX0tOknR8NxzoTys58ABD8Ei\\/6BGjDaZ757u358s9v8\\/LTZPvj3frN9eLVb\\/\\/D88Hb1\\/T2+MgZQt30UlmAcQMM1JCI6jkedDbBssWOWfhiD39xJqtyzD3+lGHPMSpVL3aUhGmudBjugoVsCXxJZOM\\/3lhZUSBHNtZgLKli8JKj0aaCaurv4IS6No8CH14ckJyBs4kUkWVS4eAVJuDdZcHORJBe\\/BiTxa0KSX+srRJI0zB+fRSQ5EKJy0DltcJHtXCQNh75OJIlrQhJNWV4hkoywooYkhVPXaoAkaQQ3zimtBJhUZJKWM4+ZHEnxASpa05dS7hXr280rLu8483YNmbfqpd8AcQiNaFVMCjeGJDCNtx0Bb5TyOnDf0v4UKBNbzWkC335YkcT85W71\\/ebvq8d+drSkDOVbNiIz2QRKotEoke\\/\\/Mcfi6vXTCiG1e3v3rX\\/jPz+ZgF8a9CLjCHb2cQpsUiDpIR0ixUQLSsviM39BKsEIk3DHjCuYLwpDNMowV8NkY1G9ApYyiUXNFtUi1CU5KBHr+yCMxh5zmElEgCG32LE3kcIrzu5wYAXLSJQYtkpDHCqBadutcpFLcJtsNPU60xjuBdm+yYh2y+Xa4F5M28BXG6TlIliO7735PUzQjyi1Zry+5SZ8glq5raqZEyB\\/\\/PMplE94niesoJsqJxQhntLjMMSHBKVzABwHHZ8D42ryYr+1dnZ1IZeSeS3ZJFs+dtolE7TlW4fywNB8xZUC4xRD6Fpv2+rRDEYi0oto9rv8EM2My\\/B76NDITDQ2cZIsttI8cdnwuEF2taIzmZWtJXiQ+9UWjVFh\\/U3DE2uJ1FL5kb7dfr\\/e0W3Pnza71f2Hfnb0OMpPjnGoQXkABRziGCgMPuYwFIar4YU5a1S3WR5pMUle1zDrwOhFevyjKCeWzDWgJB5\\/ZSxwoZH0iUpyEhb2HdeMBXl5LExUWqtoUGdAA+0MdijFBzSoRoCTmYLaoUGj2KPYbDTst6xrRoN6ATRMUzyraGDnQAOSUrFFCQ2s0UpqGCqZEqxEBKESwPC4O\\/GaAxb9NUd3C7GXuxgdY4CH4tVePO2n2F5zhEXRixOvOaJPbqzFKcfXHMXOjCICUItjqBfK2RTRVYpIcfUUkRBRRIjZFMHdRAhUPESJInHn4CrQSMekIts6t7MpYkpXgcilyMWFNb4OiiRTjBblVIpE72uE5NLEjjbFzkRXc8JZo7VFXPBEohEotYcLpQOmsYgee8EaZXjuwcD9PXneGs5sJoRF1UBz02mpZVUN1W+\\/Gzvoz7Cuqf8FuGEApNYuvnp+Q2fXZ6vdT52m5mmJuogCZb1BaYJxrG4Zm6JnHf10coCirlK\\/jCw\\/L+Ln2TGn74AOsVy+uH++f\\/4gfhL\\/\\/d32iZzmmjfb94sciBlpCVvKBetAehqLCadxrLWJxhoSxxcHOhOTr0CBDfGLYgU38dYP0gpjvIwwEdfCvkoEAfxMRQpjaoMOrbD0UiLDT8d\\/ueG2W+Yirp1uvJToBOobwgPUNtJGPYDHl2Dea+Lr1W5Du+I3m\\/Vu\\/fDjave0fuinSHIes2T6rIrrZzD8nmI4u9w4fGCgs1WzxrxxxNnHKbBgATIHreUJ5nrQx4b2Bf2FEUOgdlapgtdMYYiGCa6NLWO+kQyld1lhQq2lSxx\\/TmJCng99QSZ0s5iwqiUVmDA2md8Y8ioZcvql+guzpCFL1FEsCQhkJwU3eOIPWBIQi0WWFPH5F7OkSIcWeCSzpXB5Kw+GDJCCIYc4acevQsE0fgFREYdwMdK19FehEvciR+N\\/5Rnws80an35cpKzohBC6xoo5W8iYFeXsW9FLMcnEgeZzyZR73gJJJzBJhIlg3de4DS7iW95xJjFMA6teYCVDoCbqpEqQKtpbXtANbuwi87MnHRkcokEzYfTEEIehaS9Si1jVcMDhWtXU\\/RRbIoVF6RbhdMMBJxXOaLlfrEpnZsphXKHyp61msymSeXX3MxBXazjYT5HvKSLZyYaD6JMbiTgXReNa3BkbDpimi2eDE5BgEuM3F8yAn+MB43dLD2SySMESnj3JgXgJptrKHFfS4IZt9ajtWyD\\/h+3Kke9wcN\\/t27hpTxMcA0K4zeeb3U\\/440\\/bd\\/fvVs8\\/9DMkqVBarbSeeh3Cq+4Pk\\/brkcejXbj7+MF6HN6FhwvaUbRzSY\\/xvDhCXedLKRutUOrfawqVzuQqxWklBZ7XUnOWiCXgmJZe7ZiGpjaKpjfqh6Eh8UjvW83Sm4JQxJOAOgpjalQsMQgc78HOG8M6vxqPja4HECt4IpLM8ZfV8\\/3GxxZ++s2XX33x7af\\/8\\/PFJ5ElCqVLrr1mMgVNtlE8ckfmF5LgRTboBSTrwnIfuulJ6dXufVzEIjmyeF1kwLNcWcF0IaytMETjQCnIt0NhlxIa3PCUqwEYpVKpExfDUwC8H\\/olAAxzAFz1WC0AOBavL+S09eIAnnx1nUHYQwYuBmGLEqwqIsY1wJ21Q9UQmUprpC5wFqa1Vw2NRBF9wnV2B2Hlkqgg4X0uBX6cyVqBLf01kSO/HA5CsXB3WD3RbaP9mKYRMnhvdC3tTwCg20Dq+uP28d0zOW19cY9QHvg2Go0LNHX\\/RTXCuctbasRgULgEfAtLPQ7eIa16sKgYvHi8j6lsyiK4TF0cjYZoFGlfw7DPrtOC5joPFrAWdyMlNXNiemi0iPXCvUDKhypbuP8X3k\\/sHEGGOMS5FYTBFPeLcmKwevK+hnEwdugrlXUmFJHkFoN7FsjgZRVZmjSHsqt0kR7cyPQGBu44LJVcSpO3quCKzwxTTgCyuBndTrhr\\/ALhUWFCfErXsrc0ORHyHPxu9X5zf7\\/2v9Evq+d+fn5DccKJkfMw5W0a5WTefnX3en1\\/Txx+9\\/ruv7x+t9qt3959vt293zxtdx8+ufvX7fv1w9324f7D3X\\/Bff\\/79dv\\/+l8rVs58nQ6ab5KF7ijd8Vr0AG0ELtoIiD\\/xX8PHrJv7lzfKMe5cTmLybW5wtYUdSmGac4mnlRGGOcvjM+wI\\/wLZHhMW8lBjYDQDpjz05psJLJx7FxhMcb8oJ\\/vgRO9rfMDfnhUrnRlFNCrYhqFkPNmUVqWIqlAEMXEWH5wLUgSnCBFF9Mk+ONEnNyB4cHkY7RyclIZuAMCgGmnmU0SUTkq+VNw7b78oRe6OOirDHDsTtF+VE62byTc3RinNhpGOWeeQJOSjphQxyYmugxFJisJLmIG7fpJIFpPEzSeJI3nS2WGgV9Y5lCfxbMSzDZUhPlmerJJElkiC8hM\\/k6fa+fetwRSjRZkpT9L7Gm2dscOUAVlnop4KFO41qsAobcg0iBM7g3f2AWeCiB7x7YNgPiSwDagatJqQ1kZYYLhtU7aGzsBalCetafxdk4XOmaBvaX8CCMnBa16ff\\/Ea\\/\\/vZp998+em3iz0dUZRU5Ik11YlAo0r00k4ENOZLOBGcw4LzUpai8jhw9nUriPEFeB5U6RN89wwWpyJZCBhV6UEp5wpiV2EI\\/GwptS7yl26QHwwvCCoWf1g8FTWf7r6dWqRalqc7Jl7ZgsV5TkWdCqBnVum7WM92UWZuwfS+BjRjepjbI+vMbmHxNFSSGQOT5ZRUqe82YbqnqV+My7MIKhe9hlXxNaw4kSTJJzeOS2dju1exMycJauCox0s3WeOtksRWKaKuNiXgfoo6ogicnO4s+mQfxlC4CRp0JnIKbgRIKIb\\/CjldcqwzSZ6BTtLOqaT\\/3uskyX6KXb4zvyonGyGiT27wRJAukdRKnUOSKI7\\/Y+QS5+aTJIvNaqegllxeOUmUz+25XxU5kyT0yQ2TQguZkyTtzCKBpFWWMxBWzyIJJaHLTpI28sWcJymgHhDgnJFAJk4KSItyMkX272sks8oMb9uyziFFFLdGCMasm3e4E0XqsVlw7RSxffxiuygzKULvaxQlMSxQJO3MbKeGeQE4DTA+jSI1a7Y+jw3iIhRJphgtyqkUid7X4HkAUfqfSmdOEW0UdU6\\/X6hSJDtH9jM4x9F+YYromCJz7xd8mkomKZC3SJGoM1MSLdLAMOGmJ2CuUqRozaYwk\\/PcL1yAIv0UYX+\\/gIsCXUKgk5VE+uRGGwrXzZXEtDOjiGOakrSFEJ95FKmp7b8KisSW0zNRxAnN9TCbYdYZu+qiJkIJy4RkKFqm+ZjIEaGc\\/q4u\\/O7tKz4\\/vhBLqZdRSmj1ivsZSbZUwW9WMWs4d8a7lVVNp8o1\\/k4MVV1tg87lGhsHfZBncbiK\\/\\/pvz4z9IB82b8i753fbx6fV3e\\/Xu916s1v1U6V3aa2U9cfFlEt5FFbrWSummPfUSGqlUmqkfGnGzXbDte08Ib2IkhwZZLMT3THpV89w5EvrGTbGe5lujZCKu\\/iqt3eBRHHFGfJqrMDMIAJhNsz2Ct6vBmVTXT8+JsomuitWcSbPjjNjmMyTSBDOVMOcMTxTVCSFG2nLEfU8vr8GqQHFNn3cdhYLgELcMbdkdsl43uoC\\/CxC30hhqcTAGNAMa7wxXZsGhNcnfDRb1AMgjAI\\/1F822ydKVPDnnzfrnxJskfFbMKgn5PpN3WMUFvfgPUZCnU6LtvGjCyFG7zEQUEzwuogaDdFYwXlW\\/8CrcYw3Cuee3WN405cVuGcyJ1SayPgUwCap\\/suA7QxiPo5qHmDViYCth8nkgH0Bv\\/AXR++hjbYVQDt4xN5zs+G6J39DactswTInKZLBIMVsZr+mJFbWKOeYhTRzhgbg7gi0DlLcIliRU6RYMshbITiHO+C46XPKnybHwEoh+sYfvQ3X4ZZfNiEPWue3SclGvbD6+sP9LxvvBb77vo\\/Wb53ANequ3tl9ClYp10hcFODonXZi5G4K1ktlaC4s9YS9NqKVBwuC4qj8zJIrL6FNGKLRzFgQA6x0+ZnxTZAl62zBq0GB42l4+wngHQgBFwavPA28UyUDBG+SamI+piaC92g352PBO1GmfXn40jW5WZThS5WSYBjFgFI2Zwy3Xsk0iOkuD7G3YezyULwVEeRBLc9jXzy7y0M8xb6cGC3K6VeH+09ulKDYoMWBzowiiDX8++TBPJMiWVxJPwNzLhv82eNK4inyiCJwsg0++uQGNW0teE6RtDOniKZ0+yxInPMowusUOQ+PXJQiOqbIXB4xPrc3Aj2L08s6M4pIujBhRtjp6TJqFMkdHroZlKTeK6OIiHlE8tkUEZwSN4KWJYrEnRlFFJMgmWMw3eGhRJGRQqE4AzjHrnXBknvtFKNFmU0RYCi1qcquFXdmFNHcAlClh+m3IjWK1HcteQ4euSxFZMwjej6PoJ6LEw4Jt0c7c4qQfZFJI+dTpL5rnSdW7LIUMTFFTowYTd6HGo4OyUhGOzOKGOE0Y47DvHNkpFAozkCdJVbsohTpzO7tosymiBINLqYtFAoddOYUsc4ZSukzT\\/ot5jTfz+DKS7e2U4wWZT5FLAm4KrI+VDozilh\\/NyeVmu43V6NIFk\\/ZzsD6dCNXTRHbVwlrF2UmReh9DR3RvKAhpp05RRzKWUw6MV\\/WyrLM72dwtT7xyRSjRZlPEddQ9n6RF5wedGYUoRptjGl2RC6IGkWKnox+BvxqfeKTKUaLMpsiXDUO1QpT5JG4M\\/EJInMsWKGpzkjsNgfSWhE86XKzbJEejqeJgCXZ2yiLGctbVfDjRNZUlkvDYdw1AGcfVks2svVJ7lranwAKxaNwTK6eVo\\/vyDD719WH++2un97Cp4JQltWzzGfX+bya6n1Ompf8s8cNpMN16wgn40cPxYQZy12pHEZhCLr7F2lSkahTSaUhS19LzmVcc0nGbzb5+EvDQFXH2mK42UZhqGcKd7lcTBjvw13CopzI2sknN1bJuIBEpTP1+rHaGVSsZcv3HUGEBctCCOyBsNyIHkGD4+4V52QtF5R+dBldASl8lCBCVc1p4bQWHuVG9ObIctYoAU3wRbS6ywPJOQ\\/F\\/rg1oVNQOstQ1\\/PrFfn+rL8P+V7++u0XX\\/\\/5q08\\/\\/3SxJ+tCIgSVg2p+4UGU7qyafUc\\/nUbRuqMr\\/g2T1Rz7PMyavZz4dGHXGyBikktdAqnW1SnsPWlUfec+wUtsVnpdgwJIqILeHZVt+JghLzDDUAceGIo6fuJCKJMkEjyJn9Svj5\\/MjZ+ulZ+mOw+WOYqdgaPIhRli0+ueo1ijsFcOVWaOopgCS0VIIYmiEZQiUXjITucoGXMUUCVcYULKkvC3tZ+QXEobDAYoBoKS2jjRh9pVShy13OMgYyjHG9vyEyUrodl8cb95JE76X6vnH9fbzeMiZSQqWFH1wLox0sdgpAEQDh9MQyR1bOTNs\\/JoNkpeh3gySsf28p6NpCF7uRNDezk4S0miJaoTaWo1kCC45cWDScRaWcxGsRmSqTvBUOQPZRwHrS4wl5OO8ggKzvRohtsuy4oRqIl635gs\\/4pwlmpZ0hverXc7KhT2Zvf8j5iDFJ7aLJQVnVI64mUSsNwSo5w2jjz7OMWcqhk+x7l7CPCewxJP0DFvJKpWYkIY4oQhGimEUabMYA0VVWC51YYBApvjtEJ5+chqgxOSR3C8sK9iaw7q+cwf6JHbct+KJ7nzpxgKwNxRId3uq8sBHdB406\\/iDdUpiFpaEgnt7TKkp71+Wv\\/inej+uH23W\\/dz8xzvULfVdWe6lONZI452VD6pPgxv1NHbyUn1YS44ELzU0snzD1QKp8mBOcGuFiHbs5Z2oUrY3vFwJPc32dUM16U8RYUhGtyFXGYP9Y6HSjTAUQAZpJGxnFvAM09bVFhtwupcOq6mJFfTi84nMMr9LbwByfjMzbraasl0hMKNYK5b5HKEgmkrd2jZV\\/PYt2ndVfOQRoRbgc83q4c+dKufH9FQgRbCf8AUEZnNk5GPfvylKolM4td5MvLRj6upjxfKoAyAdJg1h0gMrCmtkMNHjyuDIvxVCOqJPElRLX2BcSD\\/X6HxWB3eXJF8bywD8PVopntAJGeu3kvZmd9WK+UHmeMazduDKUaLsjjx5ip6H1JE8kKw4aAzp4gyjjEh9fTb3RpFMi+h\\/Qyu9MJhMMVoUeZTRDVaWatckSJR5zAHh1SUXouhWDc940ONIpknXZtz4my+jRegSDLFaFFOpUj0vgaFfkiiCYudyX27IURo6xwPBcOjwiEcjCvf7vICPSjfQ3KxrElFobtll7dCiPgEiQIZ6vRkoRzTE6ZkZgUlhb\\/Q+Wq1vvdRN5\\/tnn\\/c7LbP\\/7efoFcWjJDBr\\/Y3Xfrmt6a3TxE4LmQfKKD0kH0ghXnH5NDe5R+2Dyhv9lal\\/GeFIRpyKNMxm\\/V8z6GRlHqylLacHAwU+Zgn0XbjO3F84+\\/NjOWduM\\/SrZfqPL5oF7qM76cYLcppO3HyPtpsQ8KF0c6MIoJTRVZhjogfq1FEVCgSUHfVFDHBttQvyolnY\\/LJjbYCErekYmdOEQPki3aM33+NIlCnyHmSgV6UIiamyInRSsknN1bYEEEw2jn0RQOupKV8KGn6EPJFg3Klvho9Cl5nbT7HQWt3u0FmHGkYlVkbk1b8ORR+stYXbd8SKvcd9EVrrzLIF21EVtEvkp88\\/+yJvmjtuvWES\\/KTozZ90BetWnIsGaKRVAxOFenmScDzRGcU30tGOUcKYgIkSp1QuRCL9\\/PgUT9MlGDJMs8V2fKA5600oYU3zPny8jak6a6bx1kjvTndNixMs2vpfgrHEWE+U9m3H1ZkHf9yt\\/p+8\\/fVoF6eI5Nn1TtjIK6JRkOcKuHoauqnyG446IVka\\/1C45izj1OSRXPoHGTGBHs9+FNm1KPMiEewrWctSYagBLWO6zL2G23wpB3YaSzlUXCGCpE5kVabw1+4qlSbi\\/m9Y8Y2L2dvIORARjslgoRXbsU9VuCa4lhu1MlDCNcEFxTHG6HC9VTfpqAzYBsGwocS\\/GG9w4eJJ\\/+E1P7HfT9F4mhlHY5XT7STMIfRTdXJeAroT3jeTH4+AmkX6TRY0imm3JQmncLE2fDRY025kjcgSM3p6R8lJqVrR9SyhjcqxCBIG8OkJh+49HSg1BVFr6MiILPyh4os1pRiQuStMghZSvlM0szx8ctTSSkj6SdrbLg87VvanyhmUApkWtHX25\\/fbUhK\\/Wy1e7pfPz3182vPB\\/yn7vOebtWySY4H9iL79jnqqU7Zty9Ut7VA1Cn79h4VHSxVzBKHhCgUorkaE6L2QzQobYMzZVQ2gOIYz6y5huHm7bP1yCRQncpuCleRxktskibswaE1pUZD9oVCKwSp0kkRfAMgCHA1NgGD7EE\\/URQM\\/ld9S\\/tzohDFmWhDkqYwCZ3\\/seHw9IKh\\/\\/8INwWSHmSSBBM9KO0xwg2K\\/KNlTfdDNMghGqCMSVwTLU1mYBeS8vIjxAxPctBQ6TPQqnyWxHwY4hKDI06St6Dz+oNhaSZBDnn+OowyFXEqqe3kqIF9Sko2cNrfEHy+3W2f3q1JZX395t1zK+NE3jjIL9zU87KlUMLz+egAqokuJXaYQ6t6Hz3HpaSwxAetwymNCCQGQgUgMRG0gI+4sYrc+yFQFuAMhsjxLiUovjKNUncWO96CFrEuk0itk0ArFi8J2jQV23GgrbuQZaCV8eZ+tIZ8KoLnu3kdQPD0fGwvjGHUHJldlDHMhdDD4DaUljRVeEJ90+iBz7NDocjv4uPSSbigLXhAKkppDW5gK\\/StkvnSuagHCoSkVag4j2JYo8Ls838Dyp0e7n1L+9NfbDJ\\/yfkvv3jp5LPN7qf3qxS\\/nKHSpSbqkzdjy+Rx7AspIe4llJACKCcoIRGqe7YyiXw1HlVMkTppidvqEI2mevZlrpLI5kzY7NpModAlOEgUz8URqYBiC1XP6HkKh97xSyxZwQD2YgkDPvvjuCm8n2G0JotT72j272uMNx0vDnRmBAGJuxadqdN9fGoEMVWCfNR6bZMIwmVEEDj9Ynn\\/voZqs7jhXUfWmRAEDEXWcUv+s6mabiWXuuziUyNHcqftrWzSLJXJW12wcQFKn8aX7lOjByEnS53X+pDTNfgc8JS73MZ9KI7iJuMNVV8+b+7vV8\\/vCUP\\/5+v\\/\\/fqrP\\/\\/r6z\\/9y2JPU7JqMe5M\\/Uw8\\/+3ZlLPqcuOkZ9XlnIjc2ccpnVU5bg6eVQnweuSr5Kw6cOvInSu5DhaGaLgDk7jRx50KVQ+RiaSBE\\/EfbpOiyCdxosqHvignurmcWA\\/Jyznx8iF5N66cyZUTVcUX50sBjEeFribwpa85rBi3KLJwO12GjHkt5sxqbWy5ZFebK6+fYlf3zK\\/K6RVNo09unJQ6yV5Y7MxJoizlLJZH+CfWSFKrja2uODPbniKR775ic6uV0yc3lJFEqZwiaWdGES0VMonlfLpcX6NILZriV0ERIc9OEaedSTJFFzsziuBuTHfSAubzSG3XUufJb39ZikC0a6nOb2AGRThrwLK0ommxc5C9kFESIIvyjr9TmkkRN6RIX\\/PgLJWxL5m9UEeVsf2inEqR6H2U2g9cIZt62plRxClhNUotbnrO1erRnoUc9VOAa08oqftL0nZVZpPE34hSApESSeLOxN0LfCEyisprEy707l44M27K7l41guylCkH8KQRVro9ioIatZPy3km4GujJzFXcvLprWnGj6pD5Mt5mzpOzcvZS2IeL02+33+N9fVg93n20eHtYPbzd\\/\\/ylWgihCA7SuO9kkCgmqXlWL9hRFo152aor6MPJ0Kag+XdJJ7l4JTYYHacwSi6PcvYRuGLmO1SQbrRWrAlIgdVID2SmArEPvOgFZ9y7\\/TQByopaaQzLAgmePzodkpTNJ2ASG4X8c5atIsumDpDoUqlwLLbbTd5BkXMbxmtrvz6QKL6NilFkrwohTnn\\/QnSRVvr20AZKqRyTBJPkprDLhYv3bzeO7Df78cvtuc99PzpPLCQeyvjOmJhTTHFuB101+vhT+kK7F4d1tuJht0IyS2aMTc39Fr2soV1Gy9bQxM4LqyQAe7NkFWQclg1ufnA8l9muAUn1PuxooTdyXamACGD56Cpiosm3iiNyBCTutMNYVUipQoixuFDMhidQkcTpJFRWiiEI68IpZRnAfNT5fmnb8\\/NJ0O0URyy7Qx1OcqnL6T24cSuWJNF3szCgCwMlOpqYbAaoUqdkuKS3KWS7AL0qSjkfCqpx4A558cuNo28ztMoPOjCQSNNfMhkIKM0lSMZWR\\/FBIIHdlJBExSaSYTRIh8JyTjhe4JO3MSEJOX4zKRk02A9RJklXD6acAZ4nlvShJwEQkUSdXHow+ueEclzbPBTPoHJCEcpVyzhie7JPtyVWSVPYtda47l8tRhKboeopQCqt5FPGfjHqF4yLXgwedGUVQcXKcCTDzKVLZtnAG\\/CxV1S5KEW4jiqiTq6pFn9xQHg6TnySDzowiWiltmNFJ8ZXTKFLZtVS40LhyiggVUUSfXDEq+uRGaZTE83vJQWdGEQP415k7IidElSJZxah+BnDlRztNESKKnF7nLvpk3JiM4UWKxJ0JRZyTXAIlulcJQQAlMx6iGjP9tkgPbvH35L7HUKCoUqHo5aBVL73+IyTjCASwTo+GobWqIOu02+jPYXTc9r2G\\/PVq8\\/C0flg9eAClfwqTXJBHjbSC1euXsCSLFTtOyWVizsMw52E552E152E952Ez52E752E342HO5jw8B2F8DsL4HITxOQjjcxDG5yCMz0EYn4MwPgdhYg7CxByEiTkIE3MQJuYgTMxBmJiDMDEHYWIOwsQchMFEhJWssfnJ3euQ7\\/fHbvQr\\/+8PW\\/rzfbMoyjGJYNCKJqqLYYwesIx13o+j1tpYAoGGSaG5Gwgikky5gjXOgc1CMTrRyDqhVZrH4hTRyCxKEyuKRsEN+WOKRtVkRzfR6CYa3USjm2h0E41uotFNNDosGmUX1TOFI9VdPnjhSJ5LOHImWH9y4Yij5KR5Xq8hCEcclFM6DYs8RTiyi9LECsKRCWazjyocVV1tbsLRTTi6CUc34egmHN2Eo5twNEE48gLDuYQjs79RO6twhGKOtkNRpBeONChVuVRDkQiknn+pNrRajQhHIdnIxxSOqs6jN+HoJhzdhKObcHQTjm7C0U04miAc+YiM8wlHsnPJO6twJDgIUxWOKHVXxXIkFAoLcrblSA6tVlXhqE2R8jGFIz\\/Zm3B0E45uwtFNOLoJRzfh6CYcnSockaSSpECbIxy1osH5hSNKmqIGokgnHHGuwYsgBeHIB1oN0m6fIhwVxKCacMQ\\/unDEb8LRTTi6CUc34egmHN2Eo5twNEM4IpOLPp9wxC8iHAHT3FSFI+AcspjnVjiSHKRNy86eIhyJRWliReEoRAJ\\/TOGoWhDqJhzdhKObcHQTjm7C0U04uglHE4QjOruTAkzzhKM+r8J5hSOpjRk6RffCkWLgqyiXhCOjnYH5liNYlCZWFI7gY0eryWrhwZtwdBOObsLRTTi6CUc34egmHE0QjuhQTap\\/zxOO4CLRapI5nVlveuGI0hRWrtUUfiiYQUXmE4QjuShNrCgc+cqzH1U4qlbwuwlHN+HoJhzdhKObcHQTjm7C0QThiO6j4horM4UjJS4hHCkwUpbzHKFwhJ8hK9dqyjCtbVri4BThaHilVxWO2oqWH1U4qhZVvQlHN+HoJhzdhKObcHQTjm7C0QThKDgJsXOJR\\/ty12cWjxzJRxXxSHBQKivaE8QjDRakmZ0GUk7OkO1C4eKPKh7dMmTfxKObeHQTj27i0U08uolHs8SjIAOcTzziF\\/E70lLySpZsFI+Es6qSJdsw6YSaH84\\/OUu2uwKn7FuW7Jt4dBOPbuLRTTy6iUc38WiWeOSNPWfLk+0u5JZtuLYwdI3uxSOpjBy6ZVO5aRSCmBbMOjm5KmRSpLyveKcFZcyMp0fnly867Xxm8PkV73RSN+8cFe\\/iKbYpqMKinFgVMvnkxmilIopUOgcUQeKDY5buOyeXfK5ShJcoIpaMeQxeL0X8FNm+KrplQs2hSPvJjdOoQajFgc6cIk5J+q+YzyOsTpGzVBe+LEXcmSmiG6WYcLZIkagzpojSOLQQHHctpNggmoQb5+N2M62uSpDEV1PfMbP0m3TUKiTto7jJCr9yCvVMIyRut25MqTOs8X9BmwaET8vftXQ\\/Abg2YGkJv9o8vH1cf8Df\\/rC5v1\\/sSUnaHKq9ruoLkJ7NXDcWXPRP7dBrH\\/nu7frxzW7z89Nm++Bf+\\/pp9fB2tXt7961\\/4ZQjndtG8cuM415mnFRQ8Gs4d5yCTFEATs7DMTd3UE8MM7zNB2QU590hRX9BKf5KMSaQB2zhsC1gueHgIImlKnYmm6FxHE8tZbTTzCach\\/JD8HvOOc8tCpwn7CsdtTNzx9wSD8eET9tW0dbVMMIBV8YyJ8c4DzcO5\\/++arT0HtV9S\\/sTQKGsJGi6r9c7FN7olzeb1W63fnpa9TOkZeV4EAtXTRw9gKhs4EUgKhotLgDRwgpXIFohUY+RJMODG4WsNdrYQh33wgiNstoJUUZIYxkoNfSuN8xIYzVobphPlN5DVvkD3U2GrGa+En1kIrnjaqlEm7ozbYUloyWwOK7TTinpAyarkOXEcf5vcEUhBCpp634B0MI6bwL96vntFkUOwurXq3\\/8Y9VP0WOWK0oLPw2zWjbyBSCrdGPsGSHbFQkvrPAhyKYk6jGSeE\\/iXxjDrEPpHAp11QtDNIAvTzAbd2qtHQz1Mr\\/t4\\/+1YQqm62UQ43Av4kivwFaOARJ0ziB0ynMLnf0UdZ\\/+jrwjlHKnCZ3JJzdcCRCx4Cc6xYy7RuDZpniRJKhDW8SKsvNJAhWS4PcWcHVtJFExSWAmSeiTGw1Sc1YkCSVyVhwyktDgUqMUzUAKPp8kMiNJNwV2HtXsEiSJp9ivihYnk2T\\/PuQSC0yVSCIEHkgO7OCwtdYhEaXjIJmxMj5sAbUS8Om2s8O2TBCUD\\/ftymuFqJbJZSSw9q1qGZzKOcotBhxT\\/k5u5LBt\\/AJxhgpFuJnrWtqfwjGDn0ds\\/r8293+n0\\/UPq4efNqv7RDiUpAVrW01\\/9EJCW\\/7Z4yfgcN06wqnExCmgfgJKRyizJbbohuCC1HhBAlIjJC6SXRzojHkbtXuJoqR0VhuduP2jWoLbszwKSPsbWo1cfscd7Tmgq614OnBhDWO8N8MWgSQRQMxPqjEq0ARCS0siEBqY9GLn69Vu9Y44e\\/3L6vn\\/JihyCgUQW1UxBijCEY6xZA++5TA6hovRUkMzN3x00cFtzEadvA4XR5rywkvsRBTzoYdjiwQFQnJvKpmFBFEa+sqQ4GtdXhgJWeXPj4EFySAqH3UEFlDidyKJlD4FC1Aa+tqwUM07fEYsDAudfQwsaOOilEITsIBfAGSFEoxngWF0fVI0RdVlv0TMYXfMLoVZcpm32qUvN2us4agRMItzWIzJGroJoimq9RDsD11L+xNAoK5OQv\\/iy936x+3Ow+J+8\\/DjetdPkEjGlFKsbgjOjJjiaEvUt9vt+7s\\/P9x\\/KAsahW8eh8xw0XqNwQwfJSmjs3O2ligJDLQq3FIV3t0wMMH7K9ceTKOAcz3Aj0VFTnHJpWAgVKxio\\/rHtTZF1zDYSxLuVYgBiEVV7lCyeSX0neBLodv0Cv5vwyvBSekHFKw90VHgkhyQBfubxiJ8FFmDaDdAaVx7UVWYxqfXAtYIP6rVdFdNsPj0fv13lBZ3W\\/z9z\\/frFWLp7SYVWOka2IiqZQh4iqFj7tvdwLGM88YcsyUNluQwuoZrGu6lFBgTzrYjN6RIKkXFwAgDiSNguLrlmmy8WohgVy4BClfa6NmA4r8qQNVl1ysB1ER55yNCShmdX7fgzq+VxP+14S77Mw41NufJNma7xhWEPaRkPDQer5Q7eQnFVi9JGdQnEclCWt1pDkVIOdf4yTmJnwhBdml8MFIn74BC0d0fpn9fPz769zz\\/+O5p5y8891ctzGkXopimXLXwRlXhUDvgvtk+vNqtf3hGlfr7+6l3LcgXR6vtEwfiLzLQkJFsY6uMOHMccfZxSnJIDsyDt1QJsgNrORXku4kmf0MiZ8FaXhiiQUlNCTbgLLcElEh4YwUy71Cc7VjdKdRlUnH2BFZXi18Fq1ezP+Wsruc4NdzY\\/rfG9hPP8xdnfM04U3pRYXyDp\\/ggwYklZRXwvxK1kNS+xZDrpRcQD4iNEePvBUT+ShivQttlpPVK\\/Nt3OFWm2wgVwyWKe1YciFARtjWMQ2ODTaNrQYmpscFm7gyKKvTxf\\/pwTzcu\\/7q5f7NNjRpScVQeq3yfcoZrVPVmegrgT3heTH6+6D2RLeY4PIfUaOEQ7hdVD082ZojHnZQxmGKIF7JB9d25PUYqnYmZBYSjS09rgMvE105oi3QtaDUujqDCP8iAAG5kdKMDrfQr2TKpkkryN18ylL\\/9HSsgczhuULsatbJYnD0toGUNzpJYTIrGv6HrAfwW7ryh6KvV8847\\/LzfPL3r50bPGIH0g4p2nKkjCkeYA69Rm135eZhj8yss5kHzTUqNAAfnIwVh2j2RZWT2Srx7FhV6N8w5G7l\\/dZ2EEjxTuA0WwgI68cvwkSS+7xR07ttfAp3mNHRWVO0SOuva8pWjc+Lp\\/tL45CjcJuk5EnwaF4zOWYAFAxQ6cJu2Ex15WkPQEKFkph748fQBBXjG8\\/wEuAYPhWiKYu\\/Ob1wXbXxygAV9Mh5ZTkDiDljqzCniOPnzcKdnU6QW8vLroIg7O0VAOiOGARZZZ0IRMAxocxNghpqvJBoW9\\/AaPRKl2ws3xKEybxXB05ijxKWD2\\/ToHq6h8VjSrKG7O79nN96fpf+JH4EzpqG+uN880h7+5eqX1eKT+ArHoW7sKv4i2Q6szuCHPmWnPsc4pR09X9qD2lpKmxYcig0f9ddF3VWR3+2ZESg+ILxGYLp\\/eSNQieGmjIrGkPlbVmBqFOJyYIs9HqY8H\\/rqYFoxxP6WYTrdrPBiQLUs+KFNBmpwHAbcTZGILBaKjzjhVIcH8Sr3igxem8ZfR53hhDt7wFo8xVbmCItyulfk\\/pMbLgBM4jtc6swognQEEnGZnE0RXafIeby5L0eRfTHzQJG53tz0yQ2+h\\/NCHFPaGVOE4Y7NkBRGWO4SP1XGlLCqEEFYo0fip8p9PIpBwWoJeyFIvRLqzoeoLP0OYbXBbQEEoWJsMwfVhDhE12hlIWppt7Cpbqq06QhecSvL9vOxa9Yp+\\/TxzydZEThyUdXCX9rAC8s5vn0P6dEBwqsDvdltVG+0hlIRGZcySpnijTQ6sP2gEyEKjdFcmqFMjDop+WZxa0HLtJis5Mrywm3QyH6RJL1m5PlLV9yFVh2SGXGcE8VGIeuM3gZJsnfTpEznS9C19D2IPB7WdY3CBu1uPz5v7r9f754W+70GBQ7A81dVzMIZQCXKNvE\\/s52qp6D6QpF9GfpVMz9otyTl5DQ9ZFwZgKLdsmVSnlDw0asTHNAH3U0YApkegi9htoujpKMMk6wUk6PpqlUz4HbiuTq8PNF7PmHVc7UU6nVV56oJ\\/NwvShcdd\\/q5CrJBgdiBKVEk7swoojXZu5gW8ylSi1szS3ml6SviKeqIIvrEZAnJJzdWg0pdVUudqZusAic5KjgUmJOcJNjJXTH8tUoPHg\\/NfHYZGfsV9K1tWjxstEA+Pe5ARI6FJoQnGNkY7h2hHMoRIu4DCgHgXuHqjeRv3r3fPA3OEkVfWneTHXikNfZC2zp\\/oeMjOxsvNM6v9zgsj6Ne4tgtMMDBO42Eg3oWVvGxO3YljFsvKOWvoCcMgXKaY6LMwaIRYDgMHF37LQWcBjF7S4F86ItuKXDyllLNN17YUmJvpaO9FG\\/7y21\\/OWF\\/mX5rmu0wXmZMUnacc4eRUomiYA8a+dFBlrMDRUvSG8E6pSBNM6NxJ7Tla31Y5DsMyj3pDqPIvVvIOGdH30pe2guK6uVO4UBW6e6ry670vPFXmtI2xnl9u2vpfgJQohkvjL8mP3raX\\/64fbd6eFw\\/9PPzG4x2Dnj9aj8zhIt4g7mQ9T1lFFO36pyTT84wTIFNCiQ9yCQJJnzkkiQvqPB49EBs4fegAoFNjMkRvTd6eSOZZqlM7xOgKTwFWSOU5EOXQX9da63lityyxMRcFO39U34Ey3KSQH9ny86TtvGSd9id50dYlLlpG+mTG61bU+JoZ0YRB+AoJZ1ksylS9\\/Pg4uopwiM\\/j+DoPo8iXKBq27rMj3bmFLFaOUYJrWZTZMTPo7DG10aROLVpVwZpjp8HayzlWyqkNk07E4pwJ5xQVktrh8FMDAWEQm6QGj2SlA4G5e877329ZJC3quBa6yi1p9BgxfgFOpnrXTjUmfW4Earxb+h6gGzwwjP56839\\/erRKw+7\\/kjvIhysEhIqOkPhzkMnR\\/qFTNoqG\\/QCh21hqQ8dtimterCkGbnGs8hpjRJjNSNXMkRjjTRaFLFCYZG4vw913ha8qCrjIKnOewJ4C0NfELzyVPBWKkiUwJtcyFwinVwJvLMVxAPgnaxQvTB8HbOal9FSg6+0jvJYaiesS8vEWcNDxpGJ8B362AHFJnfa4qCVh6xUIIGdemkVN6MKFfDGJ\\/vFE0Qqw+OW7icoxoUPNyHU4o8vt+vdT+vFJ4n3Eu67mk8E76\\/cAFBY24O+SwlxenSkaBXjIVFau1K66sIQ5MHEoho5aSfqTkIVUm4b1PwpNTKqXdOl6cQAYFpMWKhK06FO0XzZzcLFZLd9KaV2UWbKbvQ+PBFA8ILslnZmFNEcz03GuZ6ucdYowqoUEWfxmr4oRQSPKKLnek3T+xqQWqaJj0udOUWMYYahXDKfR1SdImexAVyWIiamyPzSDcI0xAWsYANIO7NCAbgxcso+BNM1zhpFZIkiuC1zn0vneinSTzFalDkUad\\/XMDwK1DB9RtaZmJG5A4XnC55V0qUaJ369FGWpp0aPWOPkzh9hZuBFFVrt0gdK4nwofNeBNqPZykupnzULfn5R5meqb+dX9Yv7zT9W3699cNgv24e7328en\\/pMUV7rxqWQlIqmKgFlVy23m5Yp4+iz31AVqwRksDnoqJXgrgd+cp8jDtznoF4wZrCOhmiUseB0EffQkIzgKoyombJtlu45jAj50NfNiPV4n4wR2Qvo0Te2nMmWk+0COWNSFnEb8\\/Q5GVNzunaK+KDPr43oVRR\\/MLSSk1el1vhJFrSIhUiQzpngGHHALtAxZpJQ0+e4p6wGsOSFVulrHRF7CJCGrq9HXTl83DQtke4q6UjWxk+3PQConEhvffl69bAlY9bvVrvdapf4weMKgw3lVKZYBqBxswsWTOGMy42jBxYzfgnOKFDxkAUihUGHQ8OGj2Z3q5Q5VRqk9Uj0VPTyxmgBkVt83GkaJaTMoqdajrCMm1StOokjZD70NXJE\\/YC6ccRMjpgcUfhiPGGZFVAAZpUnLCKE9CutpbYyTcYOSmkou+OoRc4T5I6z16OUT39DSeiXUeWHYSvlT7BCKoryaT+5LL4xFchg8TNCHe2+SfWZlzme414E\\/Xyz8mWfvt7u1g\\/bfnrEStYAOFb3xknAqo7NkpFAULHq0xGwujv0wVJMiXRK17Ijps5h1e039P8akKJ8QRKBZDizcVhTsTMtD0FFLw1KUWbgjA6SCetvqqYBqVAeQlCd0yXwvFUsvTlGoyhufLGE8bA7AU24ZnONhlB4rG3pfgJVWlc+Xcvr1Xv877eb+zTBHVWGQCLVb9DOfxM7ZcNjjZutVBQ2vMLKHsRlQpoOGwkuER0HKlBo5aqicVpkQjmIC\\/5UOtPqMpJZ3MlxJ0F1d7o5LwnCsy0mtHhlUnNe5ObFz+NAcolQlniK0aIsTg1l2b+vEVqFePnRzqzeDzl5KeZsG6g4hSKqQpE8jLoz8corrVY8mGK0KHMo0r4PtyAupVgc6ExSXiMdKFWEZlzzmEUEChkoQ5ftOiV67JPDUcpr7guxAoVRRxCBVwIPMzL1tMmZAeUjjceh8nfDVcHAtsGohnVyAerKPieiUo1PuSgMp3RA9NJv19+v3\\/iyep++7bb0Nv8WoyrZvBJYVLB3VK2qk7bq49NvmcnPl9NvxYt5eAvPqBFyFxoRp9Y8SbTglMMEgp4+2lmAogIuOYtl1JOgyH8VUKw7l\\/1moDg9b\\/sVgdHhhoziLKC+RNV+YzCi5C2DAXAaGNsELIuoFACXS9nW0+g3akMfT5WBO8TjeE4K3oUBl5M5t6UAHDls+mnKthSAcX0pAEFFPxa+QDX+92sUCntnG+HhSyn2VT1V0NC7v6pvT0Li8Ug2k58vJXRNV3JS2EBCihYLEJyZjsZh8joqFRqnAuo73VJYpCRufJkMS2NzBKKxqVf6SThU03HoTQIviUPFTFfA7jeOw+lRXgkxzodEsA1YAVH2tAESqYL00DkCZUSNIqLBXdl5Ku01f4OgloWY0RZ8\\/R\\/c4pO8CrPGfyn7NhNxluu+FVrNH0cAJgGF2K7yejl7mgnZ05RueDieu5buJ9CxBL5W36cPb3dr70D75\\/XDw+bhx35+hGC\\/CLq+L9oYD\\/7tZ9TLO3YrfPc4aoYL11FOxI8uuB7VyxWxK9SQlAzRGMNtZIyMO23DUNmUWXoz4xyZZRxvizrtDfSGSyvLgl4JSfhtEZJMW2YHCMRZq2ShvjkDUJIhkkP5tvpdMoRCCRSewh2LW9qfwkmUHbwz7pe71TtvRvoMKdnPLexqQskRS6QdyFny6MT7U64+828+uPcki9ZTLY0wtaMXn0pic7UqfDIEHh8i+DCXOjVlpR+G0NFFrBHIHxwF6UGGJfIkUuWjMYaRl9H3xp39jWyI7TODIPYu\\/5k3oFsqO6aMdPoAjKiWiBc98EjkIe8TtIn3+z7ArQZ5jib\\/p9XTu9Vu4w3br5\\/Wm4d+ih5NUmgxkmXJDmySVkfOCFURfp590g3HvMzFDx9W4p0\\/TikTWU7WCqe0LNACUcbVFw8xBu7lDsYYY480pKACsDkMHcmTCrdLm2VU94zhNJesLf48izESN6EDjGHPwBhyBmPULTsZY6g51WhOZIzLjJMzxiWiFGKyHhJgB6yRSh7nYg2ECopqpaSRVPmtoYKRebEBmg8I+vs2JKubxRqJw9o4a\\/g3f0zWqFuabqxxTtY4UIk3ZQ1IMvGdizUUZeKz1hVODew0jeNa2lLeNwPSCslUqHE4izViJjjEGvCRWaN+xXtjjXOyhrd7T2aNYzSN6awBjWVOCl0EoiY1ROosdAooPQF+hM\\/pmnCGlW2UbsYZkTtMzBl5eQNUcsyS87zVLv2nglbCoAJ6qLAfMra\\/AUcmkK2BRra2kLbH540POR2\\/Wvua5X\\/arTfDEtWOUfxsjSUGsCFX3XOyRCk8Mv\\/+cd11uIAtBR1LPRNGcgNTeCSVsRmrGLcfolGoWTtVJmBjAXWDLLVFQJQCBK6cjagCdi6IKHYaoqryR46oC4PoqAoBLwkjsKALu8NhGCnjtBjWXTkaRiIf+vpgVD2rXx5GByS9jwYjnGNkMDgCRlZK5VJD2gkwgnzo64NRtUDgy8PogFT00WBknXDD8jrjMKLQXfw0zZkJRU+Pd+7y95\\/dsVYMn+VBUjuLd9clEzapffgsrcpiXkCzF06ZRJIkjFTqzEjia0egBsenF5KpkqSWQ8st2bVnNXP9fX5YlblZzeiTUd20IS\\/taGdOElTOARUjPr2eYJUk9dQYrKARXRtJ4rRm0s4niW0k3ZGzIkmiztjxzPjUneQDKcQgOEJoHcoSjPtYJATZd0jSLekiVC4jp+K+VYVj0eKhq7kRDvpDouxkAW0NUtsnQ25bup8gmEV9m5bxr9uHD2Tz+MP2P\\/Asik9ASeMJr8dO8bPAE3D2jeS0QJ4L3RsN\\/DYuN05mLLrQOL\\/e+7byOFkW7IsYonImGxe4hlzaH+Wx5rgYC\\/2l+AYql1n1o4jcEME1UpuQwSk73aVrnHEuP0qcFQLwh1GCpzH5zrYTOiC3w37fiuPJmPO5n90S8lbOlr6CsQIFRmimrRiNxZGUP2rhCwqxkLKvb2l\\/Aqq9uAX7gPzd5qewez2s9xntguSOvwGrJwU7v+Q+Ba4Xi4nnwwqNFzGuFYh4UA9JUNDBcHD\\/PRL2g3qIAyVl1VybDNEYJoWzJRRK1nAOPLv\\/1kZyRYXpuDQsTfRIUUK8WBkuUlAjtkhC1Kz3bHI+VYXJW21XLUbj5zHy2hxNVSFMEzJw6EYLn+2va\\/Gxs54tlELlO1QU90G\\/v9ver\\/EF\\/eQ8V5BzmKnba1O4Xiwslw8rj88fp+RXni\\/uQbgOqBPgAWz4aFbmUwiuhZGmlGCu8PLGsjZrY95pGqGdksOAB8qcJ8FKAdak5jvc51RIPpkDNY5O64BKQbn7dqDk5Mx4u4uttjKnuMFPhoA+VgOq6gJwRQPhvq1r6SJyyYatvRvcX9ZPHqifPa93P24eHzvbSxuVa5ywwcurjNUEQyNRuYW42sHXHEbGcDnC+S6ZtyTxSd61i\\/3eEEcU+Mwz2g5VELZU4M95J8wwH7wVZJohhzlUGZPkfIAQYcYVL1\\/To1zuobA\\/tIObN19KtYwUlmGrU5ajYIQSiBh18xa840dDEAgE6Nt0jwduHfe\\/vv7pAymxX293T9ukXIKkTKZSVp1r08N8JMR6whl9bHj3ZBiWn4aJMy9lrk3pcBjEQ0K20YQa3PDR44JmgHLRSDxaWOIiHrK1MUoSZanCR8klUyNiLW7RJpFIR20b6dkre6FvpHYc59dp2oinGCUM0tC95\\/jwzv0nN9wwW64dF3fmFHFW0xNazqYIq1PkSo1N8RRdTJETjU3JJzdGSm2L9RXjziwE2mn8h1kN8ykCGUU6c9e1GskHU4wW5WSK7N\\/XSBSrdOL5VOpMKvVa4IYOf1RDVZpZm7J6l81\\/VXrwaGhyfGJ9AZq4VVM+j5CQwoDVUqFkK0b1BZy9F7GUbEQIR+haUKZoSxVx0pHppV+vnneUEOV3q8en1Y8P276YdHsHJrmzrOpFPtAZXKN17ONxGTfygQKBg16mWFF6PtNF1yX06gJVx0\\/yISz6bWq8WpFHG0rfVERIq5RTy5DDk1uyBI\\/7LQsaJZyC4SGCKrPmdKuMkklyrwfgkEf8ZA4Ip20p632OAGrXyDg+5FculctbdZBpUAHTVOXdSjUe+UXsvfDaiWwjv9qW7icILRzzt8yfPjxs3pCl6ctn1Kh\\/fF738yPhFJfeOjP1jvhimV9yy+hl6unxF6oPOGC9y61bXpjlAixewOU4iw+B3XOWjR5djNSYkM5pRiCuB+ZFIzRCciUL3OY7lXRGZ2VBacdxHCwlzOGxRWJcNklMElHV+pq0aK85YU4\\/RR5tw67bcE+VFq3PiUNOHwX5Pe3MKWK4phpY0xPmVClS06jsmTKSX5YiJqbI6dLi\\/pMbpnHZC4dh2hlbashwYTkZapw1g8tismyXD8MaPfauAyg5cmJPyUL+i6zVC5kWlXs6ohEqo2WEbBt2b3AzDwnQZJu3pOvB2eLX+dj\\/1cPfST78\\/n79bu8s5S9qrGQomNetNNkdxYWMy\\/BCB5Q8+zilq8ichoeuIlMQdCxgY7exURd5uopk3Jiq71d8FYlbEW+NBRlX4MnBfWaWClcYaG9rZnFFEf\\/XxhV19SnnisunBP\\/tsshEd+2cSVpQTg6xmscklc7M9cuihm5QCTGTvfGiEMeUTQbZ7yJPJ3ml4lU8xdipwp4oXiWf3BjJhU2uikudOUW0VpaB1pMzRFYpYuoUuVLxajDFaFHmU0Q3HCCuG17pTAReBYaSajCuuEmNcZpC9cqX90V6JJf3htKLCUbXfWk0fWilwhcLshRIowRQHb9RX3TBmxZojQUfsNi3tD\\/xeGHS+dQnf1mHmhKvt\\/ffr96862e38FXDURFz1ewgg139HLlfp+zql8oxm6\\/uIaNYSp4eH+mePl6WUXoL74g6sB+CyjIa6YrwkA03judFvHDvAIWimOB2kIQEFWlZEXxq+0fibaVoaOSWtKpYaBXL4FJOCWgEp6zeo1GzhjX+72uDFPGhaqBCMZSuB3mRromo6\\/PV+9VuQ1mRvv7bM2M\\/vHl3nybqohIVWk8F7TksrFNAeyFLbmGJx0E7pFEHkkLC7iwPvBQUgSNU1cKTvLyhROBRSsu40zRE0CytYQdXSkOemBNOgqvKh75WuNb9nX+7cJ0e6PhigHVWKijiowxYPF21oyoMKMen99d4CBipoSwPyPgPXdlb5SJ5AKhmAp4cSsSAHbYqTVKIJb+i7mSpyQN+tbjdu8W0LSwkhQWuLdmT8NdvkYiUC\\/bb7Yd+ZgvyjpJ4wLi6sWV4RldtLRX8qcnPlxxF05WY4l+VLmVHS2WGjx7nmiJJmFeKCb0nW6UzvXbXAFwCoMAFg5xzinIOT8YRowSOiWBp6TuZWfKhswyFnAQXaq6RMZXBTt4tX61+Mgup5RrVJrdoW7hqVFuwwzHpReNvN+9CbMfv1z9tg7UizG\\/h884ZHew0U9BEL7\\/MrqfOPk7JLzRf3oOSZUqfoPaELHP1Pc9L+taRC7MrXWEUXt6AlFIkh3TQgRCu5PSvwoVk4hdKte25JBdQmzowU5SSZcVK9VEWV\\/xDW2MjvW\\/F3\\/kdh6XQSyWrrXTJa\\/G8VKqrfl7NjxiqK\\/FGqECNtqXzA2Q4VfBGyq9XdDSvv7\\/3R\\/NftrhYq+3z+k0\\/S9r\\/LKOi9fX9b3pG6+K5mHzVFKNruiwdXYQdPnpsPmohGoUHdKRnVDqT5Oh0v6+NwROSQ2Jg5RQbwsq+wib+Q5cHODkHRVt+Tso4+yq0OXvorpDe4Zym3CeoYLFRJxXbeLsBnuXWm1fbP\\/eAcHj6e1+Vr7fvVuHZz9b32+fV0yY5ClHTB2vrTu1JSBcNcpSXZf4xB4+yZDVacmiPpN7kQwEzddOiIn5W1ZiH6AQjFw0S4vbCdaUzqXnGUXxA1ICQjiViEh6GNpx4B7aMLqXqQA0lq6qg5LzK5q34yf6wwcNVcKZNKCxSDwVqre3kXNqGAnUt7U88n6Vhfrv86tknbfry\\/fp+8+Pd1+vN3+\\/7DL2dZM+tGIsIyspKJrU5L+R8YF7IEG+HtwwXiQ\\/KKXtImUih0WMzcXI4kM7JWWPqYXPJEI2TZIwoQhOFDTy62cAMb6XhFrkRlWcXRKwo7IKjYnyYV0Q497yv\\/Z5NAdt9ojM8+XW1ldJPc9RdgKvR43Wqr73D5fIs8PXmzbvNmh79CsnaT5CYy1D0o6xfWE2tZzYB8Ec\\/ncD4aGd7N\\/HpQsTIgBCTIkYSSp7L2V5RYDqSCLXfHjWxsz1vnMWNfeCI4JTVjBtnUS5wNk3+YwQqxuXozxKMKYv2sCiBpM8EV201liqGAWr\\/bNSUI61rd3hN0Z5+VpqHy9a+D\\/lOWeZlmN95QdG92XhJ8bP14+MwnbbETb8uGQy3\\/Kode+Lufezz6a6MnzwH0KPPl1zScqIcVIMSqgZYiZA\\/KC0xYFmw2nfjjSg\\/+1f6MJGkrjd1KkoO74PIbH4l18LaSUoOn0ZvngDrOoCvEdb1HGk3WLdEOap2xgWB7aQ0IBYVYFNtzOxmswW2xv062FnnADt1Wrt6ZNfTtt2Q3SF7Wj65yyPbSDBDdPXIRuk5v7MnZONgHFgIrJ6HbL6YjGxZOfVeENn1KIEbsjtkT0txN6TreZFN1dU5StnDYlc9shH3bJjSrkO2pBIuaYaVU5A9PC+uG9n1u4MbsjtkEwjVFSBbkeGsgmxO9bpqe7YxamBPPgnZsPgVIRvqvgA3ZHfI9v4XV4Bs22ZJLCKbCpZnN70B2ZyhDM5SD8JTkD3kqutG9nRb+P+\\/yKaVzpwQXh7ZnOlBre4Y2aAZZOF5LbLJgfwMe\\/bQLHPdyBY3ZB9Eti+\\/dQXIBh6KwBaRLZ3mmTQiKWQbKMicQWrL1uQUxorIdosCsoV9lTCV8EEhauD1HVp1cCu2jEpkClIAxr2+KaaWfsqGBa9vH1sc9QAIK7QP6P1q80jXl\\/\\/jeX1\\/\\/351\\/49+eh7SuNx2pMjGMMXnRQKYC599EDjJuvWEO6ZUixSGmWpKtWSIhhujpSnTrcGlVGKYErMFEiKGGZtaj08AEuRDXyWQ6nbjFwfSdPvsy0KJ3PPZcVCiKkSMkicxIZN7Yvq9HhsUV8Ty7qwHg+FF4RuuITYonmKUk8Sw7kg4PfSacG65lsVg+LgzowiFn1JOUKNnU6QeDA+FNb4yikCUniBEqc+jCLBGkqBVrIwVdw6TWVHNdgpw1UnRuNMoUkxmFW7Br5ci3RR5KQPfacms2k9uDHcgEw201JkksxLWWKM1UiG4Ku4PQKBy1WXnlho9YiGOuTuG4h0E5A1aZRs6AEpJTUevg7EDsM\\/8bBsV4i7znNBMMed9zl4\\/rX9+t3rY+EySn+62bxafRD5gtB85XrfCn99NaooUf7FxxEvUpymQ8dBxPsBBd1Kw5DgXo8c5w52EyTpjREM0lDu0XN9SMASQZFA6zmmbUtgn3fStKpENO9awUD3O3bLk5Xn8VpWGLJ7v8FC0ezAWEcnNzYRIn9zgXiTSsIhSZ0YRzqVARndmevB1jSLF49x\\/Lpwl+PqiFImzDdlTywUln9woPIjsMItH1plTRDtkEwNycm2aKkXkr5oi+uwUoSwDZpgtNOtMjnMjtFHAqQKvSfy6pbTk+V88zmv0SOpVS4pCAbdMbiX3rWEQVC6ZcdF+Xa5MY4J1z\\/LOUxVE421LEg9C\\/2fgFHpFO83vVrvtfSjI+9XmezyINos9NfE8B3Jir\\/uqpesaare1ZPOT1U\\/P4uF29IFmXIwpiva77mZijzJZzR5XcOVCoE3+ZaLyw+c8eEh2MHJEPOn5pET4BTLgdcNp6mWto8Ip8mGkwxQnqg6u4U+BVBWSOFK9BOCcucGP+MCoAQjc\\/5sQEFp6KsE1FSL28cE1FR\\/ssoOlTnsnAAowYCVxHTcoWQjUG0b7lAWyBYnmRFCcZUWQtBIIVFMxhzJnXtAUXDGHlBA5TgEp0Cm6G8PW6lUuZbYZbsyT5VkzO26S97ooL92LX1chpJS+2DGTx\\/f3a8p0vuz7cP6qQt06yIzOBlPq3dbYmoZhFJ8Q\\/otU+Ib0sXoTBmFmNij4hukN+gZSlEcI6G3ZLiGk2Uisy2hXqAEl5yCH9KtBfcdUBPCdIplfIwP4wAK2Aadt9qQmUpxH4OLO9h4SBv3tRDoFzCdMd\\/wxnNP3we4Y7BQmOSr9dPmHxsf2LbebdJSPgwZD0aiHpOLTjcS9TjPkmEGw1wo5VBqmOGXKjM42JAvNk7KqhdbNsFfZhhx7mFKwYU5jx28NUqYtN8lYuFn4TWqRdXMREnHSuVDCkM0dM2gRXGTEA3T0uYeo+2u5bW5NM\\/ECbsWz4e+6K6lTt61Joau0K4VlRKwF0o6cdvCblvYi29h06++X3gTsxIYFPeMyiamnbEoNaL4x6wQEy9a7avoYsi9gtaPglvsyDOVOpoC\\/lvKHHW0IdDXgJxoCPz603\\/55q9ffPPpN7\\/74kBezHiK0aosTjIEJu9rqAJHVtQy68xIIplVlqmgic4kSZ6qNEwhZLG4apLI\\/v6iXZWZJKH3NUIrEVVLqHRmJMGzXnAm2rxD80hiqyQ5S7mzy5KEx1yi+GyS4DFnrLOOl0gSd+YkcRqJRQHE80ni6iQppIm+NpK4mCQn3vIl70NZkXFe2LjSziFJHMrDSjAuYP7G1TuCDKegltJcOUlUn7\\/fr4o0M0lCn4wnuGYOcpKknRlJDNBZ4pQS80nCKyTR3qR41STRvdWzXZWZJKH3Nbi0QqucJGlnRhIruaUMZfwMJBFVkojrJ4mIucTOJ4mARhsZoD7amZHEgTKk3k4thjRGEqiSBK6fJBBziZsrBNP7GpSaBCtySdw5IIljyCDAyeJwBi6RVZLIc\\/jkXpYkUvUkoVWZTRKpGh9PVyRJ3JmRhGvjqPosTKxpMUYSVSXJWeqZXpYkXenGdlVmk0SRow6PQ3srnRlJhFQgGZVsnE+SmvZuluzaNy7TZ6ELqzL3LKFPbpwEDgUuSTszklBabgrcU\\/P1ElnT3kPZ6SsnSaQqUlm72SThZMZKah1WOgNJ\\/v2f+Nv24dPHx82PD+u3365p4Vdk9XtcLP9z8dP6A\\/78t3\\/\\/ZLHb\\/of\\/7Z\\/\\/\\/H8=";
        LOGGER.info( IOUtils.toString( MimeUtility.decode( new ByteArrayInputStream( data.getBytes() ), "8bit" ), Charset.defaultCharset() ) );
    }

    @Test
    public void testDateParse() throws Exception {
        DateTimeFormatter MMM_DD_YYYY = DateTimeFormatter.ofPattern( "MMM d, yyyy" );
        LocalDate d = LocalDate.parse( "Sep 3, 2020", MMM_DD_YYYY );
        LOGGER.info( "Parsed date: " + d );
    }

    @Test
    public void testDateTimeParse() throws Exception {
        final DateTimeFormatter DD_MM_YYYY_HH_MM = DateTimeFormatter.ofPattern( "dd/MM/yyyy hh:mm a" );
        LocalDateTime d = LocalDateTime.parse( "18/04/2019 08:11 AM", DD_MM_YYYY_HH_MM );
        LOGGER.info( "Parsed datetime: " + d );
    }

    @Test
    public void testMatchSession() throws Exception {
        Pattern p = Pattern.compile( "ses=([a-f\\d]+)" );
        Matcher m = p.matcher( "https://admin.booking.com/hotel/hoteladmin/extranet_ng/manage/home.html?hotel_id=637927&ses=bec89bb1d4613a5c98066756167c270e&lang=en" );
        if ( m.find() ) {
            LOGGER.info( "Session is " + m.group( 1 ) );
        }
    }

    @Test
    public void testJsonGeneration() throws Exception {
        LOGGER.info( gson.toJson( getDetailedRates( LocalDate.parse( "2020-12-21" ), LocalDate.parse( "2020-12-28" ), 10 ) ) );
    }

    private JsonObject[] getDetailedRates( LocalDate startDate, LocalDate endDate, int ratePerDay ) {
        final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern( "yyyy-MM-dd" );
        List<JsonObject> result = new ArrayList<>();
        for ( LocalDate cursor = startDate; cursor.isBefore( endDate ); cursor = cursor.plusDays( 1 ) ) {
            JsonObject e = new JsonObject();
            e.addProperty( "date", cursor.format( YYYY_MM_DD ) );
            e.addProperty( "rate", 10 );
            e.addProperty( "adults", 0 );
            e.addProperty( "kids", 0 );
            result.add( e );
        }
        return result.toArray( new JsonObject[0] );
    }

    @Test
    public void testRandom() throws Exception {
        String style = "width:422px; left: -336px";
        Pattern left = Pattern.compile( "; left: ([\\-0-9]*)px;" );
        Matcher m = left.matcher( style );
        if ( m.find() ) {
            String leftOffset = m.group( 1 );
            LOGGER.info( "offset is " + leftOffset );
            if ( Integer.parseInt( leftOffset ) < 0 ) {
                LOGGER.warn( "offset off screen, skipping " );
            }
        }
        else {
            LOGGER.warn( "pattern not found" );
        }
    }

    @Test
    public void testPatternMatching() throws Exception {
        String value = "12-06 Touchy-Feely";

        Pattern p = Pattern.compile( "([^\\-]*)-(.*)$" ); // anything but dash for room #, everything else for bed
        Matcher m = p.matcher( value );
        String room = null, bed = null;
        if ( m.find() == false ) {
            LOGGER.warn( "Couldn't determine bed name from '" + value + "'. Is it a private?" );
            room = value;
        }
        else {
            room = m.group( 1 );
            bed = m.group( 2 );
        }
        assertEquals( "12", room );
        assertEquals( "06 Touchy-Feely", bed );
    }
}
