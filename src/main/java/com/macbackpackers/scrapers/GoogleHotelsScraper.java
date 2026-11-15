package com.macbackpackers.scrapers;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
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
 * The {@code dates=} query param alone is <strong>not</strong> enough: Google's check-in /
 * check-out inputs often keep a default (or profile-cached) range and price the page for that.
 * After navigation we force the calendar UI to the requested stay and verify the inputs before
 * parsing prices.
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
        applyAndVerifyStayDates( driver, stayDate, checkout );
        waitForListings( driver );
        String html = driver.getPageSource();
        if ( looksLikeCaptcha( html ) ) {
            throw new IllegalStateException(
                    "Google Hotels captcha / unusual-traffic page for stay_date=" + stayDate );
        }
        // Re-check after listings settle — SPA may revert dates when refreshing results.
        assertStayDatesSelected( driver, stayDate, checkout );
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
     * Prefer {@code /travel/search} — {@code /travel/hotels/{city}} redirects there and the
     * {@code dates=} param alone does not drive the calendar widget.
     */
    public static String buildSearchUrl( String city, LocalDate checkin, LocalDate checkout ) {
        String q = URLEncoder.encode( city.trim() + " hostels", StandardCharsets.UTF_8 );
        return "https://www.google.com/travel/search?q=" + q
                + "&dates=" + checkin + "," + checkout
                + "&curr=GBP";
    }

    static String formatDateLabel( LocalDate date ) {
        return date.format( GHS_DATE_LABEL );
    }

    /**
     * Opens the date picker and selects check-in / check-out, then asserts the inputs match.
     */
    void applyAndVerifyStayDates( WebDriver driver, LocalDate checkin, LocalDate checkout ) {
        String expectedIn = formatDateLabel( checkin );
        String expectedOut = formatDateLabel( checkout );
        if ( datesMatch( driver, expectedIn, expectedOut ) ) {
            LOGGER.info( "GHS dates already selected: {} → {}", expectedIn, expectedOut );
            return;
        }
        LOGGER.info( "GHS dates UI shows '{}' → '{}'; setting {} → {}",
                readDateInput( driver, "Check-in" ), readDateInput( driver, "Check-out" ),
                expectedIn, expectedOut );

        WebDriverWait wait = new WebDriverWait( driver, Duration.ofSeconds( Math.max( 30, maxWaitSeconds ) ) );
        WebElement checkinInput = wait.until(
                ExpectedConditions.elementToBeClickable( By.cssSelector( "input[aria-label='Check-in']" ) ) );
        checkinInput.click();

        clickCalendarDay( driver, wait, checkin );
        clickCalendarDay( driver, wait, checkout );
        clickDoneIfPresent( driver );

        try {
            wait.until( d -> datesMatch( d, expectedIn, expectedOut ) );
        }
        catch ( TimeoutException e ) {
            throw new IllegalStateException(
                    "Failed to set GHS dates to " + expectedIn + " → " + expectedOut
                            + "; UI shows '" + readDateInput( driver, "Check-in" ) + "' → '"
                            + readDateInput( driver, "Check-out" ) + "'; url=" + driver.getCurrentUrl(),
                    e );
        }
        // Give results a moment to requery after date change
        try {
            Thread.sleep( 1500 );
        }
        catch ( InterruptedException ie ) {
            Thread.currentThread().interrupt();
        }
    }

    void assertStayDatesSelected( WebDriver driver, LocalDate checkin, LocalDate checkout ) {
        String expectedIn = formatDateLabel( checkin );
        String expectedOut = formatDateLabel( checkout );
        if ( false == datesMatch( driver, expectedIn, expectedOut ) ) {
            throw new IllegalStateException(
                    "GHS date mismatch after scrape wait: wanted " + expectedIn + " → " + expectedOut
                            + " but UI shows '" + readDateInput( driver, "Check-in" ) + "' → '"
                            + readDateInput( driver, "Check-out" ) + "'" );
        }
    }

    private void clickCalendarDay( WebDriver driver, WebDriverWait wait, LocalDate day ) {
        // Ensure a calendar grid is open (check-in click should have opened it).
        ensureCalendarVisible( driver, wait );
        if ( false == tryClickIsoDay( driver, day ) ) {
            // Navigate forward up to 14 months (horizon scrapes are near-term; profile may open far ahead).
            for ( int i = 0; i < 14 && false == tryClickIsoDay( driver, day ); i++ ) {
                if ( false == clickMonthNav( driver, false ) ) {
                    break;
                }
                try {
                    Thread.sleep( 400 );
                }
                catch ( InterruptedException ie ) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        // If still missing (calendar opened on a later month), step backward.
        if ( false == tryClickIsoDay( driver, day ) ) {
            for ( int i = 0; i < 14 && false == tryClickIsoDay( driver, day ); i++ ) {
                if ( false == clickMonthNav( driver, true ) ) {
                    break;
                }
                try {
                    Thread.sleep( 400 );
                }
                catch ( InterruptedException ie ) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if ( false == tryClickIsoDay( driver, day ) ) {
            // Fallback: aria-label contains month name + day + year
            String month = day.format( DateTimeFormatter.ofPattern( "MMMM", Locale.ENGLISH ) );
            String ariaSnippet = month + " " + day.getDayOfMonth() + ", " + day.getYear();
            List<WebElement> byAria = driver.findElements( By.cssSelector(
                    "[role='gridcell'][aria-label*='" + ariaSnippet + "'], "
                            + "div[role='button'][aria-label*='" + ariaSnippet + "'], "
                            + "button[aria-label*='" + ariaSnippet + "']" ) );
            if ( byAria.isEmpty() ) {
                throw new IllegalStateException(
                        "GHS calendar day not found for " + day + " (iso + aria). Page date UI='"
                                + readDateInput( driver, "Check-in" ) + "'" );
            }
            byAria.get( 0 ).click();
        }
    }

    private void ensureCalendarVisible( WebDriver driver, WebDriverWait wait ) {
        try {
            wait.until( d -> false == d.findElements( By.cssSelector(
                    "[data-iso], [role='gridcell'], div[jsname][data-ical]" ) ).isEmpty()
                    || false == d.findElements( By.cssSelector( "[role='dialog']" ) ).isEmpty() );
        }
        catch ( TimeoutException e ) {
            // Some builds keep the calendar in-DOM without those attrs; continue to click attempts.
            LOGGER.warn( "GHS calendar grid not detected after Check-in click; trying day selectors anyway" );
        }
    }

    private boolean tryClickIsoDay( WebDriver driver, LocalDate day ) {
        String iso = day.toString();
        List<WebElement> cells = driver.findElements( By.cssSelector(
                "[data-iso='" + iso + "'], [data-ical*='" + iso + "']" ) );
        for ( WebElement cell : cells ) {
            try {
                if ( cell.isDisplayed() ) {
                    cell.click();
                    return true;
                }
            }
            catch ( Exception ignored ) {
                // try next
            }
        }
        // JS click fallback for covered overlays
        if ( false == cells.isEmpty() ) {
            try {
                ( (JavascriptExecutor) driver ).executeScript( "arguments[0].click();", cells.get( 0 ) );
                return true;
            }
            catch ( Exception ignored ) {
                return false;
            }
        }
        return false;
    }

    private boolean clickMonthNav( WebDriver driver, boolean previous ) {
        String[] labels = previous
                ? new String[] { "Previous month", "Previous", "Go to previous month" }
                : new String[] { "Next month", "Next", "Go to next month" };
        for ( String label : labels ) {
            List<WebElement> buttons = driver.findElements( By.cssSelector(
                    "button[aria-label='" + label + "'], div[role='button'][aria-label='" + label + "']" ) );
            for ( WebElement b : buttons ) {
                try {
                    if ( b.isDisplayed() ) {
                        b.click();
                        return true;
                    }
                }
                catch ( Exception ignored ) {
                    // try next
                }
            }
        }
        return false;
    }

    private void clickDoneIfPresent( WebDriver driver ) {
        for ( String text : List.of( "Done", "Apply", "OK" ) ) {
            List<WebElement> buttons = driver.findElements(
                    By.xpath( "//button[normalize-space()='" + text + "']"
                            + " | //div[@role='button'][normalize-space()='" + text + "']" ) );
            for ( WebElement b : buttons ) {
                try {
                    if ( b.isDisplayed() ) {
                        b.click();
                        LOGGER.info( "Clicked GHS date picker '{}'", text );
                        return;
                    }
                }
                catch ( Exception ignored ) {
                    // try next
                }
            }
        }
    }

    private static boolean datesMatch( WebDriver driver, String expectedIn, String expectedOut ) {
        return expectedIn.equalsIgnoreCase( StringUtils.trimToEmpty( readDateInput( driver, "Check-in" ) ) )
                && expectedOut.equalsIgnoreCase( StringUtils.trimToEmpty( readDateInput( driver, "Check-out" ) ) );
    }

    private static String readDateInput( WebDriver driver, String ariaLabel ) {
        List<WebElement> inputs = driver.findElements(
                By.cssSelector( "input[aria-label='" + ariaLabel + "']" ) );
        if ( inputs.isEmpty() ) {
            return "";
        }
        String value = inputs.get( 0 ).getAttribute( "value" );
        return value == null ? "" : value.trim();
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
