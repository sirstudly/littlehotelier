
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.cloudbeds.responses.EmailTemplateInfo;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;

/**
 * Convenience class for creating JSON requests for Cloudbeds.
 */
@Component
public class CloudbedsJsonRequestFactory {

    private final DateTimeFormatter YYYY_MM_DD = DateTimeFormatter.ofPattern( "yyyy-MM-dd" );
    private final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern( "dd/MM/yyyy" );
    private final DecimalFormat CURRENCY_FORMAT = new DecimalFormat( "###0.00" );

    @Autowired
    private WordPressDAO dao;

    // a default version which we'll probably get prompted to update
    private static final String DEFAULT_VERSION = "https://static1.cloudbeds.com/myfrontdesk-front/initial-12.0/app.js.gz";

    // the cloudbeds property id
    private String propertyId;
    // the current cloudbeds version we're using
    private String version;
    // the currently loaded user agent (cached)
    private String userAgent;
    // the cookies in use (cached)
    private String cookies;

    /**
     * Retrieves the current Cloudbeds property ID.
     * 
     * @return non-null property ID
     */
    public String getPropertyId() {
        if ( propertyId == null ) {
            propertyId = dao.getOption( "hbo_cloudbeds_property_id" );
            if ( propertyId == null ) {
                throw new MissingUserDataException( "Missing Cloudbeds property ID" );
            }
        }
        return propertyId;
    }

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
     * Retrieves the current Cloudbeds user-agent.
     * 
     * @return non-null user agent
     */
    public String getUserAgent() {
        if ( userAgent == null ) {
            userAgent = dao.getOption( "hbo_cloudbeds_useragent" );
            if ( userAgent == null ) {
                throw new MissingUserDataException( "Missing Cloudbeds session (user-agent)" );
            }
        }
        return userAgent;
    }

