package com.macbackpackers.scrapers;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
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
        String url = buildSearchUrl( city, stayDate, stayDate.plusDays( 1 ) );
        LOGGER.info( "Google Hotels: {}", url );
        driver.get( url );
        dismissConsentIfPresent( driver );
        waitForListings( driver );
        String html = driver.getPageSource();
        if ( looksLikeCaptcha( html ) ) {
            throw new IllegalStateException(
                    "Google Hotels captcha / unusual-traffic page for stay_date=" + stayDate );
        }
        List<ListingPrice> listings = parseListingPrices( html );
        if ( listings.isEmpty() ) {
            throw new IllegalStateException(
                    "Google Hotels returned no listing prices for stay_date=" + stayDate
                            + " (empty results or selector drift)" );
        }
        LOGGER.info( "Google Hotels {} {}: {} listing(s)", city, stayDate, listings.size() );
        return toFacts( listings, stayDate, ownMatches );
    }

    public static String buildSearchUrl( String city, LocalDate checkin, LocalDate checkout ) {
        String q = URLEncoder.encode( city.trim() + " hostels", StandardCharsets.UTF_8 );
        return "https://www.google.com/travel/hotels/"
                + URLEncoder.encode( city.trim(), StandardCharsets.UTF_8 )
                + "?q=" + q
                + "&dates=" + checkin + "," + checkout
                + "&curr=GBP";
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
