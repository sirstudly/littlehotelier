
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;

/**
 * Convenience class for creating JSON requests for Cloudbeds.
 */
@Component
public class CloudbedsJsonRequestFactory {
    
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern( "yyyy-MM-dd" );
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern( "dd/MM/yyyy" );
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("###0.00");

    @Value( "${cloudbeds.property.id}" )
    protected String PROPERTY_ID;
    
    private static final String VERSION = "https://static1.cloudbeds.com/myfrontdesk-front/initial-10.1/app.js.gz";

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

    public WebRequest createGetReservationRequest( String reservationId ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/reservations/get_reservation" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "id", reservationId ),
                new NameValuePair( "billing_portal_id", "0" ),
                new NameValuePair( "is_identifier", "0" ),
                new NameValuePair( "is_bp_setup_completed", "0" ),
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", VERSION ) ) );
        return webRequest;
    }

    public WebRequest createGetCustomersRequest( LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/hotel/get_customers" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "sEcho", "2" ),
                new NameValuePair( "iColumns", "3" ),
                new NameValuePair( "sColumns", ",," ),
                new NameValuePair( "iDisplayStart", "0" ),
                new NameValuePair( "iDisplayLength", "100" ),
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
                new NameValuePair( "billing_portal_id", "0" ),
                new NameValuePair( "is_bp_setup_completed", "0" ),
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", VERSION ) ) );
        return webRequest;
    }

    public WebRequest createGetReservationsRequest( LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/reservations/get_reservations" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "sEcho", "2" ),
                new NameValuePair( "iColumns", "8" ),
                new NameValuePair( "sColumns", ",,,,,,," ),
                new NameValuePair( "iDisplayStart", "0" ),
                new NameValuePair( "iDisplayLength", "100" ),
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
                new NameValuePair( "date_start[0]", checkinDateStart.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_start[1]", checkinDateEnd.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_end[0]", "" ),
                new NameValuePair( "date_end[1]", "" ),
                new NameValuePair( "booking_date[0]", "" ),
                new NameValuePair( "booking_date[1]", "" ),
                new NameValuePair( "status", "all" ),
                new NameValuePair( "billing_portal_id", "0" ),
                new NameValuePair( "is_bp_setup_completed", "0" ),
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", VERSION ) ) );
        return webRequest;
    }

    /**
     * Records a new payment onto the existing reservation.
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
                new NameValuePair( "billing_portal_id", "0" ),
                new NameValuePair( "is_bp_setup_completed", "0" ),
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", VERSION ) ) );
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
                new NameValuePair( "billing_portal_id", "0" ),
                new NameValuePair( "is_bp_setup_completed", "0" ),
                new NameValuePair( "property_id", PROPERTY_ID ),
                new NameValuePair( "group_id", PROPERTY_ID ),
                new NameValuePair( "version", VERSION ) ) );
        return webRequest;
    }
}
