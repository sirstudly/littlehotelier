
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
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.macbackpackers.beans.HostelworldBooking;
import com.macbackpackers.beans.HostelworldBookingDate;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.services.FileService;

@Component
@Scope( "prototype" )
public class HostelworldScraper {

    private final Logger LOGGER = LogManager.getLogger( getClass() );

    public static final FastDateFormat DATE_FORMAT_BOOKED_DATE = FastDateFormat.getInstance( "d MMM yy HH:mm:ss" );
    public static final FastDateFormat DATE_FORMAT_YYYY_MM_DD = FastDateFormat.getInstance( "yyyy-MM-dd" );

    /** for saving login credentials */
    private static final String COOKIE_FILE = "hostelworld.cookies";

    /** &nbsp; in HTML terms */
    private static final String NON_BREAKING_SPACE = "\u00a0";

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

    @Value( "${hostelworld.username}" )
    private String username;

    @Value( "${hostelworld.password}" )
    private String password;

    @Value( "${hostelworld.url.bookings}" )
    private String bookingsUrl;

    /**
     * Logs into Hostelworld providing the necessary credentials.
     * 
     * @return the page after login
     * @throws IOException
     */
    public HtmlPage login() throws IOException {
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
        if ( "Hostels Inbox Login".equals( StringUtils.trim( nextPage.getTitleText() ) ) ) {
            login();
            nextPage = webClient.getPage( url );

            // if we still get redirected to the login page...
            if ( "Hostels Inbox Login".equals( StringUtils.trim( nextPage.getTitleText() ) ) ) {
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
        Pattern pattern = Pattern.compile( "bookingdetails\\.php\\?CustID\\=([\\d]+)" );
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

        HtmlPage bookingPage = webClient.getPage( "https://secure.hostelworld.com/inbox/bookings/bookingdetails.php?CustID=" + bookingRef );
        List<DomElement> tables = bookingPage.getElementsByTagName( "table" );
        LOGGER.debug( tables.size() + " tables found" );

        HostelworldBooking hwBooking = new HostelworldBooking();
        hwBooking.setBookingRef( bookingRef );

        // seventh table on the page contains the booking summary we want to scrape
        for ( DomElement tr : tables.get( 6 ).getElementsByTagName( "tr" ) ) {
            DomElement td = tr.getFirstElementChild();
            if ( "Name:".equals( StringUtils.trim( td.getTextContent() ) ) ) {
                hwBooking.setGuestName( StringUtils.trim( tr.getElementsByTagName( "td" ).get( 2 ).getTextContent() ) );
                LOGGER.debug( "Name: " + hwBooking.getGuestName() );
            }
            else if ( "Email:".equals( StringUtils.trim( td.getTextContent() ) ) ) {
                hwBooking.setGuestEmail( StringUtils.trim( tr.getElementsByTagName( "td" ).get( 2 ).getTextContent() ) );
                LOGGER.debug( "Email: " + hwBooking.getGuestEmail() );
            }
            else if ( "Phone:".equals( StringUtils.trim( td.getTextContent() ) ) ) {
                hwBooking.setGuestPhone( StringUtils.trim( tr.getElementsByTagName( "td" ).get( 2 ).getTextContent() ) );
                LOGGER.debug( "Phone: " + hwBooking.getGuestPhone() );
            }
            else if ( "Nationality:".equals( StringUtils.trim( td.getTextContent() ) ) ) {
                hwBooking.setGuestNationality( StringUtils.trim( tr.getElementsByTagName( "td" ).get( 2 ).getTextContent() ) );
                LOGGER.debug( "Nationality: " + hwBooking.getGuestNationality() );
            }
            else if ( "Booked:".equals( StringUtils.trim( td.getTextContent() ) ) ) {
                String bookedDate = StringUtils.trim( tr.getElementsByTagName( "td" ).get( 2 ).getTextContent() );
                LOGGER.debug( "Booked on: " + bookedDate );
                hwBooking.setBookedDate( convertHostelworldDate( bookedDate ) );
            }
            else if ( "Source:".equals( StringUtils.trim( td.getTextContent() ) ) ) {
                hwBooking.setBookingSource( StringUtils.trim( tr.getElementsByTagName( "td" ).get( 2 ).getTextContent() ) );
                LOGGER.debug( "Source: " + hwBooking.getBookingSource() );
            }
            else if ( "Arriving:".equals( StringUtils.trim( td.getTextContent() ) ) ) {
                String arrivalDate = StringUtils.trim( tr.getElementsByTagName( "td" ).get( 2 ).getTextContent() );
                LOGGER.debug( "Arriving: " + arrivalDate );
                hwBooking.setArrivalTime( convertHostelworldDate( arrivalDate + " 00:00:00" ) );
            }
            else if ( "Arrival Time:".equals( StringUtils.trim( td.getTextContent() ) ) ) {
                String arrivalTime = StringUtils.trim( tr.getElementsByTagName( "td" ).get( 2 ).getTextContent() );
                LOGGER.debug( "Arrival Time: " + arrivalTime );
                hwBooking.setArrivalTime( setTimeOnDate( hwBooking.getArrivalTime(), arrivalTime ) );
            }
            else if ( "Persons:".equals( StringUtils.trim( td.getTextContent() ) ) ) {
                hwBooking.setPersons( StringUtils.trim( tr.getElementsByTagName( "td" ).get( 2 ).getTextContent() ) );
                LOGGER.debug( "Persons: " + hwBooking.getPersons() );
            }
        }

        // eighth table on the page contains the booking dates we want to scrape
        for ( DomElement tr : tables.get( 7 ).getElementsByTagName( "tr" ) ) {
            DomElement td = tr.getFirstElementChild();

            // for all rows that isn't the header row (or the footer totals)
            List<HtmlElement> columns = tr.getElementsByTagName( "td" );
            if ( false == "Date".equals( td.getTextContent().replaceAll( NON_BREAKING_SPACE, "" ).trim() )
                    && columns.size() == 9 ) {

                HostelworldBookingDate bookingDate = new HostelworldBookingDate();
                String bookedDate = StringUtils.trim( columns.get( 0 ).getTextContent() );
                String roomType = StringUtils.trim( columns.get( 4 ).getTextContent() );
                String persons = StringUtils.trim( columns.get( 6 ).getTextContent() );
                String price = StringUtils.trim( columns.get( 8 ).getTextContent() );
                LOGGER.debug( "  Date: " + bookedDate );
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
        }

        // eighth table on the page contains the payment details in the footer
        for ( DomElement tr : tables.get( 7 ).getElementsByTagName( "tr" ) ) {
            DomElement td = tr.getFirstElementChild();
            if ( "Total Price inc. Service Charge:".equals( td.getTextContent() ) ) {
                String paymentTotal = StringUtils.trim( tr.getElementsByTagName( "td" ).get( 1 ).getTextContent() );
                LOGGER.debug( "Total Price inc. Service Charge: " + paymentTotal );
                hwBooking.setPaymentTotal( new BigDecimal( paymentTotal.replaceAll( "GBP", "" ).trim() ) );
            }
            else if ( "Balance Due:".equals( td.getTextContent() ) ) {
                String balanceDue = StringUtils.trim( tr.getElementsByTagName( "td" ).get( 1 ).getTextContent() );
                LOGGER.debug( "Balance Due: " + balanceDue );
                hwBooking.setPaymentOutstanding( new BigDecimal( balanceDue.replaceAll( "GBP", "" ).trim() ) );
            }
        }

        LOGGER.debug( "Adding HW Booking: " + ToStringBuilder.reflectionToString( hwBooking ) );
        wordPressDAO.insertHostelworldBooking( hwBooking );
        return bookingPage;
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
