
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlListItem;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlPasswordInput;
import org.htmlunit.html.HtmlSubmitInput;
import org.htmlunit.html.HtmlTextInput;
import org.htmlunit.html.HtmlUnorderedList;
import org.htmlunit.util.Cookie;
import org.htmlunit.util.NameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.macbackpackers.beans.CardDetails;
import com.macbackpackers.beans.HostelworldBooking;
import com.macbackpackers.beans.HostelworldBookingDate;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.MissingUserDataException;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.exceptions.WebResponseException;
import com.macbackpackers.services.FileService;

@Component
public class HostelworldScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    public static final FastDateFormat DATE_FORMAT_BOOKED_DATE = FastDateFormat.getInstance( "d MMM yy HH:mm:ss" );
    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );

    /** the title on the login page */
    public static final String LOGIN_PAGE_TITLE = "Hostels Inbox Login";

    /** for saving login credentials */
    private static final String COOKIE_FILE = "hostelworld.cookies";
    
    @Autowired
    private ApplicationContext context;
    
    @Autowired
    private FileService fileService;

    @Autowired
    private WordPressDAO wordPressDAO;

    @Autowired
    @Qualifier( "gsonForHostelworld" )
    private Gson gson;

    /**
     * Logs into Hostelworld providing the necessary credentials.
     * 
     * @param webClient web client
     * @return the page after login
     * @throws IOException
     */
    public HtmlPage doLogin( WebClient webClient ) throws IOException {
        return doLogin( webClient,
                wordPressDAO.getOption( "hbo_hw_username" ),
                wordPressDAO.getOption( "hbo_hw_password" ) );
    }

    /**
     * Logs into Hostelworld providing the necessary credentials.
     * 
     * @param webClient web client to use
     * @param username user credentials
     * @param password user credentials
     * @return the page after login
     * @throws IOException
     */
    public HtmlPage doLogin( WebClient webClient, String username, String password ) throws IOException {
        LOGGER.info( "Logging into Hostelworld" );
        HtmlPage loginPage = webClient.getPage( wordPressDAO.getMandatoryOption( "hbo_hw_url_login" ) );
        HtmlForm form = loginPage.getFormByName( "loginForm" );

        HtmlTextInput hostelNumberField = form.getInputByName( "HostelNumber" );
        HtmlTextInput usernameField = form.getInputByName( "Username" );
        HtmlPasswordInput passwordField = form.getInputByName( "Password" );

        // Change the value of the text fields
        hostelNumberField.setValueAttribute( wordPressDAO.getMandatoryOption( "hbo_hw_hostelnumber" ) );
        usernameField.setValueAttribute( username );
        passwordField.setValueAttribute( password );
        HtmlSubmitInput loginLink = loginPage.getFirstByXPath( "//input[@type='submit' and @value='Login']" );

        HtmlPage nextPage = loginLink.click();
        LOGGER.info( "Finished logging in" );

        // if we get redirected to the login page again...
        if ( LOGIN_PAGE_TITLE.equals( StringUtils.trim( nextPage.getTitleText() ) ) ) {
            LOGGER.error( nextPage.asXml() );
            throw new UnrecoverableFault( "Unable to login to Hostelworld. Incorrect password?" );
        }

        // save credentials to disk so we don't need to do this again
        // for the immediate future
        fileService.writeCookiesToFile( webClient, COOKIE_FILE );
        return nextPage;
    }

    /**
     * Logs in and navigates to the given page. Synchronized to avoid multiple simultaneous
     * logins if there are a bunch of jobs scraping at the same time.
     * 
     * @param webClient web client
     * @param url the HW page to go to
     * @return the accessed page
     * @throws IOException
     */
    public synchronized HtmlPage gotoPage( WebClient webClient, String url ) throws IOException {

        // load most recent cookies from disk first
        fileService.loadCookiesFromFile( webClient, COOKIE_FILE );

        HtmlPage nextPage = webClient.getPage( url );

        // if we got redirected, then login
        if ( LOGIN_PAGE_TITLE.equals( StringUtils.trim( nextPage.getTitleText() ) ) ) {

            // create a new web client just for logging in
            try (WebClient webClientForLogin = context.getBean( "webClientForHostelworldLogin", WebClient.class )) {
                doLogin( webClientForLogin );
                nextPage = webClientForLogin.getPage( url );

                // if we still get redirected to the login page...
                if ( LOGIN_PAGE_TITLE.equals( StringUtils.trim( nextPage.getTitleText() ) ) ) {
                    throw new UnrecoverableFault( "Unable to login to Hostelworld! Has the password changed?" );
                }

                // now copy the cookies (with credentials) over to the web client
                // the other web client has JS disabled so that it works faster
                for ( Iterator<Cookie> i = webClientForLogin.getCookieManager().getCookies().iterator() ; i.hasNext() ; ) {
                    webClient.getCookieManager().addCookie( i.next() );
                }
            }
        }
        return nextPage;
    }

    /**
     * Dumps all HW bookings checking in on the given date.
     * 
     * @param webClient web client
     * @param arrivalDate date of arrival
     * @throws IOException
     * @throws ParseException
     */
