
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.html.HtmlUnorderedList;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.macbackpackers.beans.HostelworldBooking;
import com.macbackpackers.beans.HostelworldBookingDate;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.services.FileService;

@Component
@Scope( "prototype" )
public class HostelworldScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    public static final FastDateFormat DATE_FORMAT_BOOKED_DATE = FastDateFormat.getInstance( "d MMM yy HH:mm:ss" );
    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );

    /** the title on the login page */
    public static final String LOGIN_PAGE_TITLE = "Hostels Inbox Login";

    /** for saving login credentials */
    private static final String COOKIE_FILE = "hostelworld.cookies";

    @Autowired
    @Qualifier( "webClientForHostelworld" )
    private WebClient webClient;

    @Autowired
    @Qualifier( "webClientForHostelworldLogin" )
    private WebClient webClientForLogin;

    @Autowired
    private FileService fileService;

    @Autowired
    private WordPressDAO wordPressDAO;

    @Value( "${hostelworld.url.login}" )
    private String loginUrl;

    @Value( "${hostelworld.hostelnumber}" )
    private String hostelNumber;

    @Value( "${hostelworld.url.bookings}" )
    private String bookingsUrl;

    /**
     * Logs into Hostelworld providing the necessary credentials.
     * 
     * @return the page after login
     * @throws IOException
     */
    public HtmlPage doLogin() throws IOException {
        return doLogin(
                wordPressDAO.getOption( "hbo_hw_username" ),
                wordPressDAO.getOption( "hbo_hw_password" ) );
    }

    /**
     * Logs into Hostelworld providing the necessary credentials.
     * 
     * @param username user credentials
     * @param password user credentials
     * @return the page after login
     * @throws IOException
     */
    public HtmlPage doLogin( String username, String password ) throws IOException {
        LOGGER.info( "Logging into Hostelworld" );
        HtmlPage loginPage = webClientForLogin.getPage( loginUrl );
        HtmlForm form = loginPage.getFormByName( "loginForm" );

        HtmlTextInput hostelNumberField = form.getInputByName( "HostelNumber" );
        HtmlTextInput usernameField = form.getInputByName( "Username" );
        HtmlPasswordInput passwordField = form.getInputByName( "Password" );

        // Change the value of the text fields
        hostelNumberField.setValueAttribute( hostelNumber );
        usernameField.setValueAttribute( username );
        passwordField.setValueAttribute( password );

        HtmlDivision loginButtonDiv = loginPage.getHtmlElementById( "loginButton" );
        HtmlAnchor loginLink = (HtmlAnchor) loginButtonDiv.getHtmlElementsByTagName( "a" ).get( 0 );

        HtmlPage nextPage = loginLink.click();
        LOGGER.info( "Finished logging in" );
        LOGGER.debug( nextPage.asXml() );

        // if we get redirected to the login page again...
        if ( LOGIN_PAGE_TITLE.equals( StringUtils.trim( nextPage.getTitleText() ) ) ) {
            throw new UnrecoverableFault( "Unable to login to Hostelworld. Incorrect password?" );
        }

        // save credentials to disk so we don't need to do this again
        // for the immediate future
        fileService.writeCookiesToFile( webClientForLogin, COOKIE_FILE );

        // now copy the cookies (with credentials) over to the web client
        // the other web client has JS disabled so that it works faster
        for ( Iterator<Cookie> i = webClientForLogin.getCookieManager().getCookies().iterator() ; i.hasNext() ; ) {
            webClient.getCookieManager().addCookie( i.next() );
        }
        return nextPage;
    }

    /**
     * Logs in and navigates to the given page.
     * 
     * @param url the HW page to go to
     * @return the accessed page
     * @throws IOException
     */
    public HtmlPage gotoPage( String url ) throws IOException {

        // load most recent cookies from disk first
        fileService.loadCookiesFromFile( webClient, COOKIE_FILE );

        HtmlPage nextPage = webClient.getPage( url );

        // if we got redirected, then login
        if ( LOGIN_PAGE_TITLE.equals( StringUtils.trim( nextPage.getTitleText() ) ) ) {
            doLogin();
            nextPage = webClient.getPage( url );

            // if we still get redirected to the login page...
            if ( LOGIN_PAGE_TITLE.equals( StringUtils.trim( nextPage.getTitleText() ) ) ) {
                throw new UnrecoverableFault( "Unable to login to Hostelworld! Has the password changed?" );
            }
        }
        return nextPage;
    }

    /**
     * Dumps all HW bookings checking in on the given date.
     * 
     * @param arrivalDate date of arrival
     * @throws IOException
     * @throws ParseException
     */
    @Transactional
    public void dumpBookingsForArrivalDate( Date arrivalDate ) throws IOException, ParseException {

        // first delete any existing bookings on the given arrival date
        wordPressDAO.deleteHostelworldBookingsWithArrivalDate( arrivalDate );

        HtmlPage arrivalsPage = gotoPage( getBookingsURLForArrivalsByDate( arrivalDate ) );

        // the anchor link has an extra quote that fucks up the HTML DOM so we will need to
        // extract the bookings using a regex
        String bookingsPageHtml = arrivalsPage.getWebResponse().getContentAsString();

        // now loop through the bookings, saving each one
        Pattern pattern = Pattern.compile( "\\/booking\\/view\\/([\\d]+)" );
        Matcher matcher = pattern.matcher( bookingsPageHtml );
        while ( matcher.find() ) {
            String bookingRef = matcher.group( 1 );
            LOGGER.info( "Booking Ref: " + bookingRef );
            dumpSingleBooking( bookingRef );
        }

    }

    /**
     * Writes a single booking to the database.
     * 
     * @param bookingRef HW booking ref
     * @return the booking page that was saved
     * @throws IOException
     * @throws ParseException
     */
    public HtmlPage dumpSingleBooking( String bookingRef ) throws IOException, ParseException {

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

        //////////// FOR NOW - IGNORE Hostelbookers (within HW) ///////////////////
        //////////// The room type isn't displayed correctly in HW ?? /////////////
        // Workaround is being done in WordpressDAOImpl#getRoomTypeIdForHostelworldLabel() 
        //        if ( StringUtils.trimToEmpty( hwBooking.getBookingSource() ).startsWith( "Hostelbookers" ) ) {
        //            LOGGER.info( "Skipping HB record " + bookingRef );
        //            return bookingPage;
        //        }

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
        return bookingsUrl.replaceAll( "__DATE__", DATE_FORMAT_YYYY_MM_DD.format( date ) );
    }

}
