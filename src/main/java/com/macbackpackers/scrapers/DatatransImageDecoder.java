package com.macbackpackers.scrapers;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.htmlunit.WebClient;
import org.htmlunit.WebResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.macbackpackers.exceptions.UnrecoverableFault;

/**
 * Decodes Datatrans digit images rendered as {@code /upp/imghdn/.../spacer.gif} GIFs.
 * Uses a bundled MD5 hash map plus optional runtime cache; unknown glyphs are matched
 * against bundled pixel templates (no native Tesseract required).
 */
@Component
public class DatatransImageDecoder {

    private static final String DIGIT_MAP_FILE = "hostelworld.datatrans.digit-map";

    private static final String BUNDLED_DIGIT_MAP = "datatrans-digit-map.properties";

    private static final String DIGIT_TEMPLATE_PREFIX = "datatrans/digits/";

    private static final String DATATRANS_ORIGIN = "https://pay.datatrans.com";

    private static final int MAX_TEMPLATE_DIFF = 70;

    private static final int MIN_TEMPLATE_MARGIN = 8;

    private final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    private final Properties digitMap = new Properties();

    private final Map<Character, boolean[][]> digitTemplates = new HashMap<>();

    public DatatransImageDecoder() {
        loadDigitMap();
        loadBundledDigitMap();
        loadDigitTemplates();
    }

    /**
     * Downloads each image URL and returns the concatenated digit string.
     */
    public String decodeImageUrls( WebClient webClient, Iterable<String> imagePaths ) throws IOException {
        StringBuilder digits = new StringBuilder();
        for ( String imagePath : imagePaths ) {
            digits.append( decodeDigitImage( webClient, imagePath ) );
        }
        return digits.toString();
    }

    /**
     * Records hash mappings for known digit images (e.g. after a successful captcha submission).
     */
    public void learnKnownMappings( WebClient webClient, Iterable<String> imagePaths, String digits ) throws IOException {
        if ( StringUtils.isBlank( digits ) ) {
            return;
        }
        int index = 0;
        for ( String imagePath : imagePaths ) {
            if ( index >= digits.length() ) {
                break;
            }
            byte[] imageBytes = downloadImage( webClient, imagePath );
            String hash = md5Hex( imageBytes );
            char digit = digits.charAt( index++ );
            digitMap.setProperty( hash, String.valueOf( digit ) );
            LOGGER.info( "Recorded Datatrans digit mapping {} -> {}", hash.substring( 0, 8 ), digit );
        }
        saveDigitMap();
    }

    char decodeDigitImage( byte[] imageBytes ) throws IOException {
        String hash = md5Hex( imageBytes );

        String cached = digitMap.getProperty( hash );
        if ( cached != null && cached.length() == 1 ) {
            return cached.charAt( 0 );
        }

        LOGGER.warn( "Unknown Datatrans digit hash {}, falling back to template match", hash.substring( 0, 8 ) );
        char digit = matchTemplate( imageBytes );
        digitMap.setProperty( hash, String.valueOf( digit ) );
        saveDigitMap();
        LOGGER.info( "Learned Datatrans digit mapping {} -> {}", hash.substring( 0, 8 ), digit );
        return digit;
    }

    private char decodeDigitImage( WebClient webClient, String imagePath ) throws IOException {
        return decodeDigitImage( downloadImage( webClient, imagePath ) );
    }

    private byte[] downloadImage( WebClient webClient, String imagePath ) throws IOException {
        String url = imagePath.startsWith( "http" ) ? imagePath : DATATRANS_ORIGIN + imagePath;
        WebResponse response = webClient.getPage( url ).getWebResponse();
        try (InputStream inputStream = response.getContentAsStream()) {
            return StreamUtils.copyToByteArray( inputStream );
        }
    }