    /**
     * Retrieves the current Cloudbeds cookies.
     * 
     * @return non-null cookies
     */
    public String getCookies() {
        if( cookies == null ) {
            cookies = dao.getOption( "hbo_cloudbeds_cookies" );
            if ( cookies == null ) {
                throw new MissingUserDataException( "Missing Cloudbeds session cookies" );
            }
        }
        return cookies;
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
        requestSettings.setAdditionalHeader( "Referer", "https://hotels.cloudbeds.com/connect/" + getPropertyId() );
        requestSettings.setAdditionalHeader( "Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8" );
        requestSettings.setAdditionalHeader( "Accept-Encoding", "gzip, deflate, br" );
        requestSettings.setAdditionalHeader( "X-Requested-With", "XMLHttpRequest" );
        requestSettings.setAdditionalHeader( "X-Used-Method", "common.ajax" );
        requestSettings.setAdditionalHeader( "Cache-Control", "max-age=0" );
        requestSettings.setAdditionalHeader( "Origin", "https://hotels.cloudbeds.com" );
        requestSettings.setAdditionalHeader( "User-Agent", getUserAgent() );
        requestSettings.setAdditionalHeader( "Cookie", getCookies() );
        requestSettings.setCharset( StandardCharsets.UTF_8 );
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
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
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
                new NameValuePair( "is_identifier", "0" ),
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Get info on all customers checking in on the given date range.
     * 
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
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
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
     * Retrieves all reservations within the given checkin/stay date range.
     * 
     * @param stayDateStart checkin date (inclusive)
     * @param stayDateEnd checkin date (inclusive)
     * @param checkinDateStart checkin date (inclusive)
     * @param checkinDateEnd checkin date (inclusive)
     * @param statuses comma-delimited list of statuses (optional)
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetReservationsRequest( LocalDate stayDateStart, LocalDate stayDateEnd,
            LocalDate checkinDateStart, LocalDate checkinDateEnd, String statuses ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/reservations/get_reservations" );
        webRequest.setRequestParameters( getCommonReservationsQueryParameters(
                new NameValuePair( "status", StringUtils.isBlank(statuses) ? "all" : statuses ),
                new NameValuePair( "date_start[0]", checkinDateStart == null ? "" : checkinDateStart.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_start[1]", checkinDateEnd == null ? "" : checkinDateEnd.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_end[0]", "" ),
                new NameValuePair( "date_end[1]", "" ),
                new NameValuePair( "date_stay[0]", stayDateStart == null ? "" : stayDateStart.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_stay[1]", stayDateEnd == null ? "" : stayDateEnd.format( YYYY_MM_DD ) ),
                new NameValuePair( "booking_date[0]", "" ),
                new NameValuePair( "booking_date[1]", "" ) ) );
        return webRequest;
    }

    /**
     * Get all reservations matching the given booking source(s) and booked dates.
     * 
     * @param checkinDateStart checkin date (inclusive)
     * @param checkinDateEnd checkin date (inclusive)
     * @param bookedDateStart booked date (inclusive)
     * @param bookedDateEnd booked date (inclusive)
     * @param bookingSourceIds comma-delimited list of booking source Id(s)
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetReservationsRequestByBookingSource( 
            LocalDate checkinDateStart, LocalDate checkinDateEnd, LocalDate bookedDateStart,
            LocalDate bookedDateEnd, String bookingSourceIds ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/reservations/get_reservations" );
        webRequest.setRequestParameters( getCommonReservationsQueryParameters(
                new NameValuePair( "date_start[0]", checkinDateStart == null ? "" : checkinDateStart.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_start[1]", checkinDateEnd == null ? "" : checkinDateEnd.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_end[0]", "" ),
                new NameValuePair( "date_end[1]", "" ),
                new NameValuePair( "booking_date[0]", bookedDateStart == null ? "" : bookedDateStart.format( YYYY_MM_DD ) ),
                new NameValuePair( "booking_date[1]", bookedDateEnd == null ? "" : bookedDateEnd.format( YYYY_MM_DD ) ),
                new NameValuePair( "status", "all" ),
                new NameValuePair( "source", bookingSourceIds ) ) );
        return webRequest;
    }

    /**
     * Get all cancelled reservations matching the given booking source(s) and booked dates.
     * 
     * @param checkinDateStart checkin date (inclusive)
     * @param checkinDateEnd checkin date (inclusive)
     * @param cancelDateStart cancellation date (inclusive)
     * @param cancelDateEnd cancellation date (inclusive)
     * @param bookingSourceIds comma-delimited list of booking source Id(s)
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetCancelledReservationsRequestByBookingSource( LocalDate checkinDateStart,
            LocalDate checkinDateEnd, LocalDate cancelDateStart, LocalDate cancelDateEnd,
            String bookingSourceIds ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/reservations/get_reservations" );
        webRequest.setRequestParameters( getCommonReservationsQueryParameters(
                new NameValuePair( "date_start[0]", checkinDateStart.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_start[1]", checkinDateEnd.format( YYYY_MM_DD ) ),
                new NameValuePair( "date_end[0]", "" ),
                new NameValuePair( "date_end[1]", "" ),
                new NameValuePair( "cancellation_date[0]", cancelDateStart == null ? "" : cancelDateStart.format( YYYY_MM_DD ) ),
                new NameValuePair( "cancellation_date[1]", cancelDateEnd == null ? "" : cancelDateEnd.format( YYYY_MM_DD ) ),
                new NameValuePair( "status", "canceled" ),
                new NameValuePair( "source", bookingSourceIds ) ) );
        return webRequest;
    }

    /**
     * Retrieves all reservations staying within the given date range.
     * 
     * @param dateStart checkin date (inclusive)
     * @param dateEnd checkin date (inclusive)
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetRoomAssignmentsReport( LocalDate dateStart, LocalDate dateEnd ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/reports/get_room_assignments_report" );
        webRequest.setRequestParameters( getCommonReservationsQueryParameters(
                new NameValuePair( "booking_date[0]", dateStart.format( YYYY_MM_DD ) ),
                new NameValuePair( "booking_date[1]", dateEnd.format( YYYY_MM_DD ) ),
                new NameValuePair( "room_types", dao.getAllRoomTypeIds().stream()
                        .map( rt -> rt.toString() )
                        .collect( Collectors.joining( "," ) ) ),
                new NameValuePair( "show_assigned_unassigned", "show_assigned_rooms,show_unassigned_rooms" ),
                new NameValuePair( "view", "room_assignments_report" ),
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Retrieves all activity for a reservation.
     * 
     * @param reservationId the cloudbeds reservation id
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetActivityLog( String reservationId ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/hotel/get_activity_log" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "sEcho", "1" ),
                new NameValuePair( "iColumns", "2" ),
                new NameValuePair( "sColumns", "," ),
                new NameValuePair( "iDisplayStart", "0" ),
                new NameValuePair( "iDisplayLength", "1000" ),
                new NameValuePair( "mDataProp_0", "0" ),
                new NameValuePair( "sSearch_0", "" ),
                new NameValuePair( "bRegex_0", "false" ),
                new NameValuePair( "bSearchable_0", "true" ),
                new NameValuePair( "bSortable_0", "true" ),
                new NameValuePair( "mDataProp_1", "1" ),
                new NameValuePair( "sSearch_1", "" ),
                new NameValuePair( "bRegex_1", "false" ),
                new NameValuePair( "bSearchable_1", "true" ),
                new NameValuePair( "bSortable_1", "false" ),
                new NameValuePair( "sSearch", "" ),
                new NameValuePair( "bRegex", "false" ),
                new NameValuePair( "iSortCol_0", "0" ),
                new NameValuePair( "sSortDir_0", "desc" ),
                new NameValuePair( "iSortingCols", "1" ),
                new NameValuePair( "from_date", "" ),
                new NameValuePair( "from_time", "" ),
                new NameValuePair( "to_date", "" ),
                new NameValuePair( "to_time", "" ),
                new NameValuePair( "user", "0" ),
                new NameValuePair( "change", "" ),
                new NameValuePair( "filter", reservationId ),
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Retrieves an existing email template.
     * 
     * @param templateId the email template id
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetEmailTemplate( String templateId ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/emails/get_email_template_info" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "id", templateId ),
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Returns a request to get property details.
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetPropertyContent() throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/hotel/get_content" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "lang", "en" ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }
    
    /**
     * Sends a custom email using an email template.
     * 
     * @param template email template to use
     * @param identifier the unique id of the booking (under the name when viewing a reservation)
     * @param customerId the customer id to send email to
     * @param reservationId the reservation id (from address bar)
     * @param emailAddress email recipient
     * @param transformBodyFn apply any transforms to the email body
     * @param token captcha token
     * @return non-null web request
     * @throws IOException
     */
    public WebRequest createSendCustomEmail( EmailTemplateInfo template, String identifier, 
            String customerId, String reservationId, String emailAddress, 
            Function<String, String> transformBodyFn, String token ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/emails/send_composed_email" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "identifier", identifier ), // the unique id of reservation
                new NameValuePair( "customer_id", customerId ),
                new NameValuePair( "reservation_id", reservationId ),
                new NameValuePair( "email[design_type]", template.getDesignType() ),
                new NameValuePair( "email[email_body]", transformBodyFn == null ? template.getEmailBody() : transformBodyFn.apply( template.getEmailBody() ) ),
                new NameValuePair( "email[email_template_id]", template.getId() ),
                new NameValuePair( "email[email_type]", template.getEmailType() ),
                new NameValuePair( "email[lang]", "en" ),
                new NameValuePair( "email[regal_image]", "" ),
                new NameValuePair( "email[reply_to]", "" ),
                new NameValuePair( "email[send_from]", template.getSendFromAddress() ),
                new NameValuePair( "email[subject]", template.getSubject() ),
                new NameValuePair( "email[template_name]", template.getTemplateName() ),
                new NameValuePair( "email[to][]", emailAddress ),
                new NameValuePair( "email[top_image][current_src]", template.getTopImageSrc() ),
                new NameValuePair( "email[top_image][original_src]", template.getTopImageSrc() ),
                new NameValuePair( "email[top_image][original_id]", template.getTopImageId() ),
                new NameValuePair( "email[top_image][image_align]", template.getTopImageAlign() ),
                new NameValuePair( "hidden_captcha", token ),
                new NameValuePair( "visible_captcha", "" ),
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "contains_pii", "0" ),
                new NameValuePair( "suppress_client_errors", "true" ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Returns a new modifiable list of common parameters to the {@code get_reservations} request.
     * 
     * @param additionalParams additional parameters to add to return list
     * @return new non-null modifiable list
     */
    private List<NameValuePair> getCommonReservationsQueryParameters( NameValuePair ... additionalParams ) {
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
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
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
                        cardType.equalsIgnoreCase( "mastercard" ) ? "master" : cardType.equalsIgnoreCase( "visa" ) ? "visa" : "" ),
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
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Process payment (AUTH and CAPTURE) onto an existing reservation.
     * 
     * @param reservationId ID of reservation (as it appears in the URL)
     * @param bookingRoomId (which "room" we're booking the payment to)
     * @param cardType one of "mastercard", "visa". Anything else will blank the field.
     * @param cardId unique id of card being processed
     * @param amount amount to record
     * @param description description
     * @return web request
     * @throws IOException on creation failure
     */
    public WebRequest createProcessPaymentRequest( String reservationId, String bookingRoomId, String cardType,
            String cardId, BigDecimal amount, String description ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/hotel/add_new_payment" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "payment_type", "exist_credit_card" ),
                new NameValuePair( "choose_card",
                        cardType.equalsIgnoreCase( "mastercard" ) ? "master" : cardType.equalsIgnoreCase( "visa" ) ? "visa" : "" ),
                new NameValuePair( "credit_card_id", cardId ),
                new NameValuePair( "assign_to", bookingRoomId ),
                new NameValuePair( "paid", CURRENCY_FORMAT.format( amount ) ),
                new NameValuePair( "payment_date", LocalDate.now().format( DD_MM_YYYY ) ),
                new NameValuePair( "cash_drawer_option", "add-to-opened-drawer" ),
                new NameValuePair( "description", description ),
                new NameValuePair( "isAllocatePayment", "false" ),
                new NameValuePair( "isRequireAllocate", "false" ),
                new NameValuePair( "process_payment", "1" ),
                new NameValuePair( "refund_payment", "0" ),
                new NameValuePair( "auth_payment", "0" ),
                new NameValuePair( "keep_credit_card_info", "0" ),
                new NameValuePair( "booking_id", reservationId ),
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Retrieves all transactions for a given reservation.
     * 
     * @param res cloudbeds reservation
     * @return web request
     * @throws IOException on creation failure
     */
    public WebRequest createGetTransactionsByReservationRequest( Reservation res ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/reports/transactions_by_reservation" );
        String roomIdentifiers = res.getBookingRooms().stream()
                .map( br -> "\"" + br.getRoomIdentifier() + "\"" )
                .collect( Collectors.joining( "," ) );
                
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "booking_id", res.getReservationId() ),
                new NameValuePair( "options", "{\"filters\":{\"from\":\"\",\"to\":\"\",\"filter\":\"\",\"res_room_identifier\":[" + roomIdentifiers + "],\"user\":\"all\",\"posted\":[\"1\"],\"description\":[]},\"group\":{\"main\":\"\",\"sub\":\"\"},\"sort\":{\"column\":\"datetime_transaction\",\"order\":\"desc\"},\"loaded_filter\":1}" ),
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Records a new note onto the existing reservation.
     * 
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
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Records a new credit card onto the existing reservation.
     * 
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
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );

        // CVV optional
        if ( StringUtils.isNotBlank( cardDetails.getCvv() ) ) {
            params.add( new NameValuePair( "cvv", cardDetails.getCvv() ) );
        }
        webRequest.setRequestParameters( params );
        return webRequest;
    }

    /**
     * Returns the source id for the given source name (used for searching).
     * 
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createReservationSourceLookupRequest() throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/associations/loader/sources" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Retrieves the email log for a reservation.
     * 
     * @param reservationId ID of reservation (as it appears in the URL)
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createGetEmailDeliveryLogRequest( String reservationId ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/connect/emails/get_email_delivery_log" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "sEcho", "1" ),
                new NameValuePair( "iColumns", "5" ),
                new NameValuePair( "sColumns", ",,,," ),
                new NameValuePair( "iDisplayStart", "0" ),
                new NameValuePair( "iDisplayLength", "100" ),
                new NameValuePair( "mDataProp_0", "event_date" ),
                new NameValuePair( "bRegex_0", "false" ),
                new NameValuePair( "bSearchable_0", "true" ),
                new NameValuePair( "bSortable_0", "true" ),
                new NameValuePair( "mDataProp_1", "email" ),
                new NameValuePair( "bRegex_1", "false" ),
                new NameValuePair( "bSearchable_1", "true" ),
                new NameValuePair( "bSortable_1", "true" ),
                new NameValuePair( "mDataProp_2", "template" ),
                new NameValuePair( "bRegex_2", "false" ),
                new NameValuePair( "bSearchable_2", "true" ),
                new NameValuePair( "bSortable_2", "true" ),
                new NameValuePair( "mDataProp_3", "subject" ),
                new NameValuePair( "bRegex_3", "false" ),
                new NameValuePair( "bSearchable_3", "true" ),
                new NameValuePair( "bSortable_3", "false" ),
                new NameValuePair( "mDataProp_4", "4" ),
                new NameValuePair( "bRegex", "false" ),
                new NameValuePair( "iSortCol_0", "0" ),
                new NameValuePair( "sSortDir_0", "desc" ),
                new NameValuePair( "iSortingCols", "1" ),
                new NameValuePair( "booking_id", reservationId ),
                new NameValuePair( "email_status", "all" ),
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    /**
     * Authorises a guest credit card.
     * 
     * @param reservationId the unique CB reservation id
     * @param cardId the card to charge
     * @param amount amount to charge
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createAuthorizeCreditCardRequest( String reservationId, String cardId, BigDecimal amount ) throws IOException {
        return createAuthorizeCreditCardRequest( "authorize", reservationId, cardId, amount );
    }

    /**
     * Captures a previous AUTHORIZE on a guest credit card.
     * 
     * @param reservationId the unique CB reservation id
     * @param cardId the card to charge
     * @param amount amount to charge
     * @return web request
     * @throws IOException on i/o error
     */
    public WebRequest createCaptureCreditCardRequest( String reservationId, String cardId, BigDecimal amount ) throws IOException {
        return createAuthorizeCreditCardRequest( "capture", reservationId, cardId, amount );
    }

    /**
     * Authorises a guest credit card.
     * 
     * @param transactionType (one of "authorize" or "capture")
     * @param reservationId the unique CB reservation id
     * @param cardId the card to charge
     * @param amount amount to charge
     * @return web request
     * @throws IOException on i/o error
     */
    private WebRequest createAuthorizeCreditCardRequest( String transactionType, String reservationId, String cardId, BigDecimal amount ) throws IOException {
        WebRequest webRequest = createBaseJsonRequest( "https://hotels.cloudbeds.com/hotel/authorize_credit_card" );
        final DecimalFormat CURRENCY_FORMAT = new DecimalFormat( "###0.00" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "authorize_amount", CURRENCY_FORMAT.format( amount ) ),
                new NameValuePair( "booking_id", reservationId ),
                new NameValuePair( "card_id", cardId ),
                new NameValuePair( "transaction_type", transactionType ),
                new NameValuePair( "property_id", getPropertyId() ),
                new NameValuePair( "group_id", getPropertyId() ),
                new NameValuePair( "version", getVersion() ) ) );
        return webRequest;
    }

    public WebRequest createVerifyCaptchaRequest(String token) throws IOException {
        WebRequest requestSettings = new WebRequest( new URL( "https://hotels.cloudbeds.com/captcha/verify" ), HttpMethod.POST );
        requestSettings.setAdditionalHeader( "accept", "*/*" );
        requestSettings.setAdditionalHeader( "content-type", "application/x-www-form-urlencoded; charset=UTF-8" );
        requestSettings.setAdditionalHeader( "referer", "https://hotels.cloudbeds.com/connect/" + getPropertyId() );
        requestSettings.setAdditionalHeader( "accept-language", "en-GB,en-US;q=0.9,en;q=0.8" );
        requestSettings.setAdditionalHeader( "accept-encoding", "gzip, deflate, br" );
        requestSettings.setAdditionalHeader( "x-requested-with", "XMLHttpRequest" );
        requestSettings.setAdditionalHeader( "authority", "hotels.cloudbeds.com" );
        requestSettings.setAdditionalHeader( "origin", "https://hotels.cloudbeds.com" );
        requestSettings.setAdditionalHeader( "user-agent", getUserAgent() );
        requestSettings.setAdditionalHeader( "cookie", getCookies() );
        requestSettings.setCharset( StandardCharsets.UTF_8 );

        requestSettings.setRequestParameters( Arrays.asList(
                new NameValuePair( "token", token ) ) );
        return requestSettings;
    }
}
