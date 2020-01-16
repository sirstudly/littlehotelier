package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.cloudbeds.responses.ActivityLogEntry;
import com.macbackpackers.beans.cloudbeds.responses.CloudbedsJsonResponse;
import com.macbackpackers.beans.cloudbeds.responses.Customer;
import com.macbackpackers.beans.cloudbeds.responses.EmailTemplateInfo;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.PaymentCardNotAcceptedException;
import com.macbackpackers.exceptions.PaymentNotAuthorizedException;
import com.macbackpackers.exceptions.RecordPaymentFailedException;
import com.macbackpackers.exceptions.UnrecoverableFault;

@Component
public class CloudbedsScraper {
    
    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );
    
    public static final String TEMPLATE_HWL_CANCELLATION_CHARGE = "Hostelworld Cancellation Charge";
    public static final String TEMPLATE_NON_REFUNDABLE_CHARGE_SUCCESSFUL = "Non-Refundable Charge Successful";
    public static final String TEMPLATE_NON_REFUNDABLE_CHARGE_DECLINED = "Non-Refundable Charge Declined";
    public static final String TEMPLATE_SAGEPAY_PAYMENT_CONFIRMATION = "Sagepay Payment Confirmation";
    public static final String TEMPLATE_DEPOSIT_CHARGE_SUCCESSFUL = "Deposit Charge Successful";
    public static final String TEMPLATE_DEPOSIT_CHARGE_DECLINED = "Deposit Charge Declined";
    public static final String TEMPLATE_RETRACT_DEPOSIT_CHARGE_SUCCESSFUL = "Retract Deposit Charge Successful";
    public static final String TEMPLATE_RETRACT_DEPOSIT_CHARGE_DECLINED = "Retract Deposit Charge Declined";

    // the last result of getPropertyContent() as it's an expensive operation
    private static String propertyContent;

    private static LoadingCache<String, EmailTemplateInfo> emailTemplateCache = CacheBuilder.newBuilder()
            .build( new CacheLoader<String, EmailTemplateInfo>() {
                @Override
                public EmailTemplateInfo load( String templateId ) {
                    throw new UnsupportedOperationException( "Not used." ); // logic defined in get(key, Callable)
                }
            } );

    @Autowired
    @Qualifier( "gsonForCloudbeds" )
    private Gson gson;

    @Autowired
    private CloudbedsJsonRequestFactory jsonRequestFactory;

    @Value( "${process.jobs.retries:3}" )
    private int MAX_RETRY;

    /**
     * Retrieves the current Cloudbeds property ID.
     * 
     * @return non-null property ID
     */
    public String getPropertyId() {
        return jsonRequestFactory.getPropertyId();
    }

    /**
     * Returns new instance of a currency format (2 decimals) cause it's not thread safe.
     * 
     * @return new currency format
     */
    public DecimalFormat getCurrencyFormat() {
        return new DecimalFormat( "###0.00" );
    }

    /**
     * Verifies that we're logged in (or fails fast if not).
     * 
     * @param webClient web client instance to use
     * @throws IOException on connection error
     */
    public synchronized void validateLoggedIn( WebClient webClient ) throws IOException {

        // simple request to see if we're logged in
        Optional<CloudbedsJsonResponse> response = doGetUserPermissionRequest( webClient );

        // if user not logged in, then response is blank
        if ( false == response.isPresent() ) {
            LOGGER.info( "Something's wrong. I don't think we're logged in. Session cookies may need to be updated." );
            throw new UnrecoverableFault( "Not logged in. Update session data." );
        }

        // if user is logged in and correct version, response is one of either {"access": true} or {"access": false}
        // if user is logged in but using an obsolete version, then response is not-blank and {success: false}
        if ( false == response.get().isSuccess() ) {

            // we may have updated our version; try to validate again
            response = doGetUserPermissionRequest( webClient );

            // if still nothing, then we fail here
            if ( false == response.isPresent() || false == response.get().isSuccess() ) {
                throw new UnrecoverableFault( "Not logged in. Update session data." );
            }
        }
    }

    /**
     * Attempts a simple request to see if current user has access to view CC details functionality.
     * Response should either be access: false, or access: true. Or if they aren't logged in, then
     * an empty response is expected.
     * 
     * @param webClient current web client
     * @return a possibly empty response
     * @throws IOException on network error
     */
    private Optional<CloudbedsJsonResponse> doGetUserPermissionRequest( WebClient webClient ) throws IOException {
        // we'll just send a sample request; we just want to make sure we get a valid response back
        WebRequest requestSettings = jsonRequestFactory.createGetUserHasViewCCPermisions();

        Page redirectPage = webClient.getPage( requestSettings );
        Optional<CloudbedsJsonResponse> response = Optional.ofNullable(
                fromJson( redirectPage.getWebResponse().getContentAsString(),
                        CloudbedsJsonResponse.class ) );

        // We get a response back and it's unsuccessful
        LOGGER.debug( "Response status {}: {}",
                redirectPage.getWebResponse().getStatusCode(),
                redirectPage.getWebResponse().getStatusMessage() );
        if ( response.isPresent() && false == response.get().isSuccess() ) {
            LOGGER.info( redirectPage.getWebResponse().getContentAsString() );

            // check if we're using an outdated version
            if ( StringUtils.isNotBlank( response.get().getVersion() ) ) {
                LOGGER.info( "Looks like we're using an outdated version. Updating our records." );
                if ( false == jsonRequestFactory.getVersion().equals( response.get().getVersion() ) ) {
                    jsonRequestFactory.setVersion( response.get().getVersion() );
                }
            }
        }
        else if ( response.isPresent() ) {
            LOGGER.info( redirectPage.getWebResponse().getContentAsString() );
        }
        return response;
    }

    /**
     * Goes to the Cloudbeds dashboard.
     * 
     * @param webClient web client instance to use
     * @return dashboard page
     * @throws IOException on navigation failure
     */
    public HtmlPage loadDashboard( WebClient webClient ) throws IOException {
        HtmlPage loadedPage = webClient.getPage( "https://hotels.cloudbeds.com/connect/" + getPropertyId() );
        if ( loadedPage.getUrl().getPath().contains( "/login" ) ) {
            LOGGER.info( "Oops, I don't think we're logged in." );
            throw new UnrecoverableFault( "Not logged in. Update session data." );
        }
        return loadedPage;
    }

    /**
     * Goes to the reservation page.
     * 
     * @param webClient web client instance to use
     * @param reservationId cloudbeds reservation id (in URL)
     * @return reservation page
     * @throws IOException on navigation failure
     */
    public HtmlPage loadReservationPage( WebClient webClient, String reservationId ) throws IOException {
        HtmlPage loadedPage = webClient.getPage( "https://hotels.cloudbeds.com/connect/" + getPropertyId() + "#/reservations/" + reservationId );
        if ( loadedPage.getUrl().getPath().contains( "/login" ) ) {
            LOGGER.info( "Oops, I don't think we're logged in." );
            throw new UnrecoverableFault( "Not logged in. Update session data." );
        }
        return loadedPage;
    }

    /**
     * Loads the given reservation by ID.
     * 
     * @param webClient web client instance to use
     * @param reservationId unique cloudbeds reservation ID
     * @return the loaded reservation (not-null)
     * @throws IOException on load failure
     * @throws MissingUserDataException if reservation not found
     */
    public Reservation getReservation( WebClient webClient, String reservationId ) throws IOException {

        WebRequest requestSettings = jsonRequestFactory.createGetReservationRequest( reservationId );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        Reservation r = fromJson( redirectPage.getWebResponse().getContentAsString(), Reservation.class );
        if ( r != null && false == r.isSuccess() && StringUtils.isNotBlank( r.getVersion() )
                && false == jsonRequestFactory.getVersion().equals( r.getVersion() ) ) {
            LOGGER.info( "Looks like we're using an outdated version. Updating our records." );
            jsonRequestFactory.setVersion( r.getVersion() );
            return getReservation( webClient, reservationId );
        }
        else if ( r == null || false == r.isSuccess() ) {
            LOGGER.error( redirectPage.getWebResponse().getContentAsString() );
            throw new MissingUserDataException( "Reservation not found." );
        }

        // need to parse the credit_cards object manually to check for presence
        JsonObject rootElem = fromJson( redirectPage.getWebResponse().getContentAsString(), JsonObject.class );
        JsonElement creditCardsElem = rootElem.get( "credit_cards" );
        if ( creditCardsElem.isJsonObject() && creditCardsElem.getAsJsonObject().entrySet().size() > 0 ) {
            String cardId = null;
            for ( Iterator<Entry<String, JsonElement>> it = creditCardsElem.getAsJsonObject().entrySet().iterator() ; it.hasNext() ; ) {
                cardId = it.next().getKey();

                // save the last one on the list (if more than one)
                JsonObject cardObj = creditCardsElem.getAsJsonObject().get( cardId ).getAsJsonObject();
                if ( false == "1".equals( cardObj.get( "is_cc_data_purged" ).getAsString() ) &&
                        "1".equals( cardObj.get( "is_active" ).getAsString() ) ) {
                    r.setCreditCardId( cardId );
                    r.setCreditCardType( cardObj.get( "card_type" ).getAsString() );
                    r.setCreditCardLast4Digits( cardObj.get( "card_number" ).getAsString() );
                }
            }
        }
        return r;
    }

    /**
     * Gets reservation but retries {@code MAX_RETRY} number of times before failing.
     * 
     * @param webClient web client instance to use
     * @param reservationId the reservation to query
     * @return non-null reservation
     */
    public Reservation getReservationRetry( WebClient webClient, String reservationId ) {
        for ( int i = 0 ; i < MAX_RETRY ; i++ ) {
            try {
                return getReservation( webClient, reservationId );
            }
            catch ( IOException ex ) {
                LOGGER.error( "Failed to retrieve reservation " + reservationId, ex );
            }
        }
        throw new UnrecoverableFault( "Max attempts made to retrieve reservation " + reservationId );
    }

    /**
     * Retrieve all customers between the given checkin dates (inclusive).
     * 
     * @param webClient web client instance to use
     * @param checkinDateStart checkin date start
     * @param checkinDateEnd checkin date end (inclusive)
     * @return non-null customer list
     * @throws IOException on page load failure
     */
    public List<Customer> getCustomers( WebClient webClient, LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        return getCustomers( webClient, jsonRequestFactory.createGetCustomersRequest(
                checkinDateStart, checkinDateEnd ) );
    }

    /**
     * Get all reservations staying between the given stay date range.
     * 
     * @param webClient web client instance to use
     * @param stayDateStart stay date (inclusive)
     * @param stayDateEnd stay date (inclusive)
     * @return non-null list of customer reservations
     * @throws IOException
     */
    public List<Customer> getReservations( WebClient webClient, LocalDate stayDateStart, LocalDate stayDateEnd ) throws IOException {
        return getCustomers( webClient, jsonRequestFactory.createGetReservationsRequestByStayDate(
                stayDateStart, stayDateEnd ) );
    }

    /**
     * Get all reservations within the given date ranges.
     * 
     * @param webClient web client instance to use
     * @param stayDateStart stay date (inclusive)
     * @param stayDateEnd stay date (inclusive)
     * @param checkinDateStart checkin date (inclusive)
     * @param checkinDateEnd checkin date (inclusive)
     * @param statuses comma-delimited list of statuses (optional)
     * @return non-null list of customer reservations
     * @throws IOException
     */
    public List<Customer> getReservations( WebClient webClient, LocalDate stayDateStart, LocalDate stayDateEnd,
            LocalDate checkinDateStart, LocalDate checkinDateEnd, String statuses ) throws IOException {
        return getCustomers( webClient, jsonRequestFactory.createGetReservationsRequest(
                stayDateStart, stayDateEnd, checkinDateStart, checkinDateEnd, statuses ) );
    }

    /**
     * Get all reservations staying between the given checkin date range.
     * 
     * @param webClient web client instance to use
     * @param checkinDateStart checkin date (inclusive)
     * @param checkinDateEnd checkin date (inclusive)
     * @return non-null list of customer reservations
     * @throws IOException
     */
    public List<Customer> getReservationsByCheckinDate( WebClient webClient, LocalDate checkinDateStart, LocalDate checkinDateEnd ) throws IOException {
        return getCustomers( webClient, jsonRequestFactory.createGetReservationsRequestByCheckinDate(
                checkinDateStart, checkinDateEnd ) );
    }

    /**
     * Get all reservations matching the given query.
     * 
     * @param webClient web client instance to use
     * @param query the query string
     * @return non-null list of customer reservations
     * @throws IOException
     */
    public List<Customer> getReservations( WebClient webClient, String query ) throws IOException {
        return getCustomers( webClient, jsonRequestFactory.createGetReservationsRequest( query ) );
    }

    /**
     * Get all customers/reservations using the given request.
     * 
     * @param webClient web client instance to use
     * @param requestSettings request details
     * @return non-null list of customer reservations
     * @throws IOException
     */
    private List<Customer> getCustomers( WebClient webClient, WebRequest requestSettings ) throws IOException {
        
        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );
        
        Optional<JsonElement> jelement = Optional.ofNullable( fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class ) );
        if( false == jelement.isPresent() ) {
            throw new MissingUserDataException( "Failed to retrieve reservations." );
        }

        Optional<JsonObject> jobject = Optional.ofNullable( jelement.get().getAsJsonObject() );
        if( false == jobject.isPresent() ) {
            throw new MissingUserDataException( "Failed to retrieve reservations." );
        }

        // check if we're using an outdated version
        if ( false == jobject.get().get( "success" ).getAsBoolean() ) {
            String version = jobject.get().get("version").getAsString();
            if ( StringUtils.isNotBlank( version ) && false == jsonRequestFactory.getVersion().equals( version ) ) {
                LOGGER.info( "Looks like we're using an outdated version. Updating our records." );
                jsonRequestFactory.setVersion( version );
                requestSettings.getRequestParameters().add( new NameValuePair( "version", version ) );
                return getCustomers( webClient, requestSettings );
            }
        }

        Optional<JsonArray> jarray = Optional.ofNullable( jobject.get().getAsJsonArray( "aaData" ) );
        if( false == jarray.isPresent() ) {
            throw new MissingUserDataException( "Failed to retrieve reservations." );
        }
        return Arrays.asList( gson.fromJson( jarray.get(), Customer[].class ) );
    }

    /**
     * Get all transactions by reservation.
     * 
     * @param webClient web client instance to use
     * @param reservation cloudbeds reservation
     * @return non-null list of transactions
     * @throws IOException
     */
    public boolean isExistsSagepayPaymentWithVendorTxCode( WebClient webClient, Reservation res, String vendorTxCode ) throws IOException {
        
        WebRequest requestSettings = jsonRequestFactory.createGetTransactionsByReservationRequest( res );
        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        Optional<JsonObject> rpt = Optional.ofNullable( fromJson( redirectPage.getWebResponse().getContentAsString(), JsonObject.class ) );
        if ( false == rpt.isPresent() || false == rpt.get().get( "success" ).getAsBoolean() ) {
            if ( rpt.isPresent() ) {
                LOGGER.info( redirectPage.getWebResponse().getContentAsString() );
            }
            throw new MissingUserDataException( "Failed response." );
        }

        // look for the existence of a payment record with matching vendor tx code
        Pattern p = Pattern.compile( "VendorTxCode: (.*?)," );
        Optional<String> txnNote = StreamSupport.stream(
                rpt.get().get( "records" ).getAsJsonArray().spliterator(), false )
                .filter( r -> r.getAsJsonObject().has( "notes" ) )
                .map( r -> r.getAsJsonObject().get( "notes" ).getAsString() )
                .filter( n -> {
                    Matcher m = p.matcher( n );
                    return m.find() && vendorTxCode.equals( m.group( 1 ) );
                } )
                .findFirst();
        return txnNote.isPresent();
    }

    /**
     * Finds all staff allocations for the given date (and the day after):
     * <ul>
     * <li>If stayDate is staff bed, stayDate + 1 is staff bed -&gt; allocation for 2 days
     * <li>If stayDate is staff bed, stayDate + 1 is not staff bed -&gt; allocation for 1st day
     * <li>If stayDate is not staff bed, stayDate +1 is staff bed -&gt; allocation for 2nd day
     * </ul>
     * 
     * @param webClient web client instance to use
     * @param stayDate the date we're searching on
     * @return non-null raw JSON object holding all bed assignments
     * @throws IOException on failure
     */
    public JsonObject getAllStaffAllocations( WebClient webClient, LocalDate stayDate ) throws IOException {

        WebRequest requestSettings = jsonRequestFactory.createGetRoomAssignmentsReport( stayDate, stayDate.plusDays( 1 ) );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.info( "Fetching staff allocations for " + stayDate.format( DateTimeFormatter.ISO_LOCAL_DATE ) );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        Optional<JsonObject> rpt = Optional.ofNullable( fromJson( redirectPage.getWebResponse().getContentAsString(), JsonObject.class ) );
        if ( false == rpt.isPresent() || false == rpt.get().get( "success" ).getAsBoolean() ) {
            if ( rpt.isPresent() ) {
                LOGGER.info( redirectPage.getWebResponse().getContentAsString() );
            }
            throw new MissingUserDataException( "Failed response." );
        }

        return rpt.get();
    }

    /**
     * Add a payment record to the given reservation.
     * 
     * @param webClient web client instance to use
     * @param res cloudbeds reservation
     * @param cardType one of "mastercard", "visa". Anything else will blank the field.
     * @param amount amount to add
     * @param description description of payment
     * @throws IOException on page load failure
     * @throws RecordPaymentFailedException payment record failure
     */
    public void addPayment( WebClient webClient, Reservation res, String cardType, BigDecimal amount, String description ) throws IOException, RecordPaymentFailedException {

        // first we need to find a "room" we're booking to
        // it doesn't actually map to a room, just an assigned guest
        // it doesn't even have to be an allocated room
        if ( res.getBookingRooms() == null || res.getBookingRooms().isEmpty() ) {
            throw new MissingUserDataException( "Unable to find allocation to assign payment to!" );
        }

        // just take the first one
        String bookingRoomId = res.getBookingRooms().get( 0 ).getId();

        WebRequest requestSettings = jsonRequestFactory.createAddNewPaymentRequest(
                res.getReservationId(), bookingRoomId, cardType, amount, description );

        Page redirectPage = webClient.getPage( requestSettings );
        String jsonResponse = redirectPage.getWebResponse().getContentAsString();
        LOGGER.debug( jsonResponse );
        
        if( StringUtils.isBlank( jsonResponse ) ) {
            throw new MissingUserDataException( "Missing response from add payment request?" );
        }
        CloudbedsJsonResponse response = fromJson( jsonResponse, CloudbedsJsonResponse.class );

        // throw a wobbly if response not successful
        if ( response.isFailure() ) {
            throw new RecordPaymentFailedException( response.getMessage() );
        }
    }

    /**
     * Adds a note to an existing reservation.
     * 
     * @param webClient web client instance to use
     * @param reservationId unique reservation ID
     * @param note note description to add
     * @throws IOException on page load failure
     */
    public void addNote( WebClient webClient, String reservationId, String note ) throws IOException {

        WebRequest requestSettings = jsonRequestFactory.createAddNoteRequest( reservationId, note );
        LOGGER.info( "Adding note: " + note + " to reservation " + reservationId );

        Page redirectPage = webClient.getPage( requestSettings );
        String jsonResponse = redirectPage.getWebResponse().getContentAsString();
        LOGGER.debug( jsonResponse );

        if ( StringUtils.isBlank( jsonResponse ) ) {
            throw new MissingUserDataException( "Missing response from add note request?" );
        }
        CloudbedsJsonResponse response = fromJson( jsonResponse, CloudbedsJsonResponse.class );

        // throw a wobbly if response not successful
        if ( response.isFailure() ) {
            throw new IOException( response.getMessage() );
        }
    }

    /**
     * Adds a payment card to an existing reservation.
     * 
     * @param webClient web client instance to use
     * @param reservationId unique reservation ID
     * @param cardDetails card to add
     * @throws IOException on page load failure
     */
    public void addCardDetails( WebClient webClient, String reservationId, CardDetails cardDetails ) throws IOException {

        WebRequest requestSettings = jsonRequestFactory.createAddCreditCardRequest( reservationId, cardDetails );

        Page redirectPage = webClient.getPage( requestSettings );
        String jsonResponse = redirectPage.getWebResponse().getContentAsString();
        LOGGER.debug( jsonResponse );

        if ( StringUtils.isBlank( jsonResponse ) ) {
            throw new MissingUserDataException( "Missing response from add card details request?" );
        }
        CloudbedsJsonResponse response = fromJson( jsonResponse, CloudbedsJsonResponse.class );

        // throw a wobbly if response not successful
        if ( response.isFailure() ) {
            throw new PaymentCardNotAcceptedException( response.getMessage() );
        }
    }

    /**
     * Sends a source lookup request to the server.
     * 
     * @param webClient web client instance to use
     * @param sourceName name of booking source to search for
     * @return comma-delimited list of matching source ids
     * @throws IOException
     */
    public String lookupBookingSourceIds( WebClient webClient, String... sourceNames ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createReservationSourceLookupRequest();

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        JsonElement jelement = fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        if( jelement == null || false == jelement.getAsJsonObject().get( "success" ).getAsBoolean() ) {
            throw new MissingUserDataException( "Failed to retrieve source lookup." );
        }
        
        // drill down to each of the 3rd party OTAs matching the given source(s)
        List<String> sourceIds = StreamSupport.stream( jelement.getAsJsonObject()
                .get( "result" ).getAsJsonArray().spliterator(), false )
                .filter( o -> "OTA".equals( o.getAsJsonObject().get( "text" ).getAsString() ) )
                .flatMap( o -> StreamSupport.stream(
                        o.getAsJsonObject().get( "children" ).getAsJsonArray().spliterator(), false ) )
                .filter( p -> Arrays.asList( sourceNames ).contains( p.getAsJsonObject().get( "text" ).getAsString() ) )
                .map( o -> o.getAsJsonObject().get( "li_attr" ).getAsJsonObject().get( "data-source-id" ).getAsString() )
                .collect( Collectors.toList() );
        
        if ( sourceIds.size() != sourceNames.length ) {
            LOGGER.warn( "Mismatch on booking source lookup: found source ids {} for sources {}", sourceIds, sourceNames );
        }
        if ( sourceIds.isEmpty() ) {
            throw new IOException( "Unable to find sources for " + sourceNames );
        }
        return sourceIds.stream().collect( Collectors.joining( "," ) );
    }
    
    /**
     * Get all reservations with the given booking sources.
     * 
     * @param webClient web client instance to use
     * @param checkinDateStart checkin date (inclusive)
     * @param checkinDateEnd checkin date (inclusive)
     * @param bookedDateStart booked date (inclusive)
     * @param bookedDateEnd booked date (inclusive)
     * @param bookingSources comma-delimited list of booking source(s)
     * @return non-null list of reservations
     * @throws IOException
     */
    public List<Reservation> getReservationsForBookingSources( WebClient webClient,
            LocalDate checkinDateStart, LocalDate checkinDateEnd, 
            LocalDate bookedDateStart, LocalDate bookedDateEnd, String ... sourceNames ) throws IOException {
        return getCustomers( webClient, jsonRequestFactory.createGetReservationsRequestByBookingSource(
                checkinDateStart, checkinDateEnd, bookedDateStart, bookedDateEnd,
                lookupBookingSourceIds( webClient, sourceNames ) ) )
                        .stream()
                        .map( c -> getReservationRetry( webClient, c.getId() ) )
                        .collect( Collectors.toList() );
    }

    /**
     * Get all cancelled reservations with the given booking sources.
     * 
     * @param webClient web client instance to use
     * @param checkinDateStart checkin date (inclusive)
     * @param checkinDateEnd checkin date (inclusive)
     * @param cancelDateStart cancellation date (inclusive)
     * @param cancelDateEnd cancellation date (inclusive)
     * @param bookingSources comma-delimited list of booking source(s)
     * @return non-null list of reservations
     * @throws IOException
     */
    public List<Reservation> getCancelledReservationsForBookingSources( WebClient webClient,
            LocalDate checkinDateStart, LocalDate checkinDateEnd, LocalDate cancelDateStart, 
            LocalDate cancelDateEnd, String ... sourceNames ) throws IOException {
        return getCustomers( webClient, jsonRequestFactory.createGetCancelledReservationsRequestByBookingSource(
                checkinDateStart, checkinDateEnd, cancelDateStart, cancelDateEnd,
                lookupBookingSourceIds( webClient, sourceNames ) ) )
                        .stream()
                        .map( c -> getReservationRetry( webClient, c.getId() ) )
                        .collect( Collectors.toList() );
    }

    /**
     * Does an AUTHORIZE/CAPTURE for the given booking for the amount given.
     * 
     * @param webClient web client instance to use
     * @param reservationId unique CB reservation
     * @param cardId the card id to charge against
     * @param amount how much
     * @throws IOException
     */
    public void chargeCardForBooking( WebClient webClient, String reservationId, String cardId, BigDecimal amount ) throws IOException {

        // AUTHORIZE
        LOGGER.info( "Begin AUTHORIZE for reservation " + reservationId + " for " + getCurrencyFormat().format( amount ) );
        WebRequest requestSettings = jsonRequestFactory.createAuthorizeCreditCardRequest(
                reservationId, cardId, amount );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        JsonElement jelement = fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        if ( jelement == null || false == jelement.getAsJsonObject().get( "success" ).getAsBoolean() ) {
            LOGGER.error( redirectPage.getWebResponse().getContentAsString() );
            addNote( webClient, reservationId, "Failed to AUTHORIZE booking: " +
                    jelement.getAsJsonObject().get( "message" ).getAsString() );
            throw new PaymentNotAuthorizedException( "Failed to AUTHORIZE booking.", redirectPage.getWebResponse() );
        }

        // CAPTURE
        LOGGER.info( "Begin CAPTURE for reservation " + reservationId  + " for " + getCurrencyFormat().format( amount ) );
        requestSettings = jsonRequestFactory.createCaptureCreditCardRequest(
                reservationId, cardId, amount );

        redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        jelement = fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        if ( jelement == null || false == jelement.getAsJsonObject().get( "success" ).getAsBoolean() ) {
            LOGGER.error( redirectPage.getWebResponse().getContentAsString() );
            addNote( webClient, reservationId, "Failed to CAPTURE booking: " +
                    jelement.getAsJsonObject().get( "message" ).getAsString() );
            throw new PaymentNotAuthorizedException( "Failed to CAPTURE booking.", redirectPage.getWebResponse() );
        }
    }

    /**
     * Sends a ping request to the server.
     * 
     * @param webClient web client instance to use
     * @throws IOException
     */
    public void ping( WebClient webClient ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createPingRequest();

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );
    }

    /**
     * Goes to the given page.
     * 
     * @param webClient web client instance to use
     * @param pageURL URL
     * @return non-null page
     * @throws IOException on i/o error
     */
    public HtmlPage navigateToPage( WebClient webClient, String pageURL ) throws IOException {
        // attempt to go the page directly using the current credentials
        HtmlPage nextPage = webClient.getPage( pageURL );
        if ( nextPage.getUrl().getPath().endsWith( "/login" ) ) {
            throw new UnrecoverableFault( "Login to Cloudbeds failed. Has the password changed?" );
        }
        return nextPage;
    }

    /**
     * Attempts to find the CB booking based on either the bookingRef or guest name. Throws
     * UnrecoverableFault if booking could not be resolved.
     * 
     * @param webClient web client instance to use
     * @param bookingRef LH booking reference
     * @param guestName name of guest
     * @return non-null reservation
     * @throws IOException on i/o error
     */
    public Reservation findBookingByLHBookingRef( WebClient webClient, String bookingRef, Date checkinDate, String guestName ) throws IOException {

        // first try to search by booking ref
        Pattern p = Pattern.compile( "\\D*(\\d+)$" );
        Matcher m = p.matcher( bookingRef );
        if ( m.find() ) {
            List<Customer> reservations = getReservations( webClient, m.group( 1 ) );
            if ( reservations.size() > 1 ) {
                throw new UnrecoverableFault( "More than one CB booking found for " + bookingRef );
            }
            else if ( reservations.size() == 1 ) {
                return getReservationRetry( webClient, reservations.get( 0 ).getId() );
            }
        }

        // could not find booking by booking ref; try with guest name
        List<Customer> reservations = getReservations( webClient, guestName ).stream()
                .peek( c -> LOGGER.info( "Matched reservation " + c.getId() + " for "
                        + c.getFirstName() + " " + c.getLastName() + " with checkin date " + c.getCheckinDate() ) )
                .filter( c -> AllocationsPageScraper.DATE_FORMAT_YYYY_MM_DD.format( checkinDate ).equals( c.getCheckinDate() ) )
                .collect( Collectors.toList() );
        if ( reservations.size() > 1 ) {
            throw new UnrecoverableFault( "More than one CB booking found for " + bookingRef + ": " + guestName );
        }
        else if ( reservations.size() == 1 ) {
            return getReservationRetry( webClient, reservations.get( 0 ).getId() );
        }
        throw new UnrecoverableFault( "No CB bookings found for " + bookingRef + ": " + guestName );
    }

    /**
     * Returns the activity log for the given reservation.
     * 
     * @param webClient web client instance to use
     * @param identifier the cloudbeds unique id (under the reservation name)
     * @return list of activity log entries
     * @throws IOException
     */
    public List<ActivityLogEntry> getActivityLog( WebClient webClient, String identifier ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createGetActivityLog( identifier );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        JsonElement jelement = fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        if( jelement == null || false == jelement.getAsJsonObject().get( "success" ).getAsBoolean() ) {
            throw new MissingUserDataException( "Failed to retrieve activity log for identifier " + identifier );
        }

        final DateTimeFormatter DD_MM_YYYY_HH_MM = DateTimeFormatter.ofPattern( "dd/MM/yyyy hh:mm a" );
        List<ActivityLogEntry> logEntries = new ArrayList<ActivityLogEntry>();
        jelement.getAsJsonObject().get( "aaData" ).getAsJsonArray().forEach( e -> {
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
        } );
        return logEntries;
    }
    
    /**
     * Retrieves the given email template.
     * @param webClient web client instance to use
     * @param templateId email template id
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo getEmailTemplate( WebClient webClient, String templateId ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createGetEmailTemplate( templateId );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        JsonElement jelement = fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        if( jelement == null || false == jelement.getAsJsonObject().get( "success" ).getAsBoolean() ) {
            throw new MissingUserDataException( "Failed to retrieve email template " + templateId );
        }

        EmailTemplateInfo template = new EmailTemplateInfo();
        JsonObject elem = jelement.getAsJsonObject().get( "email_template" ).getAsJsonObject();
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
        return template;
    }

    /**
     * Retrieves the Hostelworld late cancellation email template.
     * 
     * @param webClient web client instance to use
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo getHostelworldLateCancellationEmailTemplate( WebClient webClient ) throws IOException {
        return fetchEmailTemplate( webClient, TEMPLATE_HWL_CANCELLATION_CHARGE );
    }

    /**
     * Retrieves the non-refundable charged successful email template.
     * 
     * @param webClient web client instance to use
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo getNonRefundableSuccessfulEmailTemplate( WebClient webClient ) throws IOException {
        return fetchEmailTemplate( webClient, TEMPLATE_NON_REFUNDABLE_CHARGE_SUCCESSFUL );
    }

    /**
     * Retrieves the non-refundable charged declined email template.
     * 
     * @param webClient web client instance to use
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo getNonRefundableDeclinedEmailTemplate( WebClient webClient ) throws IOException {
        return fetchEmailTemplate( webClient, TEMPLATE_NON_REFUNDABLE_CHARGE_DECLINED );
    }

    /**
     * Retrieves the Sagepay confirmation email template.
     * 
     * @param webClient web client instance to use
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo getSagepayPaymentConfirmationEmailTemplate( WebClient webClient ) throws IOException {
        return fetchEmailTemplate( webClient, TEMPLATE_SAGEPAY_PAYMENT_CONFIRMATION );
    }

    /**
     * Retrieves the deposit successfully charged email template.
     * 
     * @param webClient web client instance to use
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo getDepositChargeSuccessfulEmailTemplate( WebClient webClient ) throws IOException {
        return fetchEmailTemplate( webClient, TEMPLATE_DEPOSIT_CHARGE_SUCCESSFUL );
    }

    /**
     * Retrieves the deposit charge declined email template.
     * 
     * @param webClient web client instance to use
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo getDepositChargeDeclinedEmailTemplate( WebClient webClient ) throws IOException {
        return fetchEmailTemplate( webClient, TEMPLATE_DEPOSIT_CHARGE_DECLINED );
    }

    /**
     * Retrieves an email template.
     * 
     * @param webClient web client instance to use
     * @param templateName name of template
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo fetchEmailTemplate( WebClient webClient, String templateName ) throws IOException {

        try {
            // retrieve email template only if this is the first time we've done it
            return emailTemplateCache.get( templateName, new Callable<EmailTemplateInfo>() {
                @Override
                public EmailTemplateInfo call() throws Exception {
                    Optional<JsonElement> emailTemplate = StreamSupport.stream(
                            fromJson( getPropertyContent( webClient ), JsonElement.class ).getAsJsonObject()
                                    .get( "email_templates" ).getAsJsonArray().spliterator(),
                            false )
                            .filter( t -> templateName.equals( t.getAsJsonObject().get( "template_name" ).getAsString() ) )
                            .findFirst();

                    if ( false == emailTemplate.isPresent() ) {
                        throw new MissingUserDataException( "Failed to retrieve email template" );
                    }

                    String emailTemplateId = emailTemplate.get().getAsJsonObject().get( "email_template_id" ).getAsString();
                    LOGGER.info( "Found " + templateName + " email template id: " + emailTemplateId );

                    return getEmailTemplate( webClient, emailTemplateId );
                }
            } );
        }
        catch ( ExecutionException ex ) {
            throw new IOException( ex );
        }
    }

    /**
     * Retrieves the most recent time the email template was used to send an email to a guest for
     * the given booking.
     * 
     * @param webClient web client instance to use
     * @param reservationId ID of reservation
     * @param templateName name of template
     * @return non-null email template
     * @throws IOException
     */
    public Optional<LocalDateTime> getEmailLastSentDate( WebClient webClient, String reservationId, String templateName ) throws IOException {

        Page redirectPage = webClient.getPage( jsonRequestFactory.createGetEmailDeliveryLogRequest( reservationId ) );

        JsonElement jelement = fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        if ( jelement == null || false == jelement.getAsJsonObject().get( "success" ).getAsBoolean() ) {
            throw new MissingUserDataException( "Failed to retrieve email log for reservation " + reservationId );
        }

        final DateTimeFormatter DD_MM_YYYY_HH_MM = DateTimeFormatter.ofPattern( "dd/MM/yyyy hh:mm a" );
        return StreamSupport.stream( jelement.getAsJsonObject().get( "aaData" )
                .getAsJsonArray().spliterator(), false )
                .filter( e -> false == e.getAsJsonObject().get( "template" ).isJsonNull() )
                .filter( e -> templateName.equals( e.getAsJsonObject().get( "template" ).getAsString() ) )
                .map( e -> LocalDateTime.parse( e.getAsJsonObject().get( "event_date" ).getAsString(), DD_MM_YYYY_HH_MM ) )
                .findFirst();
    }

    /**
     * Sends an email from a booking from an email template.
     * 
     * @param webClient
     * @param template email template
     * @param res reservation to send from
     * @param emailTo email recipient (overrides reservation email)
     * @param transformBodyFn and transformation on the email body
     * @param token the solved recaptcha token
     * @throws IOException
     */
    public void sendEmailFromTemplate( WebClient webClient, EmailTemplateInfo template, Reservation res,
            String emailTo, Function<String, String> transformBodyFn, String token ) throws IOException {

        WebRequest requestSettings = jsonRequestFactory.createSendCustomEmail(
                template, res.getIdentifier(), res.getCustomerId(), res.getReservationId(),
                emailTo, transformBodyFn, token );

        Page redirectPage = webClient.getPage( requestSettings );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        JsonElement jelement = fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        if ( jelement == null || false == jelement.getAsJsonObject().get( "success" ).getAsBoolean() ) {
            LOGGER.error( redirectPage.getWebResponse().getContentAsString() );
            throw new UnrecoverableFault( "Failed to send " + template.getTemplateName() + " email for reservation " + res.getReservationId() );
        }
    }

    /**
     * Retrieves the property info.
     * 
     * @param webClient web client instance to use
     * @throws non-null email template
     * @throws IOException
     */
    public String getPropertyContent( WebClient webClient ) throws IOException {
        if ( propertyContent == null ) {
            WebRequest requestSettings = jsonRequestFactory.createGetPropertyContent();

            Page redirectPage = webClient.getPage( requestSettings );
            LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );
            propertyContent = redirectPage.getWebResponse().getContentAsString();
        }
        return propertyContent;
    }

    /**
     * Deserialize json into object. Logs if RuntimeException is thrown.
     * 
     * @param json JSON string
     * @param clazz
     * @return deserialized object (nullable)
     */
    private <T> T fromJson( String json, Class<T> clazz ) {
        try {
            return gson.fromJson( json, clazz );
        }
        catch ( RuntimeException ex ) {
            LOGGER.error( json );
            LOGGER.error( "Error attempting to parse JSON", ex );
            throw ex;
        }
    }
}