    private char matchTemplate( byte[] imageBytes ) throws IOException {
        boolean[][] unknown = toBinaryGrid( imageBytes );
        Character bestDigit = null;
        int bestDiff = Integer.MAX_VALUE;
        int secondBestDiff = Integer.MAX_VALUE;

        for ( Map.Entry<Character, boolean[][]> entry : digitTemplates.entrySet() ) {
            int diff = gridDiff( unknown, entry.getValue() );
            if ( diff < bestDiff ) {
                secondBestDiff = bestDiff;
                bestDiff = diff;
                bestDigit = entry.getKey();
            }
            else if ( diff < secondBestDiff ) {
                secondBestDiff = diff;
            }
        }

        if ( bestDigit == null ) {
            throw new UnrecoverableFault( "No Datatrans digit templates loaded" );
        }
        if ( bestDiff > MAX_TEMPLATE_DIFF ) {
            throw new UnrecoverableFault(
                    "Unable to decode Datatrans digit image (best template diff=" + bestDiff + ")" );
        }
        if ( secondBestDiff < Integer.MAX_VALUE && bestDiff + MIN_TEMPLATE_MARGIN > secondBestDiff ) {
            throw new UnrecoverableFault(
                    "Ambiguous Datatrans digit image (best diff=" + bestDiff + ", second=" + secondBestDiff + ")" );
        }
        return bestDigit;
    }

    private void loadBundledDigitMap() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream( BUNDLED_DIGIT_MAP )) {
            if ( in == null ) {
                LOGGER.warn( "Bundled Datatrans digit map {} not found on classpath", BUNDLED_DIGIT_MAP );
                return;
            }
            digitMap.load( in );
        }
        catch ( IOException e ) {
            LOGGER.warn( "Unable to load bundled Datatrans digit map from {}", BUNDLED_DIGIT_MAP, e );
        }
    }

    private void loadDigitMap() {
        File file = new File( DIGIT_MAP_FILE );
        if ( false == file.exists() ) {
            return;
        }
        try (FileInputStream in = new FileInputStream( file )) {
            digitMap.load( in );
        }
        catch ( IOException e ) {
            LOGGER.warn( "Unable to load Datatrans digit map from {}", DIGIT_MAP_FILE, e );
        }
    }

    private void loadDigitTemplates() {
        for ( char digit = '0'; digit <= '9'; digit++ ) {
            String resource = DIGIT_TEMPLATE_PREFIX + digit + ".gif";
            try (InputStream in = getClass().getClassLoader().getResourceAsStream( resource )) {
                if ( in == null ) {
                    LOGGER.warn( "Missing Datatrans digit template {}", resource );
                    continue;
                }
                digitTemplates.put( digit, toBinaryGrid( StreamUtils.copyToByteArray( in ) ) );
            }
            catch ( IOException e ) {
                LOGGER.warn( "Unable to load Datatrans digit template {}", resource, e );
            }
        }
        if ( digitTemplates.isEmpty() ) {
            LOGGER.warn( "No Datatrans digit templates loaded" );
        }
    }

    private void saveDigitMap() throws IOException {
        try (FileOutputStream out = new FileOutputStream( DIGIT_MAP_FILE )) {
            digitMap.store( out, "Datatrans digit image hash mappings" );
        }
    }

    private static boolean[][] toBinaryGrid( byte[] imageBytes ) throws IOException {
        BufferedImage image = ImageIO.read( new ByteArrayInputStream( imageBytes ) );
        if ( image == null ) {
            throw new UnrecoverableFault( "Unable to read Datatrans digit image" );
        }
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] grid = new boolean[height][width];
        for ( int y = 0; y < height; y++ ) {
            for ( int x = 0; x < width; x++ ) {
                grid[y][x] = ( image.getRGB( x, y ) & 0xFF ) < 128;
            }
        }
        return grid;
    }

    private static int gridDiff( boolean[][] left, boolean[][] right ) {
        int minHeight = Math.min( left.length, right.length );
        int minWidth = Math.min( left[0].length, right[0].length );
        int diff = 0;
        for ( int y = 0; y < minHeight; y++ ) {
            for ( int x = 0; x < minWidth; x++ ) {
                if ( left[y][x] != right[y][x] ) {
                    diff++;
                }
            }
        }
        diff += Math.abs( left.length - right.length ) * minWidth;
        diff += Math.abs( left[0].length - right[0].length ) * minHeight;
        return diff;
    }

    private static String md5Hex( byte[] data ) {
        try {
            byte[] digest = MessageDigest.getInstance( "MD5" ).digest( data );
            StringBuilder builder = new StringBuilder( digest.length * 2 );
            for ( byte value : digest ) {
                builder.append( String.format( "%02x", value ) );
            }
            return builder.toString();
        }
        catch ( NoSuchAlgorithmException e ) {
            throw new IllegalStateException( e );
        }
    }
}
