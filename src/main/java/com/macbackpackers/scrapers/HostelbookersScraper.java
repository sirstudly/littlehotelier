
package com.macbackpackers.scrapers;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.collections.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlImageInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.macbackpackers.beans.HostelworldBooking;
import com.macbackpackers.beans.HostelworldBookingDate;
import com.macbackpackers.dao.WordPressDAO;
import com.macbackpackers.exceptions.UnrecoverableFault;
import com.macbackpackers.services.FileService;

@Component
@Scope( "prototype" )
@Deprecated // No longer in use; HB merged with HW
public class HostelbookersScraper {

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    public static final FastDateFormat DATE_FORMAT_BOOKED_DATE = FastDateFormat.getInstance( "d MMM yy HH:mm:ss" );
    public static final FastDateFormat DATE_FORMAT_DD_MMMMM_YYYY = FastDateFormat.getInstance( "dd-MMMMM-yyyy" );
    public static final FastDateFormat DATE_FORMAT_DD_MMM_YY = FastDateFormat.getInstance( "dd-MMM-yy" );
    public static final FastDateFormat DATE_FORMAT_DD_MMM_YYYY = FastDateFormat.getInstance( "dd MMM yyyy" );
    public static final FastDateFormat DATE_FORMAT_EEE_DD_MMMMM_YYYY = FastDateFormat.getInstance( "EEE dd MMMMM yyyy" ); // e.g. Tue 7 July 2015

    /** for saving login credentials */
    private static final String COOKIE_FILE = "hostelbookers.cookies";

    /** &nbsp; in HTML terms */
    private static final String NON_BREAKING_SPACE = "\u00a0";

    @Autowired
    @Qualifier( "webClientForHostelworld" )
    private WebClient webClient;

    @Autowired
    private FileService fileService;

    @Autowired
    private WordPressDAO wordPressDAO;

    @Value( "${hostelbookers.url.login}" )
    private String loginUrl;

    @Value( "${hostelbookers.url.bookings}" )
    private String bookingsUrl;

    /**
     * Logs into Hostelbookers providing the necessary credentials.
     * 
     * @return the page after login
     * @throws IOException
     */
    public HtmlPage doLogin() throws IOException {
        return doLogin(
                wordPressDAO.getOption( "hbo_hb_username" ),
                wordPressDAO.getOption( "hbo_hb_password" ) );
    }

    /**
     * Logs into Hostelbookers providing the necessary credentials.
     * 
     * @param username HB username
     * @param password HB password
     * @return the page after login
     * @throws IOException
     */
    public HtmlPage doLogin( String username, String password ) throws IOException {
        LOGGER.info( "Logging into Hostelbookers" );
        HtmlPage loginPage = webClient.getPage( loginUrl );
        HtmlForm form = loginPage.getHtmlElementById( "loginForm" );

        HtmlTextInput usernameField = form.getInputByName( "strLogin" );
        HtmlPasswordInput passwordField = form.getInputByName( "strPassword" );

        // Change the value of the text fields
        usernameField.setValueAttribute( username );
        passwordField.setValueAttribute( password );

        HtmlImageInput submitButton = form.getInputByName( "login" );
        HtmlPage nextPage = (HtmlPage) submitButton.click();

        LOGGER.info( "Finished logging in" );
        LOGGER.debug( nextPage.asXml() );

        List<?> anchorList = nextPage.getByXPath( "//a[text()='click here']" );
        if ( anchorList.size() == 0 ) {
            LOGGER.error( "Processing... link not found" );
            throw new UnrecoverableFault( "Unable to login to Hostelbookers! Has the password changed?" );
        }
        nextPage = ((HtmlAnchor) anchorList.get( 0 )).click();

        // if we still get redirected to the login page...
        if ( isNotLoggedIn( nextPage ) ) {
            throw new UnrecoverableFault( "Unable to login to Hostelbookers! Has the password changed?" );
        }

        // save credentials to disk so we don't need to do this again
        // for the immediate future
        fileService.writeCookiesToFile( webClient, COOKIE_FILE );

        return nextPage;
    }