//    @Transactional
    public void dumpBookingsForArrivalDate( WebClient webClient, Date arrivalDate ) throws IOException, ParseException {

        // first delete any existing bookings on the given arrival date
        wordPressDAO.deleteHostelworldBookingsWithArrivalDate( arrivalDate );
        dumpBookings( webClient, getBookingsURLForArrivalsByDate( arrivalDate ) );
    }

    /**
     * Dumps all HW bookings booked on the given date.
     * 
     * @param webClient web client
     * @param bookedDate date when reservation was booked
     * @throws IOException
     * @throws ParseException
     */
//    @Transactional
    public void dumpBookingsForBookedDate( WebClient webClient, Date bookedDate ) throws IOException, ParseException {

        // first delete any existing bookings on the given booked date
        wordPressDAO.deleteHostelworldBookingsWithBookedDate( bookedDate );
        dumpBookings( webClient, getBookingsURLForBookedByDate( bookedDate ) );
    }

    /**
     * Dumps all HW bookings on the given bookings page.
     * 
     * @param webClient web client
     * @param bookingsPageUrl URL of page containing all the <em>searched</em> booking entries
     * @throws IOException
     * @throws ParseException
     */
    private void dumpBookings( WebClient webClient, String bookingsPageUrl ) throws IOException, ParseException {

        HtmlPage bookingsPage = gotoPage( webClient, bookingsPageUrl );

        // the anchor link has an extra quote that fucks up the HTML DOM so we will need to
        // extract the bookings using a regex
        String bookingsPageHtml = bookingsPage.getWebResponse().getContentAsString();

        // now loop through the bookings, saving each one
        Pattern pattern = Pattern.compile( "\\/booking\\/view\\/([\\d]+)" );
        Matcher matcher = pattern.matcher( bookingsPageHtml );
        while ( matcher.find() ) {
            String bookingRef = matcher.group( 1 );
            LOGGER.info( "Booking Ref: " + bookingRef );
            dumpSingleBooking( webClient, bookingRef );
        }
    }

    /**
     * Writes a single booking to the database.
     * 
     * @param webClient web client
     * @param bookingRef HW booking ref
     * @return the booking page that was saved
     * @throws IOException
     * @throws ParseException
     */
    public HtmlPage dumpSingleBooking( WebClient webClient, String bookingRef ) throws IOException, ParseException {

        HtmlPage bookingPage = webClient.getPage( "https://inbox.hostelworld.com/booking/view/" + bookingRef );
        List<?> custDetails = bookingPage.getByXPath( "//ul[@class='customer-details']" );
        LOGGER.debug( custDetails.size() + " cust detail blocks found (expecting 1)" );
        HtmlUnorderedList ul = HtmlUnorderedList.class.cast( custDetails.get( 0 ) );
        LOGGER.debug( ul.getChildElementCount() + " customer elements found" );

        HostelworldBooking hwBooking = new HostelworldBooking();
        hwBooking.setBookingRef( bookingRef );

        Iterator<DomElement> it = ul.getChildElements().iterator();
        hwBooking.setGuestName( processCustDetailsElement( it ) );
        LOGGER.debug( "Name: " + hwBooking.getGuestName() );

        hwBooking.setGuestEmail( processCustDetailsElement( it ) );
        LOGGER.debug( "Email: " + hwBooking.getGuestEmail() );

        hwBooking.setGuestPhone( processCustDetailsElement( it ) );
        LOGGER.debug( "Phone: " + hwBooking.getGuestPhone() );

        hwBooking.setGuestNationality( processCustDetailsElement( it ) );
        LOGGER.debug( "Nationality: " + hwBooking.getGuestNationality() );

        String bookedDate = processCustDetailsElement( it );
        LOGGER.debug( "Booked on: " + bookedDate );
        hwBooking.setBookedDate( convertHostelworldDate( bookedDate ) );

        hwBooking.setBookingSource( processCustDetailsElement( it ) );
        LOGGER.debug( "Source: " + hwBooking.getBookingSource() );

        String arrivalDate = processCustDetailsElement( it );
        LOGGER.debug( "Arriving: " + arrivalDate );
        hwBooking.setArrivalTime( convertHostelworldDate( arrivalDate + " 00:00:00" ) );

        String arrivalTime = processCustDetailsElement( it );
        LOGGER.debug( "Arrival Time: " + arrivalTime );
        hwBooking.setArrivalTime( setTimeOnDate( hwBooking.getArrivalTime(), arrivalTime ) );

        hwBooking.setPersons( processCustDetailsElement( it ) );
        LOGGER.debug( "Persons: " + hwBooking.getPersons() );

        // fetch the room/date details
        List<?> roomDetails = bookingPage.getByXPath( "//h2[@class='room-details']/../div[@class='table']/div/ul[not(@class='title')]" );
        for ( Object elem : roomDetails ) {
            ul = HtmlUnorderedList.class.cast( elem );
            it = ul.getChildElements().iterator();

            HostelworldBookingDate bookingDate = new HostelworldBookingDate();
            assert it.hasNext();
            bookedDate = StringUtils.trim( it.next().getTextContent() );
            assert it.hasNext();
            String acknowledged = StringUtils.trim( it.next().getTextContent() );
            assert it.hasNext();
            String roomType = StringUtils.trim( it.next().getTextContent() );
            assert it.hasNext();
            String persons = StringUtils.trim( it.next().getTextContent() );
            assert it.hasNext();
            String price = StringUtils.trim( it.next().getTextContent() );
            LOGGER.debug( "  Date: " + bookedDate );
            LOGGER.debug( "  Acknowledged: " + acknowledged );
            LOGGER.debug( "  Room: " + roomType );
            LOGGER.debug( "  Persons: " + persons );
            LOGGER.debug( "  Price: " + price );
            bookingDate.setBookedDate( convertHostelworldDate( bookedDate + " 00:00:00" ) );
            bookingDate.setRoomType( roomType );
            bookingDate.setRoomTypeId( wordPressDAO.getRoomTypeIdForHostelworldLabel( roomType ) );

            bookingDate.setPersons( Integer.parseInt( persons ) );
            bookingDate.setPrice( new BigDecimal( price.replaceAll( "GBP", "" ).trim() ) );
            LOGGER.debug( "  Adding HW booked date: " + ToStringBuilder.reflectionToString( bookingDate ) );
            hwBooking.addBookedDate( bookingDate );
        }

        // fetch the payment totals
        List<?> paymentDetails = bookingPage.getByXPath( "//div[@class='prices-total']/div/ul" );
        assert paymentDetails.size() == 5;
        ul = HtmlUnorderedList.class.cast( paymentDetails.get( 0 ) );
        it = ul.getChildElements().iterator();

        assert it.hasNext();
        it.next();
        assert it.hasNext();
        String serviceCharge = StringUtils.trim( it.next().getTextContent() );
        LOGGER.debug( "Service Charge: " + serviceCharge );

        ul = HtmlUnorderedList.class.cast( paymentDetails.get( 1 ) );
        it = ul.getChildElements().iterator();
        assert it.hasNext();
        it.next();
        assert it.hasNext();
        String paymentTotal = StringUtils.trim( it.next().getTextContent() );
        LOGGER.debug( "Total Price inc. Service Charge: " + paymentTotal );
        hwBooking.setPaymentTotal( new BigDecimal( paymentTotal.replaceAll( "GBP", "" ).trim() ) );

        ul = HtmlUnorderedList.class.cast( paymentDetails.get( 2 ) );
        it = ul.getChildElements().iterator();
        assert it.hasNext();
        it.next();
        assert it.hasNext();
        String deposit = StringUtils.trim( it.next().getTextContent() );
        LOGGER.debug( "Deposit: " + deposit );

        ul = HtmlUnorderedList.class.cast( paymentDetails.get( 4 ) );
        it = ul.getChildElements().iterator();
        assert it.hasNext();
        it.next();
        assert it.hasNext();
        String balanceDue = StringUtils.trim( it.next().getTextContent() );
        LOGGER.debug( "Balance Due: " + balanceDue );
        hwBooking.setPaymentOutstanding( new BigDecimal( balanceDue.replaceAll( "GBP", "" ).trim() ) );

        LOGGER.debug( "Adding HW Booking: " + ToStringBuilder.reflectionToString( hwBooking ) );
        wordPressDAO.insertHostelworldBooking( hwBooking );
        return bookingPage;
    }

    /**
     * Retrieve the cardholder details from the given booking. Not sure if it needs to be
     * synchronized but had a few issues with not pulling back card details even when they were
     * present when running multiple jobs?
     * 
     * @param webClient web client
     * @param bookingRef e.g. HWL-555-1234567
     * @return the card details for the booking
     * @throws ParseException
     * @throws IOException
     */
    public synchronized CardDetails getCardDetails( WebClient webClient, String bookingRef ) throws ParseException, IOException {
        Pattern p = Pattern.compile( "HWL-" + wordPressDAO.getMandatoryOption( "hbo_hw_hostelnumber" ) + "-([\\d]+)" );
        Matcher m = p.matcher( bookingRef );
        if ( false == m.find() ) {
            throw new ParseException( "WTF kind of booking is this? " + bookingRef, 0 );
        }

        CardDetails cardDetails = new CardDetails();
        HtmlPage ccPage = gotoPage( webClient, "https://inbox.hostelworld.com/booking/ccdata/login/" + m.group( 1 ) );
        HtmlTextInput userInput = ccPage.getFirstByXPath( "//input[@id='username']" );
        userInput.setValueAttribute( wordPressDAO.getOption( "hbo_hw_username" ) );
        HtmlPasswordInput passwordInput = ccPage.getFirstByXPath( "//input[@id='password']" );
        passwordInput.setValueAttribute( wordPressDAO.getOption( "hbo_hw_password" ) );

        HtmlSubmitInput submitButton = ccPage.getFirstByXPath( "//input[@value='Submit']" );
        submitButton.click();

        // cc details should be visible now if we view the booking
        ccPage = gotoPage( webClient, "https://inbox.hostelworld.com/booking/view/" + m.group( 1 ) );
        HtmlListItem item = ccPage.getFirstByXPath( "//h2[text()='Payment Details']/../ul/li[1]" );
        if( item == null ) {
            throw new MissingUserDataException( "Card details missing from HostelWorld." );
        }
        cardDetails.setName( item.getTextContent().replaceAll( "Card Holder's Name : ", "" ) );

        item = ccPage.getFirstByXPath( "//h2[text()='Payment Details']/../ul/li[2]" );
        p = Pattern.compile( "([\\d]{2})/[\\d]{2}([\\d]{2})" );
        m = p.matcher( item.getTextContent() );
        if ( false == m.find() ) {
            throw new ParseException( "Unable to get card expiry date", 0 );
        }
        cardDetails.setExpiry( m.group( 1 ) + m.group( 2 ) );

        item = ccPage.getFirstByXPath( "//h2[text()='Payment Details']/../ul/li[4]" );
        String cardNumber = item.getTextContent().replaceAll( "Credit Card Number : ", "" );
        if ( false == NumberUtils.isDigits( cardNumber ) ) {
            throw new ParseException( "Unable to get card number", 0 );
        }
        cardDetails.setCardNumber( cardNumber );

        return cardDetails;
    }

    /**
     * Acknowledge that we've taken full payment for the booking.
     * 
     * @param webClient the logged-in web client
     * @param bookingRef HWL reservation id (property ID not required)
     * @throws IOException
     */
    public synchronized void acknowledgeFullPaymentTaken( WebClient webClient, String bookingRef ) throws IOException {

        if ( StringUtils.isBlank( wordPressDAO.getOption( "hbo_hw_password" ) ) ) {
            LOGGER.info( "Hostelworld password not set. Unable to acknowledge full payment on HWL." );
            return;
        }

        LOGGER.info( "Acknowledging full payment taken for HWL-" + bookingRef );

        // strip property id if provided
        String hwlBookingId = bookingRef;
        Pattern p = Pattern.compile( wordPressDAO.getMandatoryOption( "hbo_hw_hostelnumber" ) + "-([\\d]+)" );
        Matcher m = p.matcher( bookingRef );
        if ( m.find() ) {
            hwlBookingId = m.group( 1 );
        }

        gotoPage( webClient, "https://inbox.hostelworld.com/booking/view/" + hwlBookingId );

        WebRequest webRequest = createBasePostRequest( "https://inbox.hostelworld.com/booking/payment/confirm" );
        webRequest.setRequestParameters( Arrays.asList(
                new NameValuePair( "custId", hwlBookingId ),
                new NameValuePair( "bookingPaymentAck", "1" ) ) );
        Page redirectPage = webClient.getPage( webRequest );

        LOGGER.info( "Going to: " + redirectPage.getUrl().getPath() );
        LOGGER.info( redirectPage.getWebResponse().getContentAsString() );

        JsonElement jelement = gson.fromJson( redirectPage.getWebResponse().getContentAsString(), JsonElement.class );
        if ( jelement == null || jelement.getAsJsonObject().get( "success" ) == null ) {
            LOGGER.error( redirectPage.getWebResponse().getContentAsString() );
            throw new WebResponseException( "Acknowledgement probably failed...", redirectPage.getWebResponse() );
        }
    }

    /**
     * Moves the iterator over 2 elements and returns the text content of the 2nd element. Throws
     * assertion error if iterator runs out of elements.
     * 
     * @param it Iterator of HTML list items
     * @return text content of 2nd element
     */
    private String processCustDetailsElement( Iterator<DomElement> it ) {
        assert it.hasNext();
        DomElement elem = it.next();
        assert it.hasNext();
        elem = it.next();
        return StringUtils.trim( elem.getTextContent() );
    }

    /**
     * Converts a "Hostelworld" date to a Date object.
     * 
     * @param dateAsString e.g. 30th Jun '15 17:16:29
     * @return (non-null) date
     * @throws ParseException
     */
    private Date convertHostelworldDate( String dateAsString ) throws ParseException {
        Pattern p = Pattern.compile( "([\\d]+)[a-zA-Z]+ ([a-zA-Z]+) '([\\d]+) ([\\d]{2}:[\\d]{2}:[\\d]{2})" );
        Matcher m = p.matcher( dateAsString );
        if ( m.find() ) {
            return DATE_FORMAT_BOOKED_DATE.parse( m.group( 1 ) + " " + m.group( 2 ) + " " + m.group( 3 ) + " " + m.group( 4 ) );
        }
        throw new ParseException( "Unable to convert " + dateAsString, 0 );
    }

    /**
     * Returns the date with the hostelworld arrival time set.
     * 
     * @param date the date to start with
     * @param arrivalTime the arrival time in HH.mm format
     * @return the date with time portion set
     */
    private Date setTimeOnDate( Date date, String arrivalTime ) {
        Calendar cal = Calendar.getInstance();
        cal.setTime( date );

        Pattern p = Pattern.compile( "([\\d]{1,2}).([\\d]{2})" );
        Matcher m = p.matcher( arrivalTime );
        if ( m.find() ) {
            cal.set( Calendar.HOUR, Integer.parseInt( m.group( 1 ) ) );
            cal.set( Calendar.MINUTE, Integer.parseInt( m.group( 2 ) ) );
        }
        return cal.getTime();
    }

    /**
     * Returns the booking URL for all checkins occurring on the given date.
     * 
     * @param date date to query
     * @return URL of bookings arriving on this date
     */
    private String getBookingsURLForArrivalsByDate( Date date ) {
        return wordPressDAO.getMandatoryOption( "hbo_hw_url_bookings" )
                .replaceAll( "__DATE__", DATE_FORMAT_YYYY_MM_DD.format( date ) )
                .replaceAll( "__DATE_TYPE__", "arrivaldate" );
    }
    
    /**
     * Returns the booking URL for all reservations booked on the given date.
     * 
     * @param date date to query
     * @return URL of bookings booked on this date
     */
    private String getBookingsURLForBookedByDate( Date date ) {
        return wordPressDAO.getMandatoryOption( "hbo_hw_url_bookings" )
                .replaceAll( "__DATE__", DATE_FORMAT_YYYY_MM_DD.format( date ) )
                .replaceAll( "__DATE_TYPE__", "bookeddate" );
    }

    /**
     * Creates a standard POST request.
     * 
     * @param url the page to POST to
     * @return request options
     */
    private WebRequest createBasePostRequest( String url ) throws IOException {
        WebRequest requestSettings = new WebRequest( new URL( url ), HttpMethod.POST );
        requestSettings.setAdditionalHeader( "Accept", "*/*" );
        requestSettings.setAdditionalHeader( "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8" );
        requestSettings.setAdditionalHeader( "Referer", "https://inbox.hostelworld.com/" );
        requestSettings.setAdditionalHeader( "Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8" );
        requestSettings.setAdditionalHeader( "Accept-Encoding", "gzip, deflate, br" );
        requestSettings.setAdditionalHeader( "Origin", "https://inbox.hostelworld.com" );
        requestSettings.setAdditionalHeader( "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Safari/537.36" );
        requestSettings.setCharset( StandardCharsets.UTF_8 );
        return requestSettings;
    }
}
