package com.macbackpackers.scrapers;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.macbackpackers.beans.analytics.CompetitorBedPriceFact;
import com.macbackpackers.beans.analytics.PropertyMatch;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * Google Hotels (Travel) Edinburgh listings via a dedicated Chrome user-data-dir
 * (not the Cloudbeds/Booking profile).
 * <p>
 * Stay dates are driven by the protobuf {@code ts=} trip blob in the search URL (the same shape
 * Google writes after a UI date change). {@code dates=} alone is ignored when the profile has a
 * cached range — we assert the Check-in / Check-out inputs match before parsing prices.
 * <p>
 * List pages expose a single "Prices starting from £N" per property — for hostel searches that
 * is almost always the cheapest dorm bed. We store it as {@code room_category=dorm}. When the
 * page also exposes an explicit private-room lowest price we store {@code private}; otherwise
 * only dorm rows are written.
 */
@Component
public class GoogleHotelsScraper {

    private static final Logger LOGGER = LoggerFactory.getLogger( GoogleHotelsScraper.class );

    private static final Pattern PRICE_ARIA = Pattern.compile(
            "aria-label=\"Prices starting from £(\\d+(?:\\.\\d+)?),\\s*([^\"]+)\"" );

    /** Matches Google Hotels date inputs, e.g. {@code Sat, Nov 21}. */
    static final DateTimeFormatter GHS_DATE_LABEL =
            DateTimeFormatter.ofPattern( "EEE, MMM d", Locale.ENGLISH );

    @Value( "${ghs.chrome.userdatadir:chromeprofile/ghs}" )
    private String ghsUserDataDir;

    @Value( "${chromescraper.driver.options:}" )
    private String chromeOptionsBase;

    @Value( "${chromescraper.driver.useragent:}" )
    private String chromeUserAgent;

    @Value( "${chromescraper.maxwait.seconds:60}" )
    private int maxWaitSeconds;

    @Value( "${ghs.scrape.pause.millis:2500}" )
    private long pauseBetweenDatesMillis;

    /** Opens a Chrome session bound to the dedicated GHS profile. Caller must {@link WebDriver#quit()}. */
    public ChromeDriver openDriver() {
        WebDriverManager.chromedriver().setup();
        ChromeDriver driver = new ChromeDriver( buildChromeOptions() );
        driver.manage().timeouts().pageLoadTimeout( Duration.ofSeconds( Math.max( 60, maxWaitSeconds ) ) );
        try {
            driver.manage().window().setSize( new Dimension( 1920, 1200 ) );
        }
        catch ( Exception e ) {
            LOGGER.debug( "GHS window resize skipped: {}", e.toString() );
        }
        return driver;
    }

    public void pauseBetweenDates() throws InterruptedException {
        if ( pauseBetweenDatesMillis > 0 ) {
            Thread.sleep( pauseBetweenDatesMillis );
        }
    }

    /**
     * Scrapes one stay-date of city hostel listings using an existing driver.
     *
     * @return dorm (+ optional private) facts tagged {@code property_code=edn}
     */
    public List<CompetitorBedPriceFact> scrapeStayDate( WebDriver driver, String city, LocalDate stayDate,
            List<PropertyMatch> ownMatches ) {
        LocalDate checkout = stayDate.plusDays( 1 );
        String url = buildSearchUrl( city, stayDate, checkout );
        LOGGER.info( "Google Hotels: {}", url );
        driver.get( url );
        dismissConsentIfPresent( driver );
        assertStayDatesSelected( driver, stayDate, checkout );
        waitForListings( driver );
        // SPA may refresh results; re-check before parsing.
        assertStayDatesSelected( driver, stayDate, checkout );
        String html = driver.getPageSource();
        if ( looksLikeCaptcha( html ) ) {
            throw new IllegalStateException(
                    "Google Hotels captcha / unusual-traffic page for stay_date=" + stayDate );
        }
        List<ListingPrice> listings = parseListingPrices( html );
        if ( listings.isEmpty() ) {
            throw new IllegalStateException(
                    "Google Hotels returned no listing prices for stay_date=" + stayDate
                            + " (empty results or selector drift); url=" + driver.getCurrentUrl() );
        }
        LOGGER.info( "Google Hotels {} {}: {} listing(s) (check-in UI={}, url={})",
                city, stayDate, listings.size(),
                readDateInput( driver, "Check-in" ), driver.getCurrentUrl() );
        return toFacts( listings, stayDate, ownMatches );
    }

