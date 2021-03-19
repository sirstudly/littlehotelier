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
import java.util.function.BiConsumer;
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
import com.macbackpackers.beans.cloudbeds.responses.Guest;
import com.macbackpackers.beans.cloudbeds.responses.Reservation;
import com.macbackpackers.beans.cloudbeds.responses.TransactionRecord;
import com.macbackpackers.exceptions.MissingUserDataException;
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
    public static final String TEMPLATE_STRIPE_PAYMENT_CONFIRMATION = "Stripe Payment Confirmation";
    public static final String TEMPLATE_DEPOSIT_CHARGE_SUCCESSFUL = "Deposit Charge Successful";
    public static final String TEMPLATE_DEPOSIT_CHARGE_DECLINED = "Deposit Charge Declined";
    public static final String TEMPLATE_RETRACT_DEPOSIT_CHARGE_SUCCESSFUL = "Retract Deposit Charge Successful";
    public static final String TEMPLATE_RETRACT_DEPOSIT_CHARGE_DECLINED = "Retract Deposit Charge Declined";
    public static final String TEMPLATE_PAYMENT_LINK = "Payment Link";
    public static final String TEMPLATE_COVID19_CLOSING = "Coronavirus- Doors Closing";
    public static final String TEMPLATE_REFUND_PROCESSED = "Refund Processed";
    public static final String TEMPLATE_COVID_PRESTAY = "COVID Pre-Stay Email";

    // the last result of getPropertyContent() as it's an expensive operation
    private static JsonObject propertyContent;

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
    @Qualifier( "gsonForCloudbedsIdentity" )
    private Gson gsonIdentity;

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
        // if user not logged in, then response is blank and we throw an exception
        doGetUserPermissionRequest( webClient );
    }

    /**
     * Attempts a simple request to see if current user has access to view CC details functionality.
     * Response should either be access: false, or access: true. Or if they aren't logged in, then
     * an empty response is expected.
     * 
     * @param webClient current web client
     * @return response
     * @throws IOException on network error
     */
    private CloudbedsJsonResponse doGetUserPermissionRequest( WebClient webClient ) throws IOException {
        // we'll just send a sample request; we just want to make sure we get a valid response back
        return doRequestErrorOnFailure( webClient, jsonRequestFactory.createGetUserHasViewCCPermisions(),
                CloudbedsJsonResponse.class, ( resp, json ) -> LOGGER.info( json ) );
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

        Reservation r = doRequestErrorOnFailure( webClient, jsonRequestFactory.createGetReservationRequest( reservationId ), Reservation.class,
                ( resv, jsonResponse ) -> {
                    // need to parse the credit_cards object manually to check for presence
                    JsonObject rootElem = fromJson( jsonResponse, JsonObject.class );
                    JsonElement creditCardsElem = rootElem.get( "credit_cards" );
                    if ( creditCardsElem.isJsonObject() && creditCardsElem.getAsJsonObject().entrySet().size() > 0 ) {
                        String cardId = null;
                        for ( Iterator<Entry<String, JsonElement>> it = creditCardsElem.getAsJsonObject().entrySet().iterator() ; it.hasNext() ; ) {
                            cardId = it.next().getKey();

                            // save the last one on the list (if more than one)
                            JsonObject cardObj = creditCardsElem.getAsJsonObject().get( cardId ).getAsJsonObject();
                            if ( false == cardObj.get( "is_cc_data_purged" ).getAsBoolean() &&
                                    cardObj.get( "is_active" ).getAsBoolean() ) {
                                resv.setCreditCardId( cardId );
                                resv.setCreditCardType( cardObj.get( "card_type" ).getAsString() );
                                resv.setCreditCardLast4Digits( cardObj.get( "card_number" ).getAsString() );
                            }
                        }
                    }
                } );
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
        JsonObject jobject = doRequest( webClient, requestSettings );
        JsonArray jarray = jobject.getAsJsonArray( "aaData" );
        if ( null == jarray ) {
            throw new MissingUserDataException( "Failed to retrieve reservations." );
        }
        return Arrays.asList( gson.fromJson( jarray, Customer[].class ) );
    }

    /**
     * Checks if there are any transactions with the given vendorTxCode for the given reservation.
     * 
     * @param webClient web client instance to use
     * @param res cloudbeds reservation
     * @param vendorTxCode unique merchant identifier
     * @return non-null list of transactions
     * @throws IOException
     */
    public boolean isExistsPaymentWithVendorTxCode( WebClient webClient, Reservation res, String vendorTxCode ) throws IOException {
        
        WebRequest requestSettings = jsonRequestFactory.createGetTransactionsByReservationRequest( res );
        JsonObject rpt = doRequest( webClient, requestSettings );

        // look for the existence of a payment record with matching vendor tx code
        Pattern p = Pattern.compile( "VendorTxCode: (.*?)," );
        Optional<String> txnNote = StreamSupport.stream(
                rpt.get( "records" ).getAsJsonArray().spliterator(), false )
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
     * Returns the (Stripe) transaction charge identifier for the given reservation.
     * 
     * @param webClient
     * @param res cloudbeds reservation
     * @param id unique transaction id
     * @return (Stripe) charge identifier
     * @throws IOException
     */
    public TransactionRecord getStripeTransaction( WebClient webClient, Reservation res, String id ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createGetTransactionsByReservationRequest( res );
        JsonObject rpt = doRequest( webClient, requestSettings );

        // look for a charge id for the matching transaction
        Optional<TransactionRecord> txnRecord = StreamSupport.stream(
                rpt.get( "records" ).getAsJsonArray().spliterator(), false )
                .filter( r -> id.equals( r.getAsJsonObject().get( "id" ).getAsString() ) )
                .map( r -> gson.fromJson( r, TransactionRecord.class ) )
                .findFirst();

        if ( false == txnRecord.isPresent() ) {
            throw new MissingUserDataException( "Unable to find transaction " + id );
        }
        return txnRecord.get();
    }

    /**
     * Checks if there are any refund transactions for the given reservation.
     * 
     * @param webClient web client instance to use
     * @param res cloudbeds reservation
     * @return refunds found
     * @throws IOException
     */
    public boolean isExistsRefund( WebClient webClient, Reservation res ) throws IOException {
        
        WebRequest requestSettings = jsonRequestFactory.createGetTransactionsByReservationRequest( res );
        JsonObject rpt = doRequest( webClient, requestSettings );

        // look for the existence of a refund payment record
        Optional<JsonElement> refundTxn = StreamSupport.stream(
                rpt.get( "records" ).getAsJsonArray().spliterator(), false )
                .filter( r -> "1".equals( r.getAsJsonObject().get( "is_refund" ).getAsString() ) )
                .findFirst();
        return refundTxn.isPresent();
    }

    /**
     * Runs Room Assignments report and returns the raw response.
     * 
     * @param webClient web client instance to use
     * @param stayDate the date we're searching on
     * @return non-null raw JSON object holding all bed assignments
     * @throws IOException on failure
     */
    public JsonObject getRoomAssignmentsReport( WebClient webClient, LocalDate stayDate ) throws IOException {
        return getRoomAssignmentsReport( webClient, stayDate, stayDate.plusDays( 1 ) );
    }

    /**
     * Runs Room Assignments report and returns the raw response.
     * 
     * @param webClient
     * @param stayDateFrom
     * @param stayDateTo
     * @return non-null JSON response
     * @throws IOException
     */
    public JsonObject getRoomAssignmentsReport( WebClient webClient, LocalDate stayDateFrom, LocalDate stayDateTo ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createGetRoomAssignmentsReport( stayDateFrom, stayDateTo );
        LOGGER.info( "Fetching staff allocations for " + stayDateFrom.format( DateTimeFormatter.ISO_LOCAL_DATE )
                + " to " + stayDateTo.format( DateTimeFormatter.ISO_LOCAL_DATE ) );
        return doRequest( webClient, requestSettings );
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
     */
    public void addPayment( WebClient webClient, Reservation res, String cardType, BigDecimal amount, String description ) throws IOException {

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
        doRequestErrorOnFailure( webClient, requestSettings, CloudbedsJsonResponse.class, null );
    }

    /**
     * Registers an existing refund to the given reservation.
     * 
     * @param webClient web client instance to use
     * @param res cloudbeds reservation
     * @param amount amount to add
     * @param description description of payment
     * @throws IOException on page load failure
     */
    public void addRefund( WebClient webClient, Reservation res, BigDecimal amount, String description ) throws IOException {

        // first we need to find a "room" we're booking to
        // it doesn't actually map to a room, just an assigned guest
        // it doesn't even have to be an allocated room
        if ( res.getBookingRooms() == null || res.getBookingRooms().isEmpty() ) {
            throw new MissingUserDataException( "Unable to find allocation to assign payment to!" );
        }

        // just take the first one
        String bookingRoomId = res.getBookingRooms().get( 0 ).getId();
        WebRequest requestSettings = jsonRequestFactory.createAddRefundRequest(
                res.getReservationId(), bookingRoomId, amount, description );
        doRequestErrorOnFailure( webClient, requestSettings, CloudbedsJsonResponse.class, null );
    }

    /**
     * Processes (charges) a refund for the given reservation.
     * 
     * @param webClient web client instance to use
     * @param res cloudbeds reservation
     * @param authTxn original transaction
     * @param amount amount to add
     * @param description description of payment
     * @return response from Cloudbeds
     * @throws IOException on page load failure
     */
    public CloudbedsJsonResponse processRefund( WebClient webClient, Reservation res, TransactionRecord authTxn, BigDecimal amount, String description ) throws IOException {

        // first we need to find a "room" we're booking to
        // it doesn't actually map to a room, just an assigned guest
        // it doesn't even have to be an allocated room
        if ( res.getBookingRooms() == null || res.getBookingRooms().isEmpty() ) {
            throw new MissingUserDataException( "Unable to find allocation to assign payment to!" );
        }

        // just take the first one
        String bookingRoomId = res.getBookingRooms().get( 0 ).getId();
        WebRequest requestSettings = jsonRequestFactory.createAddNewProcessRefundRequest(
                res.getReservationId(), authTxn.getPaymentId(), bookingRoomId, authTxn.getCreditCardId(),
                authTxn.getCreditCardType(), amount, description );
        return doRequestErrorOnFailure( webClient, requestSettings, CloudbedsJsonResponse.class, null );
    }

    /**
     * Performs a refund (against the gateway) for the given payment.
     * 
     * @param webClient web client instance to use
     * @param payment initial charge we're refunding against
     * @param amount amount to add
     * @return JSON response
     * @throws IOException on page load failure
     */
    public CloudbedsJsonResponse performRefund( WebClient webClient, TransactionRecord payment, BigDecimal amount ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createRefundPaymentRequest( 
                payment.getPaymentId(), payment.getCreditCardId(), amount );
        return doRequestErrorOnFailure( webClient, requestSettings, CloudbedsJsonResponse.class, null );
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
        doRequestErrorOnFailure( webClient, requestSettings, CloudbedsJsonResponse.class, null );
    }

    /**
     * Sets the status of the reservation to "canceled".
     * 
     * @param webClient web client instance to use
     * @param reservationId unique reservation ID
     * @throws IOException on page load failure
     */
    public void cancelBooking( WebClient webClient, String reservationId ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createCancelReservationRequest( reservationId );
        LOGGER.info( "Canceling reservation " + reservationId );
        doRequestErrorOnFailure( webClient, requestSettings, CloudbedsJsonResponse.class, null );
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
        LOGGER.info( "Adding credit card details for reservation " + reservationId );
        doRequestErrorOnFailure( webClient, requestSettings, CloudbedsJsonResponse.class, null );
    }

    /**
     * Sends a source lookup request to the server.
     * 
     * @param webClient web client instance to use
     * @param sourceNames names of booking source to search for (non-null)
     * @return comma-delimited list of matching source ids
     * @throws IOException
     */
    public String lookupBookingSourceIds( WebClient webClient, String... sourceNames ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createReservationSourceLookupRequest();
        JsonObject jobject = doRequest( webClient, requestSettings );
        
        // drill down to each of the 3rd party OTAs matching the given source(s)
        List<String> sourceIds = StreamSupport.stream(
                jobject.get( "result" ).getAsJsonArray().spliterator(), false )
                .filter( o -> "OTA".equals( o.getAsJsonObject().get( "text" ).getAsString() ) )
                .flatMap( o -> StreamSupport.stream(
                        o.getAsJsonObject().get( "children" ).getAsJsonArray().spliterator(), false ) )
                .filter( p -> Arrays.asList( sourceNames ).contains( p.getAsJsonObject().get( "text" ).getAsString() ) )
                .map( o -> o.getAsJsonObject().get( "li_attr" ).getAsJsonObject().get( "data-source-id" ).getAsString() )
                .collect( Collectors.toList() );
        
        if ( sourceIds.size() != sourceNames.length ) {
            LOGGER.warn( "Mismatch on booking source lookup: found source ids {} for sources {}", sourceIds, Arrays.toString( sourceNames ) );
        }
        if ( sourceIds.isEmpty() ) {
            throw new IOException( "Unable to find sources for " + Arrays.toString( sourceNames ) );
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
    @Deprecated // use 1-step process rather than 2-step auth/capture
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
     * Attempts to charge using the most recent card on the given booking.
     * 
     * @param webClient web client instance to use
     * @param res CB reservation
     * @param amount how much
     * @throws IOException
     */
    public void chargeCardForBooking( WebClient webClient, Reservation res, BigDecimal amount ) throws IOException {
        LOGGER.info( "Begin PROCESS CHARGE for reservation " + res.getReservationId()  + " for " + getCurrencyFormat().format( amount ) );
        WebRequest requestSettings = jsonRequestFactory.createAddNewProcessPaymentRequest( res.getReservationId(), 
                res.getBookingRooms().get( 0 ).getId(), res.getCreditCardId(), amount, "Autocharging -RONBOT" );
        doRequest( webClient, requestSettings, CloudbedsJsonResponse.class, 
                (resp, jsonResp) -> {
                    // 2021-03-19: success when charge from Stripe is incomplete??
                    LOGGER.info( "Cloudbeds says successfully charged booking. " + jsonResp );
                    LOGGER.info( "Confirming reservation balance to see if anything has changed..." );
                    Reservation updatedRes = getReservationRetry( webClient, res.getReservationId() );
                    if ( updatedRes.getPaidValue().equals( res.getPaidValue() ) ) {
                        LOGGER.info( "LIES!! Balance hasn't changed!" );
                        throw new RecordPaymentFailedException( "It doesn't look like we charged the booking." );
                    }
                }, 
                (resp, jsonResp) -> {
                    LOGGER.error( "Failed to charge booking. " + jsonResp );
                    try {
                        addNote( webClient, res.getReservationId(), "Failed to charge booking: " + resp.getMessage() );
                    }
                    catch ( IOException e ) {
                        LOGGER.error( "Failed to add note for failed charge..." );
                    }
                    throw new RecordPaymentFailedException( "Failed to charge booking." );
                } );
    }

    public Guest getGuestById( WebClient webClient, String guestId ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createFindGuestByIdRequest( guestId );
        JsonObject jobject = doRequest( webClient, requestSettings );
        JsonObject guest = jobject.get( "data" ).getAsJsonObject().get( "guest" ).getAsJsonObject();
        return gsonIdentity.fromJson( guest, Guest.class );
    }

    /**
     * Adds a reservation. I assume you know what you need to pass in here.
     * 
     * @param webClient
     * @param jsonData reservation data
     * @returns response
     * @throws IOException
     */
    public JsonObject addReservation( WebClient webClient, String jsonData ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createAddReservationRequest( jsonData );
        return doRequest( webClient, requestSettings );
    }

    /**
     * Sends a ping request to the server.
     * 
     * @param webClient web client instance to use
     * @throws IOException
     */
    public void ping( WebClient webClient ) throws IOException {
        WebRequest requestSettings = jsonRequestFactory.createPingRequest();
        doRequest( webClient, requestSettings );
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
        JsonObject jobject = doRequest( webClient, requestSettings );

        final DateTimeFormatter DD_MM_YYYY_HH_MM = DateTimeFormatter.ofPattern( "dd/MM/yyyy hh:mm a" );
        List<ActivityLogEntry> logEntries = new ArrayList<ActivityLogEntry>();
        jobject.get( "aaData" ).getAsJsonArray().forEach( e -> {
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
        JsonObject jobject = doRequest( webClient, requestSettings );

        EmailTemplateInfo template = new EmailTemplateInfo();
        JsonObject elem = jobject.get( "email_template" ).getAsJsonObject();
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
     * Retrieves the Stripe confirmation email template.
     * 
     * @param webClient web client instance to use
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo getStripePaymentConfirmationEmailTemplate( WebClient webClient ) throws IOException {
        return fetchEmailTemplate( webClient, TEMPLATE_STRIPE_PAYMENT_CONFIRMATION );
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
     * Retrieves the refund successfully charged email template.
     * 
     * @param webClient web client instance to use
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo getRefundSuccessfulEmailTemplate( WebClient webClient ) throws IOException {
        return fetchEmailTemplate( webClient, TEMPLATE_REFUND_PROCESSED );
    }

    /**
     * Retrieves the covid prestay email template.
     * 
     * @param webClient web client instance to use
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo getCovidPrestayEmailTemplate( WebClient webClient ) throws IOException {
        return fetchEmailTemplate( webClient, TEMPLATE_COVID_PRESTAY );
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
     * Retrieves the payment link email template.
     * 
     * @param webClient web client instance to use
     * @return non-null email template
     * @throws IOException
     */
    public EmailTemplateInfo getPaymentLinkEmailTemplate( WebClient webClient ) throws IOException {
        return fetchEmailTemplate( webClient, TEMPLATE_PAYMENT_LINK );
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
                            getPropertyContent( webClient ).get( "email_templates" ).getAsJsonArray().spliterator(),
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

        WebRequest requestSettings = jsonRequestFactory.createGetEmailDeliveryLogRequest( reservationId );
        JsonObject jobject = doRequest( webClient, requestSettings );

        final DateTimeFormatter DD_MM_YYYY_HH_MM = DateTimeFormatter.ofPattern( "dd/MM/yyyy hh:mm a" );
        return StreamSupport.stream( jobject.get( "aaData" )
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
        doRequest( webClient, requestSettings );
    }

    /**
     * Retrieves the property info.
     * 
     * @param webClient web client instance to use
     * @returns parsed JSON response
     * @throws IOException
     */
    public JsonObject getPropertyContent( WebClient webClient ) throws IOException {
        if ( propertyContent == null ) {
            propertyContent = doRequest( webClient, jsonRequestFactory.createGetPropertyContent() );
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
    public <T> T fromJson( String json, Class<T> clazz ) {
        try {
            return gson.fromJson( json, clazz );
        }
        catch ( RuntimeException ex ) {
            LOGGER.error( json );
            LOGGER.error( "Error attempting to parse JSON", ex );
            throw ex;
        }
    }

    /**
     * Same as {@link #doRequest(WebClient, WebRequest, Class, BiConsumer, BiConsumer)} but throws
     * an exception on failure.
     * 
     * @param <T> expected response type
     * @param webClient
     * @param req
     * @param clazz * @param req requested type
     * @param clazz expected response type
     * @param fnOnSuccess something to run when response is successful (optional)
     * @return parsed response
     * @throws IOException
     */
    private <T extends CloudbedsJsonResponse> T doRequestErrorOnFailure( WebClient webClient, WebRequest req, Class<T> clazz, BiConsumer<T, String> fnOnSuccess ) throws IOException {
        return doRequest( webClient, req, clazz, fnOnSuccess, ( resp, jsonResp ) -> {
            LOGGER.error( "Response: " + jsonResp );
            throw new UnrecoverableFault( "Failed operation on: " + req.getUrl() );
        } );
    }

    /**
     * Perform a web request and return the (parsed) JSON response as the given type.
     * 
     * @param <T> expected response type
     * @param webClient
     * @param req requested type
     * @param clazz expected response type
     * @param fnOnSuccess something to run when response is successful (optional)
     * @param fnOnError something to run when response fails (optional)
     * @return parsed response
     * @throws IOException
     */
    private <T extends CloudbedsJsonResponse> T doRequest( WebClient webClient, WebRequest req, Class<T> clazz, BiConsumer<T, String> fnOnSuccess, BiConsumer<T, String> fnOnError ) throws IOException {
        Page redirectPage = webClient.getPage( req );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );
        LOGGER.debug( "Response status {}: {}",
                redirectPage.getWebResponse().getStatusCode(),
                redirectPage.getWebResponse().getStatusMessage() );

        T response = fromJson( redirectPage.getWebResponse().getContentAsString(), clazz );
        if ( response != null && false == response.isSuccess() && StringUtils.isNotBlank( response.getVersion() )
                && false == response.getVersion().equals( req.getRequestParameters().stream().filter( p -> p.getName().equals( "version" ) ).findFirst().get().getValue() ) ) {
            LOGGER.info( "Looks like we're using an outdated version. Updating our records." );
            jsonRequestFactory.setVersionForRequest( req, response.getVersion() );
            return doRequest( webClient, req, clazz, fnOnSuccess, fnOnError );
        }
        else if ( response == null || false == response.isSuccess() ) {
            if ( fnOnError != null ) {
                fnOnError.accept( response, redirectPage.getWebResponse().getContentAsString() );
            }
        }
        if ( fnOnSuccess != null ) {
            fnOnSuccess.accept( response, redirectPage.getWebResponse().getContentAsString() );
        }
        return response;
    }

    /**
     * Perform a web request and return the result as a JSON object. If the initial request resulted
     * in a failure due to an incorrect version, the version will be updated and retried.
     * 
     * @param webClient
     * @param req request to submit
     * @return raw response
     * @throws IOException
     */
    private JsonObject doRequest( WebClient webClient, WebRequest req ) throws IOException {
        Page redirectPage = webClient.getPage( req );
        LOGGER.debug( redirectPage.getWebResponse().getContentAsString() );

        JsonElement jelement = fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        if ( null == jelement ) {
            throw new UnrecoverableFault( "Failed operation on: " + req.getUrl() );
        }

        JsonObject jobject = jelement.getAsJsonObject();
        if ( null == jobject ) {
            throw new UnrecoverableFault( "Failed operation on: " + req.getUrl() );
        }

        if ( jobject.get( "success" ) != null && false == jobject.get( "success" ).getAsBoolean() ) {
            String version = jobject.get( "version" ) == null ? null : jobject.get( "version" ).getAsString();
            if ( version == null ) {
                LOGGER.error( "Unexpected error." );
            }
            else if ( StringUtils.isNotBlank( version ) && false == jsonRequestFactory.getVersionForRequest( req ).equals( version ) ) {
                LOGGER.info( "Looks like we're using an outdated version. Updating our records." );
                jsonRequestFactory.setVersionForRequest( req, version );
                return doRequest( webClient, req );
            }
            LOGGER.error( redirectPage.getWebResponse().getContentAsString() );
            throw new UnrecoverableFault( "Failed operation on: " + req.getUrl() );
        }
        return jobject;
    }

}