
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.dao.WordPressDAO;

/**
 * Convenience class for creating JSON requests for Cloudbeds.
 */
@Component
public class CloudbedsJsonRequestFactory {
    
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern( "yyyy-MM-dd" );
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern( "dd/MM/yyyy" );
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("###0.00");

    @Value( "${cloudbeds.property.id:0}" )
    private String PROPERTY_ID;

    @Autowired
    private WordPressDAO dao;
    
    private String BILLING_PORTAL_ID = "37077"; // ??? is this going to change
    
    // a default version which we'll probably get prompted to update
    private static final String DEFAULT_VERSION = "https://static1.cloudbeds.com/myfrontdesk-front/initial-12.0/app.js.gz";
    
    // the current cloudbeds version we're using
    private String version;

    /**
     * Returns the current cloudbeds version we're requesting against.
     * 
     * @return non-null version
     */
    public synchronized String getVersion() {
        if ( version == null ) {
            String currentVersion = dao.getOption( "hbo_cloudbeds_version" );
            version = StringUtils.isNotBlank( currentVersion ) ? currentVersion : DEFAULT_VERSION;
        }
        return version;
    }

    /**
     * Sets the cloudbeds version to use for all future requests.
     * 
     * @param newVersion non-null version
     */
    public synchronized void setVersion( String newVersion ) {
        dao.setOption( "hbo_cloudbeds_version", newVersion );
        version = newVersion;
    }

    protected WebRequest createBaseJsonRequest( String url ) throws IOException {
        WebRequest requestSettings = new WebRequest( new URL( url ), HttpMethod.POST );
        requestSettings.setAdditionalHeader( "Accept", "application/json, text/javascript, */*; q=0.01" );
        requestSettings.setAdditionalHeader( "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8" );
        requestSettings.setAdditionalHeader( "Referer", "https://hotels.cloudbeds.com/connect/" + PROPERTY_ID );
        requestSettings.setAdditionalHeader( "Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8" );
        requestSettings.setAdditionalHeader( "Accept-Encoding", "gzip, deflate, br" );
        requestSettings.setAdditionalHeader( "X-Requested-With", "XMLHttpRequest" );
        requestSettings.setAdditionalHeader( "Cache-Control", "max-age=0" );
        requestSettings.setAdditionalHeader( "Origin", "https://hotels.cloudbeds.com" );
        requestSettings.setAdditionalHeader( "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36" );
        return requestSettings;
    }

    /**
     * Returns a user has CC view permissions request for this property.
     * 
     * @return PaymentRequest for this property
     * @throws IOException invalid URL
     */
    public WebRequest createGetUserHasViewCCPermisions() throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/cc_passwords/user_have_ccp_view_permission" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Ping responds with pong. Doesn't require a login.
     * 
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createPingRequest() throws IOException {
        return createBaseJsonRequest( "https://hotels.cloudbeds.com/error/ping" );
    }