    /**
     * Prefer {@code /travel/search} with both {@code dates=} and the protobuf {@code ts=} trip blob.
     */
    public static String buildSearchUrl( String city, LocalDate checkin, LocalDate checkout ) {
        String q = URLEncoder.encode( city.trim() + " hostels", StandardCharsets.UTF_8 );
        String ts = URLEncoder.encode( buildTravelTs( city.trim(), checkin, checkout ), StandardCharsets.UTF_8 );
        return "https://www.google.com/travel/search?q=" + q
                + "&dates=" + checkin + "," + checkout
                + "&ts=" + ts
                + "&curr=GBP";
    }

    /**
     * Builds the Google Travel {@code ts} URL parameter for a city hostel search in GBP.
     * Edinburgh uses Knowledge Graph mid {@code /m/02m77}. Round-tripped against live scrape URLs.
     */
    static String buildTravelTs( String city, LocalDate checkin, LocalDate checkout ) {
        String mid = edinburghMidFor( city );
        byte[] place = concat( ld( 1, mid.getBytes( StandardCharsets.UTF_8 ) ),
                ld( 7, city.getBytes( StandardCharsets.UTF_8 ) ) );
        byte[] dest = ld( 1, concat( ld( 2, place ), ld( 3, new byte[0] ) ) );
        int nights = (int) ( checkout.toEpochDay() - checkin.toEpochDay() );
        byte[] dateRange = concat( ld( 1, dateMsg( checkin ) ), ld( 2, dateMsg( checkout ) ), vi( 3, nights ) );
        byte[] dates = ld( 2, concat( ld( 2, dateRange ), ld( 6, vi( 1, 1 ) ) ) );
        // Currency blob from live GBP searches: field1=':\x03GBP', field3 empty
        byte[] currency = ld( 5, new byte[] {
                0x0a, 0x05, 0x3a, 0x03, 0x47, 0x42, 0x50, 0x1a, 0x00 } );
        byte[] root = concat( vi( 1, 1 ), ld( 3, concat( dest, dates ) ), currency );
        return Base64.getUrlEncoder().withoutPadding().encodeToString( root );
    }

    private static String edinburghMidFor( String city ) {
        if ( "Edinburgh".equalsIgnoreCase( city ) ) {
            return "/m/02m77";
        }
        return "";
    }

    private static byte[] dateMsg( LocalDate d ) {
        return concat( vi( 1, d.getYear() ), vi( 2, d.getMonthValue() ), vi( 3, d.getDayOfMonth() ) );
    }

    private static byte[] vi( int field, int value ) {
        return concat( tag( field, 0 ), varint( value ) );
    }

    private static byte[] ld( int field, byte[] payload ) {
        return concat( tag( field, 2 ), varint( payload.length ), payload );
    }

    private static byte[] tag( int field, int wire ) {
        return varint( ( field << 3 ) | wire );
    }

    private static byte[] varint( int value ) {
        int n = value;
        byte[] buf = new byte[5];
        int i = 0;
        while ( ( n & ~0x7f ) != 0 ) {
            buf[i++] = (byte) ( ( n & 0x7f ) | 0x80 );
            n >>>= 7;
        }
        buf[i++] = (byte) n;
        byte[] out = new byte[i];
        System.arraycopy( buf, 0, out, 0, i );
        return out;
    }

    private static byte[] concat( byte[]... parts ) {
        int len = 0;
        for ( byte[] p : parts ) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int at = 0;
        for ( byte[] p : parts ) {
            System.arraycopy( p, 0, out, at, p.length );
            at += p.length;
        }
        return out;
    }

    static String formatDateLabel( LocalDate date ) {
        return date.format( GHS_DATE_LABEL );
    }

