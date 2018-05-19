package com.macbackpackers.scrapers;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.google.common.base.Optional;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.macbackpackers.beans.cloudbeds.responses.BookingRoom;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.RecordPaymentFailedException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.services.FileService;

@Component
@Scope( "prototype" )
public class CloudbedsScraper {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Autowired
    @Qualifier( "webClient" )
    private WebClient webClient;

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Autowired
    private FileService fileService;
    
    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;
    
    @Value("${cloudbeds.property.id}")
    private String PROPERTY_ID;
    
    public HtmlPage login(String username, String password) throws IOException {
        URL url = new URL("https://hotels.cloudbeds.com/auth/login");
        WebRequest requestSettings = new WebRequest(url, HttpMethod.POST);

        requestSettings.setAdditionalHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        requestSettings.setAdditionalHeader("Content-Type", "application/x-www-form-urlencoded");
        requestSettings.setAdditionalHeader("Referer", "https://hotels.cloudbeds.com/auth/login");
        requestSettings.setAdditionalHeader("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8");
        requestSettings.setAdditionalHeader("Accept-Encoding", "gzip, deflate, br");
//        requestSettings.setAdditionalHeader("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.3");
//        requestSettings.setAdditionalHeader("X-Requested-With", "XMLHttpRequest");
//        requestSettings.setAdditionalHeader("Cache-Control", "max-age=0");
        requestSettings.setAdditionalHeader( "Cache-Control", "no-cache" );
//        requestSettings.setAdditionalHeader("Pragma", "no-cache");
        requestSettings.setAdditionalHeader("Origin", "https://hotels.cloudbeds.com");
        requestSettings.setAdditionalHeader( "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36" );
        requestSettings.setAdditionalHeader( "Upgrade-Insecure-Requests", "1" );

//        requestSettings.setRequestBody("REQUESTBODY");
        requestSettings.setRequestParameters(Arrays.asList( 
                new NameValuePair("email", username),
                new NameValuePair("password", password),
                new NameValuePair("return_url", "")));

        HtmlPage redirectPage = webClient.getPage(requestSettings);
        LOGGER.info( "Going to: " + redirectPage.getBaseURI() );
        
        if( redirectPage.getBaseURI().contains( "/login" ) ) {
            throw new UnrecoverableFault( "Login failed." );
        }
        
        fileService.writeCookiesToFile( webClient );
        
        LOGGER.info( redirectPage.asXml() );
        return redirectPage;
    }
    
    public HtmlPage loadDashboard() throws IOException {
        fileService.loadCookiesFromFile( webClient );
        HtmlPage loadedPage = webClient.getPage( "https://hotels.cloudbeds.com/connect/" + PROPERTY_ID );
        LOGGER.info( loadedPage.getUrl().toString() );
        LOGGER.info( loadedPage.asXml() );
        if(loadedPage.getUrl().getPath().contains( "/login" )) {
            LOGGER.info( "nope, not logged in" );
        }
        else {
            LOGGER.info( "ok, we're good!" );
        }
        return loadedPage;
    }
    
    public Reservation getReservation( String reservationId ) throws IOException {

        // TO BE REMOVED
        fileService.loadCookiesFromFile( webClient );
        // TO BE REMOVED
        
        WebRequest requestSettings = jsonRequestFactory.createGetReservationRequest( reservationId );

        Page redirectPage = webClient.getPage(requestSettings);
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        LOGGER.info( redirectPage.getWebResponse().getContentAsString() );

        return gson.fromJson( redirectPage.getWebResponse().getContentAsString(), Reservation.class );
    }

    public List<Customer> getCustomers( LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        
        // TO BE REMOVED
        fileService.loadCookiesFromFile( webClient );
        // TO BE REMOVED

        WebRequest requestSettings = jsonRequestFactory.createGetCustomersRequest( checkinDateStart, checkinDateEnd );

        Page redirectPage = webClient.getPage(requestSettings);
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        LOGGER.info( redirectPage.getWebResponse().getContentAsString() );
        
        JsonElement jelement = gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        JsonObject jobject = jelement.getAsJsonObject();
        JsonArray jarray = jobject.getAsJsonArray( "aaData" );
        return Arrays.asList( gson.fromJson( jarray, Customer[].class ) );
    }

    public List<Customer> getReservations( LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        
        // TO BE REMOVED
        fileService.loadCookiesFromFile( webClient );
        // TO BE REMOVED

        WebRequest requestSettings = jsonRequestFactory.createGetReservationsRequest( checkinDateStart, checkinDateEnd );

        Page redirectPage = webClient.getPage(requestSettings);
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        LOGGER.info( redirectPage.getWebResponse().getContentAsString() );
        
        JsonElement jelement = gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        JsonObject jobject = jelement.getAsJsonObject();
        JsonArray jarray = jobject.getAsJsonArray( "aaData" );
        return Arrays.asList( gson.fromJson( jarray, Customer[].class ) );
    }

    public void addPayment( String reservationId, String cardType, BigDecimal amount, String description ) throws IOException, RecordPaymentFailedException {
        
        // first we need to find a "room" we're booking to
        // it doesn't actually map to a room, just an assigned guest
        // it doesn't even have to be an allocated room
        Reservation reservation = getReservation( reservationId );
        if ( reservation.getBookingRooms() == null || reservation.getBookingRooms().isEmpty() ) {
            throw new MissingUserDataException( "Unable to find allocation to assign payment to!" );
        }

        // just take the first one
        String bookingRoomId = reservation.getBookingRooms().get( 0 ).getId();

        WebRequest requestSettings = jsonRequestFactory.createAddNewPaymentRequest( 
                reservationId, bookingRoomId, cardType, amount, description );

        Page redirectPage = webClient.getPage(requestSettings);
        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        LOGGER.info( redirectPage.getWebResponse().getContentAsString() );

        // throw a wobbly if response not successful
        JsonElement jelement = gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        JsonObject jobject = jelement.getAsJsonObject();
        if ( false == jobject.get( "success" ).getAsBoolean() ) {
            throw new RecordPaymentFailedException( jobject.get( "message" ).getAsString() );
        }
    }
    
    public void addNote( String reservationId, String note ) throws IOException {

        // TO BE REMOVED
        fileService.loadCookiesFromFile( webClient );
        // TO BE REMOVED

        WebRequest requestSettings = jsonRequestFactory.createAddNoteRequest( reservationId, note );

        Page redirectPage = webClient.getPage(requestSettings);
        LOGGER.info( "POST: " + redirectPage.getUrl().getPath() );
        LOGGER.info( redirectPage.getWebResponse().getContentAsString() );

        // throw a wobbly if response not successful
        JsonElement jelement = gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        JsonObject jobject = jelement.getAsJsonObject();
        if ( false == jobject.get( "success" ).getAsBoolean() ) {
            throw new IOException( jobject.get( "message" ).getAsString() );
        }
    }
}