    /**
     * Logs in and navigates to the given page.
     * 
     * @param url the HB page to go to
     * @return the accessed page
     * @throws IOException
     */
    public HtmlPage gotoPage( String url ) throws IOException {

        // load most recent cookies from disk first
        fileService.loadCookiesFromFile( webClient, COOKIE_FILE );

        HtmlPage nextPage = webClient.getPage( url );

        // if we got redirected, then login
        if ( isNotLoggedIn( nextPage ) ) {
            doLogin();

            // attempt to go to the page again...
            nextPage = webClient.getPage( url );
        }
        return nextPage;
    }

    /**
     * Checks whether we are able to view the current HB page.
     * 
     * @param currentPage the page we are currently on
     * @return true if login failed, false if we have logged in
     */
    private static boolean isNotLoggedIn( HtmlPage currentPage ) {

        // if we try to access a secured page without login (or login expired)
        for ( DomElement elem : currentPage.getElementsByTagName( "h1" ) ) {
            if ( "Unauthorized".equals( elem.getTextContent() ) ) {
                return true;
            }
        }

        // this occurs if an invalid password
        for ( Object elem : currentPage.getByXPath( "//form[@id='loginForm']/b" ) ) {
            DomElement boldItem = (DomElement) elem;
            if ( "Incorrect login or password.".equals( boldItem.getTextContent() ) ) {
                return true;
            }
        }

        // this occurs if an invalid username is passed in
        for ( DomElement elem : currentPage.getElementsByTagName( "h2" ) ) {
            if ( "Reset Password".equals( elem.getTextContent() ) ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dumps all HB bookings checking in on the given date.
     * 
     * @param arrivalDate date of arrival
     * @throws IOException
     * @throws ParseException
     */
    @Transactional
    public void dumpBookingsForArrivalDate( Date arrivalDate ) throws IOException, ParseException {

        // first delete any existing bookings on the given arrival date
        wordPressDAO.deleteHostelbookersBookingsWithArrivalDate( arrivalDate );

        HtmlPage arrivalsPage = gotoPage( getBookingsURLForArrivalsByDate( arrivalDate ) );

        // find the main table
        for ( Iterator<DomElement> itTables = arrivalsPage.getElementsByTagName( "table" ).iterator() ; itTables.hasNext() ; ) {
            DomElement nextTable = itTables.next();
            if ( "reportTable".equals( nextTable.getAttribute( "class" ) ) ) {
                // find all anchors underneath
                for ( Iterator<HtmlElement> itAnchors = nextTable.getElementsByTagName( "a" ).iterator() ; itAnchors.hasNext() ; ) {
                    HtmlElement anchor = itAnchors.next();
                    LOGGER.debug( "Found booking ref: " + StringUtils.trim( anchor.getTextContent() ) + " --> " + anchor.getAttribute( "href" ) );
                    URL bookingURL = new URL( bookingsUrl );
                    dumpSingleBooking(
                            StringUtils.trim( anchor.getTextContent() ),
                            bookingURL.getProtocol() + "://" + bookingURL.getHost() + anchor.getAttribute( "href" ) );
                }
            }
        }

    }

    /**
     * Retrieves and writes a single HB booking to the datastore.
     * 
     * @param bookingRef the (HB) booking reference
     * @param dataHref the (HB) url to retrieve the booking details
     * @throws IOException
     * @throws ParseException
     */
    private void dumpSingleBooking( String bookingRef, String dataHref ) throws IOException, ParseException {

        HtmlPage bookingPage = webClient.getPage( dataHref );

        HostelworldBooking booking = new HostelworldBooking();
        booking.setBookingRef( bookingRef );
        booking.setBookingSource( "Hostelbookers" );

        // 3rd table contains the booking date
        DomElement bookingRefTable = bookingPage.getElementsByTagName( "table" ).get( 2 );
        for ( Iterator<HtmlElement> itRows = bookingRefTable.getElementsByTagName( "tr" ).iterator() ; itRows.hasNext() ; ) {
            HtmlElement tr = itRows.next();
            String fieldName = StringUtils.trim( tr.getHtmlElementDescendants().iterator().next().getTextContent() );

            if ( "Date".equalsIgnoreCase( fieldName ) ) {
                Iterator<HtmlElement> itCell = tr.getHtmlElementsByTagName( "td" ).iterator();
                itCell.next(); // skip field name
                String bookingDate = StringUtils.trim( itCell.next().getTextContent() );
                LOGGER.debug( fieldName + ": " + bookingDate );
                booking.setBookedDate( DATE_FORMAT_DD_MMM_YYYY.parse( bookingDate ) );
            }
        }

        // 4th table on the page contains the guest details
        DomElement guestDetailsTable = bookingPage.getElementsByTagName( "table" ).get( 3 );
        String firstName = "", lastName = "";
        for ( Iterator<HtmlElement> itRows = guestDetailsTable.getElementsByTagName( "tr" ).iterator() ; itRows.hasNext() ; ) {
            HtmlElement tr = itRows.next();
            String fieldName = StringUtils.trim( tr.getHtmlElementDescendants().iterator().next().getTextContent() );

            if ( "First name".equalsIgnoreCase( fieldName ) ) {
                Iterator<HtmlElement> itCell = tr.getHtmlElementsByTagName( "td" ).iterator();
                itCell.next(); // skip field name
                firstName = StringUtils.trim( itCell.next().getTextContent() );
                LOGGER.debug( fieldName + ": " + firstName );
            }

            else if ( "Last name".equalsIgnoreCase( fieldName ) ) {
                Iterator<HtmlElement> itCell = tr.getHtmlElementsByTagName( "td" ).iterator();
                itCell.next(); // skip field name
                lastName = StringUtils.trim( itCell.next().getTextContent() );
                LOGGER.debug( fieldName + ": " + lastName );
            }

            else if ( "Email".equalsIgnoreCase( fieldName ) ) {
                Iterator<HtmlElement> itCell = tr.getHtmlElementsByTagName( "td" ).iterator();
                itCell.next(); // skip field name
                booking.setGuestEmail( StringUtils.trim( itCell.next().getTextContent() ) );
                LOGGER.debug( fieldName + ": " + booking.getGuestEmail() );
            }

            else if ( "Phone".equalsIgnoreCase( fieldName ) ) {
                Iterator<HtmlElement> itCell = tr.getHtmlElementsByTagName( "td" ).iterator();
                itCell.next(); // skip field name
                booking.setGuestPhone( StringUtils.trim( itCell.next().getTextContent() ) );
                LOGGER.debug( fieldName + ": " + booking.getGuestPhone() );
            }
        }
        booking.setGuestName( firstName + " " + lastName );

        // 5th table contains the gender/arrival time
        guestDetailsTable = bookingPage.getElementsByTagName( "table" ).get( 4 );
        for ( Iterator<HtmlElement> itRows = guestDetailsTable.getElementsByTagName( "tr" ).iterator() ; itRows.hasNext() ; ) {
            HtmlElement tr = itRows.next();
            String fieldName = StringUtils.trim( tr.getHtmlElementDescendants().iterator().next().getTextContent() );

            if ( "Arrival time".equalsIgnoreCase( fieldName ) ) {
                Iterator<HtmlElement> itCell = tr.getHtmlElementsByTagName( "td" ).iterator();
                itCell.next(); // skip field name
                String arrivalTime = StringUtils.trim( itCell.next().getTextContent() );
                LOGGER.debug( fieldName + ": " + arrivalTime );
            }

            else if ( "Gender".equalsIgnoreCase( fieldName ) ) {
                Iterator<HtmlElement> itCell = tr.getHtmlElementsByTagName( "td" ).iterator();
                itCell.next(); // skip field name
                booking.setPersons( StringUtils.trim( itCell.next().getTextContent() ).replaceAll( "[\\s]+", " " ).replaceAll( " ,", "," ) );
                LOGGER.debug( fieldName + ": " + booking.getPersons() );
            }
        }

        // 8th table down should contain the booking dates
        DomElement bookingDatesTable = bookingPage.getElementsByTagName( "table" ).get( 7 );
        for ( Iterator<HtmlElement> itRows = bookingDatesTable.getElementsByTagName( "tr" ).iterator() ; itRows.hasNext() ; ) {
            HtmlElement tr = itRows.next();

            // Date, Room details, People, Cost, Total
            @SuppressWarnings( "unchecked" )
            List<HtmlElement> cells = IteratorUtils.toList( tr.getElementsByTagName( "td" ).iterator() );
            if ( cells.size() == 5 && "bookingsummary".equals( tr.getAttribute( "id" ) ) == false ) {

                String dateContent = StringUtils.trim( cells.get( 0 ).getTextContent() );
                LOGGER.debug( "  booked date: " + dateContent );
                HostelworldBookingDate bookingDate = new HostelworldBookingDate();
                bookingDate.setBookedDate( DATE_FORMAT_EEE_DD_MMMMM_YYYY.parse( dateContent ) );

                String roomType = StringUtils.trim( cells.get( 1 ).getTextContent() );
                LOGGER.debug( "  room type: " + roomType );
                bookingDate.setRoomTypeId( wordPressDAO.getRoomTypeIdForHostelworldLabel( roomType ) );

                LOGGER.debug( "  persons: " + cells.get( 2 ).getTextContent() );
                bookingDate.setPersons( Integer.parseInt( StringUtils.trim( cells.get( 2 ).getTextContent() ) ) );

                String price = StringUtils.trim( cells.get( 4 ).getTextContent() );
                LOGGER.debug( "  price for day: " + price );
                bookingDate.setPrice( new BigDecimal( price.replaceAll( "GBP", "" ).trim() ) );

                booking.addBookedDate( bookingDate );
            }

            // payment totals
            if ( cells.size() == 3 ) {
                String fieldName = StringUtils.trim( cells.get( 1 ).getTextContent().replaceAll( NON_BREAKING_SPACE, "" ) );

                // booking fee does not contribute to the total payable amount
                if ( "Booking fee".equalsIgnoreCase( fieldName ) ) {
                    String amount = StringUtils.trim( cells.get( 2 ).getTextContent() );
                    LOGGER.debug( "  booking fee: " + amount );
                }

                else if ( "12% Commission".equalsIgnoreCase( fieldName ) ) {
                    String amount = StringUtils.trim( cells.get( 2 ).getTextContent() );
                    LOGGER.debug( "  12% commission: " + amount );
                    booking.addToPaymentTotal( amount.replaceAll( "GBP", "" ).trim() );
                }

                else if ( "Total payable on arrival".equalsIgnoreCase( fieldName ) ) {
                    String amount = StringUtils.trim( cells.get( 2 ).getTextContent() );
                    LOGGER.debug( "  payment outstanding: " + amount );
                    amount = amount.replaceAll( "GBP", "" ).trim();
                    booking.addToPaymentTotal( amount );
                    booking.setPaymentOutstanding( new BigDecimal( amount ) );
                }
            }
        }

        // finally, insert our completed booking object
        wordPressDAO.insertHostelworldBooking( booking );
    }

    /**
     * Returns the booking URL for all checkins occurring on the given date.
     * 
     * @param date date to query
     * @return URL of bookings arriving on this date
     */
    private String getBookingsURLForArrivalsByDate( Date date ) {
        return bookingsUrl.replaceAll( "__DATE__", DATE_FORMAT_DD_MMMMM_YYYY.format( date ) );
    }

}