    void assertStayDatesSelected( WebDriver driver, LocalDate checkin, LocalDate checkout ) {
        String expectedIn = formatDateLabel( checkin );
        String expectedOut = formatDateLabel( checkout );
        if ( datesMatch( driver, expectedIn, expectedOut ) ) {
            LOGGER.info( "GHS dates selected: {} → {}", expectedIn, expectedOut );
            return;
        }
        throw new IllegalStateException(
                "GHS date mismatch (ts= did not stick): wanted " + expectedIn + " → " + expectedOut
                        + " but UI shows '" + readDateInput( driver, "Check-in" ) + "' → '"
                        + readDateInput( driver, "Check-out" ) + "'; url=" + driver.getCurrentUrl() );
    }

    private static boolean datesMatch( WebDriver driver, String expectedIn, String expectedOut ) {
        return expectedIn.equalsIgnoreCase( StringUtils.trimToEmpty( readDateInput( driver, "Check-in" ) ) )
                && expectedOut.equalsIgnoreCase( StringUtils.trimToEmpty( readDateInput( driver, "Check-out" ) ) );
    }

    private static String readDateInput( WebDriver driver, String ariaLabel ) {
        WebElement input = findVisibleDateInput( driver, ariaLabel );
        if ( input == null ) {
            return "";
        }
        String value = input.getAttribute( "value" );
        return value == null ? "" : value.trim();
    }

    /** Last visible matching input wins (dialog fields can duplicate the summary bar). */
    private static WebElement findVisibleDateInput( WebDriver driver, String ariaLabel ) {
        List<WebElement> inputs = driver.findElements(
                By.cssSelector( "input[aria-label='" + ariaLabel + "']" ) );
        WebElement visible = null;
        for ( WebElement input : inputs ) {
            try {
                if ( input.isDisplayed() ) {
                    visible = input;
                }
            }
            catch ( Exception ignored ) {
                // stale
            }
        }
        return visible;
    }

    /** Package-visible for unit tests. */
    static List<ListingPrice> parseListingPrices( String html ) {
        Map<String, ListingPrice> byName = new LinkedHashMap<>();
        Matcher m = PRICE_ARIA.matcher( html );
        while ( m.find() ) {
            BigDecimal price = new BigDecimal( m.group( 1 ) );
            String name = decodeHtml( m.group( 2 ).trim() );
            if ( StringUtils.isBlank( name ) ) {
                continue;
            }
            byName.putIfAbsent( name, new ListingPrice( name, price, null ) );
        }
        return new ArrayList<>( byName.values() );
    }

    static boolean looksLikeCaptcha( String html ) {
        if ( StringUtils.isBlank( html ) ) {
            return true;
        }
        String lower = html.toLowerCase( Locale.ROOT );
        return lower.contains( "unusual traffic" ) || lower.contains( "detected unusual" );
    }

    private List<CompetitorBedPriceFact> toFacts( List<ListingPrice> listings, LocalDate stayDate,
            List<PropertyMatch> ownMatches ) {
        List<CompetitorBedPriceFact> facts = new ArrayList<>();
        for ( ListingPrice listing : listings ) {
            boolean own = isOwn( listing.name, ownMatches );
            facts.add( fact( listing.name, "dorm", listing.lowestPrice, stayDate, own ) );
            if ( listing.privatePrice != null ) {
                facts.add( fact( listing.name, "private", listing.privatePrice, stayDate, own ) );
            }
        }
        return facts;
    }

    private static CompetitorBedPriceFact fact( String name, String roomCategory, BigDecimal price,
            LocalDate stayDate, boolean own ) {
        CompetitorBedPriceFact f = new CompetitorBedPriceFact();
        f.setPropertyCode( "edn" );
        f.setCompetitorName( name );
        f.setRoomCategory( roomCategory );
        f.setStayDate( stayDate );
        f.setLowestBedPrice( price );
        f.setCurrency( "GBP" );
        f.setSource( "ghs" );
        f.setOwn( own );
        return f;
    }