    /**
     * Returns a single reservation request.
     * 
     * @param reservationId unique ID of reservation
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetReservationRequest( String reservationId ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/reservations/get_reservation" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "id", reservationId ),
                new NameValuePair( "billing_portal_id", BILLING_PORTAL_ID ),
                new NameValuePair( "is_identifier", "0" ),
                new NameValuePair( "is_bp_setup_completed", "1" ),
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Get info on all customers checking in on the given date range.
     * @param checkinDateStart checkin date (inclusive)
     * @param checkinDateEnd checkin date (inclusive)
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetCustomersRequest( LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/hotel/get_customers" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "sEcho", "2" ),
                new NameValuePair( "iColumns", "3" ),
                new NameValuePair( "sColumns", ",," ),
                new NameValuePair( "iDisplayStart", "0" ),
                new NameValuePair( "iDisplayLength", "1000" ),
                new NameValuePair( "mDataProp_0", "first_name" ),
                new NameValuePair( "bRegex_0", "false" ),
                new NameValuePair( "bSearchable_0", "true" ),
                new NameValuePair( "bSortable_0", "true" ),
                new NameValuePair( "mDataProp_1", "last_name" ),
                new NameValuePair( "bRegex_1", "false" ),
                new NameValuePair( "bSearchable_1", "true" ),
                new NameValuePair( "bSortable_1", "true" ),
                new NameValuePair( "mDataProp_2", "email" ),
                new NameValuePair( "bRegex_2", "false" ),
                new NameValuePair( "bSearchable_2", "true" ),
                new NameValuePair( "bSortable_2", "true" ),
                new NameValuePair( "iSortCol_0", "1" ),
                new NameValuePair( "sSortDir_0", "asc" ),
                new NameValuePair( "iSortingCols", "1" ),
                new NameValuePair( "date_start[0]", checkinDateStart.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_start[1]", checkinDateEnd.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_end[0]", "" ),
                new NameValuePair( "date_end[1]", "" ),
                new NameValuePair( "repeating_and_new_guests", "all" ),
                new NameValuePair( "country_code", "all" ),
                new NameValuePair( "guest_status", "all" ),
                new NameValuePair( "billing_portal_id", BILLING_PORTAL_ID ),
                new NameValuePair( "is_bp_setup_completed", "1" ),
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Get info on all customers including cancelled bookings by searching on a generic term.
     * 
     * @param query whatever you want to search by
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetReservationsRequest( String query ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/reservations/get_reservations" );
        webRequest.setRequestParameters( getCommonReservationsQueryParameters(
                new NameValuePair( "date_start[0]", "" ),
                new NameValuePair( "date_start[1]", "" ),
                new NameValuePair( "date_end[0]", "" ),
                new NameValuePair( "date_end[1]", "" ),
                new NameValuePair( "booking_date[0]", "" ),
                new NameValuePair( "booking_date[1]", "" ),
                new NameValuePair( "status", "all" ),
                new NameValuePair( "query", query ) ) );
        return webRequest;
    }

    /**
     * Retrieves all reservations within the given checkin date range.
     * 
     * @param checkinDateStart checkin date (inclusive)
     * @param checkinDateEnd checkin date (inclusive)
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetReservationsRequestByCheckinDate( LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/reservations/get_reservations" );
        webRequest.setRequestParameters( getCommonReservationsQueryParameters(
                new NameValuePair( "date_start[0]", checkinDateStart.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_start[1]", checkinDateEnd.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_end[0]", "" ),
                new NameValuePair( "date_end[1]", "" ),
                new NameValuePair( "booking_date[0]", "" ),
                new NameValuePair( "booking_date[1]", "" ) ) );
        return webRequest;
    }

    /**
     * Retrieves all reservations staying within the given date range.
     * 
     * @param stayDateStart checkin date (inclusive)
     * @param stayDateEnd checkin date (inclusive)
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetReservationsRequestByStayDate( LocalDate stayDateStart, LocalDate stayDateEnd ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/reservations/get_reservations" );
        webRequest.setRequestParameters( getCommonReservationsQueryParameters(
                new NameValuePair( "date_start[0]", "" ),
                new NameValuePair( "date_start[1]", "" ),
                new NameValuePair( "date_end[0]", "" ),
                new NameValuePair( "date_end[1]", "" ),
                new NameValuePair( "date_stay[0]", stayDateStart.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_stay[1]", stayDateEnd.format( YYYY_MM_DD ) ),
                new NameValuePair( "booking_date[0]", "" ),
                new NameValuePair( "booking_date[1]", "" ) ) );
        return webRequest;
    }

    /**
     * Returns a new modifiable list of common parameters to the {@code get_reservations} request.
     * 
     * @param additionalParams additional parameters to add to return list
     * @return new non-null modifiable list
     */
    private List<NameValuePair> getCommonReservationsQueryParameters( NameValuePair... additionalParams ) {
        List<NameValuePair> params = Arrays.asList(
                new NameValuePair( "sEcho", "2" ),
                new NameValuePair( "iColumns", "8" ),
                new NameValuePair( "sColumns", ",,,,,,," ),
                new NameValuePair( "iDisplayStart", "0" ),
                new NameValuePair( "iDisplayLength", "1000" ),
                new NameValuePair( "mDataProp_0", "id" ),
                new NameValuePair( "bRegex_0", "false" ),
                new NameValuePair( "bSearchable_0", "true" ),
                new NameValuePair( "bSortable_0", "false" ),
                new NameValuePair( "mDataProp_1", "identifier" ),
                new NameValuePair( "bRegex_1", "false" ),
                new NameValuePair( "bSearchable_1", "true" ),
                new NameValuePair( "bSortable_1", "true" ),
                new NameValuePair( "mDataProp_2", "first_name" ),
                new NameValuePair( "bRegex_2", "false" ),
                new NameValuePair( "bSearchable_2", "true" ),
                new NameValuePair( "bSortable_2", "true" ),
                new NameValuePair( "mDataProp_3", "last_name" ),
                new NameValuePair( "bRegex_3", "false" ),
                new NameValuePair( "bSearchable_3", "true" ),
                new NameValuePair( "bSortable_3", "true" ),
                new NameValuePair( "mDataProp_4", "booking_date" ),
                new NameValuePair( "bRegex_4", "false" ),
                new NameValuePair( "bSearchable_4", "true" ),
                new NameValuePair( "bSortable_4", "true" ),
                new NameValuePair( "mDataProp_5", "checkin_date" ),
                new NameValuePair( "bRegex_5", "false" ),
                new NameValuePair( "bSearchable_5", "true" ),
                new NameValuePair( "bSortable_5", "true" ),
                new NameValuePair( "mDataProp_6", "checkout_date" ),
                new NameValuePair( "bRegex_6", "false" ),
                new NameValuePair( "bSearchable_6", "true" ),
                new NameValuePair( "bSortable_6", "true" ),
                new NameValuePair( "mDataProp_7", "grand_total" ),
                new NameValuePair( "bRegex_7", "false" ),
                new NameValuePair( "bSearchable_7", "true" ),
                new NameValuePair( "bSortable_7", "true" ),
                new NameValuePair( "iSortCol_0", "3" ),
                new NameValuePair( "sSortDir_0", "asc" ),
                new NameValuePair( "iSortingCols", "1" ),
                new NameValuePair( "status", "confirmed,not_confirmed,checked_in,checked_out,no_show" ),
                new NameValuePair( "billing_portal_id", BILLING_PORTAL_ID ),
                new NameValuePair( "is_bp_setup_completed", "1" ),
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", getVersion() ) );

        // copy above into a map so we can remove the ones we are replacing
        Map<String, NameValuePair> paramMap = new HashMap<>();
        for ( NameValuePair nvp : params ) {
            paramMap.put( nvp.getName(), nvp );
        }

        // remove, re-add any custom keys
        for ( NameValuePair nvp : additionalParams ) {
            paramMap.remove( nvp.getName() );
            paramMap.put( nvp.getName(), nvp );
        }

        return paramMap.entrySet().stream()
                .map( es -> es.getValue() )
                .collect( Collectors.toList() );
    }

    /**
     * Records a new payment onto the existing reservation.
     * 
     * @param reservationId ID of reservation (as it appears in the URL)
     * @param bookingRoomId (which "room" we're booking the payment to)
     * @param cardType one of "mastercard", "visa". Anything else will blank the field.
     * @param amount amount to record
     * @param description description
     * @return web request
     * @throws IOException on creation failure
     */
    public WebRequest createAddNewPaymentRequest( String reservationId, String bookingRoomId, String cardType, BigDecimal amount, String description ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/hotel/add_new_payment" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "payment_type", "cards" ),
                new NameValuePair( "choose_card",
                        cardType.equalsIgnoreCase( "mastercard" ) ? "master" : 
                        cardType.equalsIgnoreCase( "visa" ) ? "visa" : "" ),
                new NameValuePair( "assign_to", bookingRoomId ),
                new NameValuePair( "paid", CURRENCY_FORMAT.format( amount ) ),
                new NameValuePair( "payment_date", LocalDate.now().format( DD_MM_YYYY ) ),
                new NameValuePair( "cash_drawer_option", "add-to-opened-drawer" ),
                new NameValuePair( "description", description ),
                new NameValuePair( "process_payment", "0" ),
                new NameValuePair( "refund_payment", "0" ),
                new NameValuePair( "auth_payment", "0" ),
                new NameValuePair( "keep_credit_card_info", "0" ),
                new NameValuePair( "booking_id", reservationId ),
                new NameValuePair( "billing_portal_id", BILLING_PORTAL_ID ),
                new NameValuePair( "is_bp_setup_completed", "1" ),
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Records a new note onto the existing reservation.
     * @param reservationId ID of reservation (as it appears in the URL)
     * @param note note to be added
     * @return web request
     * @throws IOException on creation failure
     */
    public WebRequest createAddNoteRequest( String reservationId, String note ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/notes/add_reservation_note" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "reservation_id", reservationId ),
                new NameValuePair( "notes", note ),
                new NameValuePair( "billing_portal_id", BILLING_PORTAL_ID ),
                new NameValuePair( "is_bp_setup_completed", "1" ),
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Records a new credit card onto the existing reservation.
     * @param reservationId ID of reservation (as it appears in the URL)
     * @param cardDetails card details to be added
     * @return web request
     * @throws IOException on creation failure
     */
    public WebRequest createAddCreditCardRequest( String reservationId, CardDetails cardDetails ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/hotel/save_credit_card" );
        List<NameValuePair> params = new ArrayList<>( Arrays.asList(
                new NameValuePair( "booking_id", reservationId ),
                new NameValuePair( "card_type", cardDetails.getCloudbedsCardTypeFromBinRange() ),
                new NameValuePair( "card_number", cardDetails.getCardNumber() ),
                new NameValuePair( "card_name", cardDetails.getName().toUpperCase() ),
                new NameValuePair( "year_expir", "20" + cardDetails.getExpiry().substring( 2 ) ),
                new NameValuePair( "mon_expir", cardDetails.getExpiry().substring( 0, 2 ) ),
                new NameValuePair( "announcementsLast", "" ),
                new NameValuePair( "is_active", "1" ),
                new NameValuePair( "billing_portal_id", BILLING_PORTAL_ID ), 
                new NameValuePair( "is_bp_setup_completed", "1" ),
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", getVersion() ) ) );

        // CVV optional
        if( StringUtils.isNotBlank( cardDetails.getCvv() ) ) {
            params.add( new NameValuePair( "cvv", cardDetails.getCvv() ) );
        }
        webRequest.setRequestParameters( params );
        return webRequest;
    }
}