    private static boolean isOwn( String listingName, List<PropertyMatch> ownMatches ) {
        if ( ownMatches == null ) {
            return false;
        }
        for ( PropertyMatch m : ownMatches ) {
            if ( m.matchesName( listingName ) ) {
                return true;
            }
        }
        return false;
    }

    private ChromeOptions buildChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        List<String> args = new ArrayList<>();
        if ( StringUtils.isNotBlank( chromeOptionsBase ) ) {
            for ( String part : chromeOptionsBase.split( " " ) ) {
                if ( StringUtils.isBlank( part ) ) {
                    continue;
                }
                if ( part.startsWith( "user-data-dir=" ) || part.startsWith( "--user-data-dir=" ) ) {
                    continue;
                }
                args.add( part.startsWith( "--" ) ? part : "--" + part );
            }
        }
        else {
            args.add( "--disable-gpu" );
            args.add( "--start-maximized" );
            args.add( "--ignore-certificate-errors" );
            args.add( "--remote-allow-origins=*" );
        }
        args.add( "--user-data-dir=" + ghsUserDataDir );
        args.add( "--disable-blink-features=AutomationControlled" );
        // Headless ignores --start-maximized and defaults to 800x600 without an explicit size.
        if ( args.stream().noneMatch( a -> a.startsWith( "--window-size=" ) ) ) {
            args.add( "--window-size=1920,1200" );
        }
        if ( args.stream().noneMatch( a -> a.startsWith( "--ozone-override-screen-size=" ) ) ) {
            args.add( "--ozone-override-screen-size=1920,1200" );
        }
        options.addArguments( args );
        if ( StringUtils.isNotBlank( chromeUserAgent ) ) {
            options.addArguments( "--user-agent=" + chromeUserAgent.trim() );
        }
        String chromeBinaryPath = System.getProperty( "chrome.binary.path" );
        if ( StringUtils.isNotBlank( chromeBinaryPath ) ) {
            options.setBinary( chromeBinaryPath );
        }
        return options;
    }

    private void waitForListings( WebDriver driver ) {
        WebDriverWait wait = new WebDriverWait( driver, Duration.ofSeconds( Math.max( 30, maxWaitSeconds ) ) );
        try {
            wait.until( d -> {
                String src = d.getPageSource();
                return PRICE_ARIA.matcher( src ).find() || looksLikeCaptcha( src );
            } );
        }
        catch ( TimeoutException e ) {
            LOGGER.warn( "Timed out waiting for GHS price aria-labels; parsing whatever is present" );
        }
    }

    private void dismissConsentIfPresent( WebDriver driver ) {
        try {
            By[] buttons = {
                    By.xpath( "//button[contains(.,'Accept all')]" ),
                    By.xpath( "//button[contains(.,'I agree')]" ),
                    By.cssSelector( "button[aria-label='Accept all']" ),
            };
            for ( By by : buttons ) {
                var els = driver.findElements( by );
                if ( !els.isEmpty() && els.get( 0 ).isDisplayed() ) {
                    els.get( 0 ).click();
                    LOGGER.info( "Dismissed Google consent dialog" );
                    Thread.sleep( 1000 );
                    return;
                }
            }
        }
        catch ( Exception e ) {
            LOGGER.debug( "Consent dismiss skipped: {}", e.toString() );
        }
        try {
            ( (JavascriptExecutor) driver ).executeScript( "window.scrollTo(0, 600);" );
        }
        catch ( Exception ignored ) {
            // ignore
        }
    }

    private static String decodeHtml( String s ) {
        return s.replace( "&amp;", "&" )
                .replace( "&#39;", "'" )
                .replace( "&quot;", "\"" )
                .replace( "&lt;", "<" )
                .replace( "&gt;", ">" );
    }

    static final class ListingPrice {
        final String name;
        final BigDecimal lowestPrice;
        final BigDecimal privatePrice;

        ListingPrice( String name, BigDecimal lowestPrice, BigDecimal privatePrice ) {
            this.name = name;
            this.lowestPrice = lowestPrice;
            this.privatePrice = privatePrice;
        }
    }
}
