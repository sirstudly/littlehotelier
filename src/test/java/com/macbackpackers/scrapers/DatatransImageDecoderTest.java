package com.macbackpackers.scrapers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class DatatransImageDecoderTest {

    private static byte[] loadHashGif( String hashPrefix ) throws IOException {
        Path dir = Path.of( "src/main/resources/datatrans/hashes" );
        try (var stream = Files.list( dir )) {
            Path match = stream
                    .filter( path -> path.getFileName().toString().startsWith( hashPrefix ) )
                    .findFirst()
                    .orElseThrow();
            return Files.readAllBytes( match );
        }
    }

    @Test
    public void decodeKnownDigitHashesFromAttempt3() throws IOException {
        DatatransImageDecoder decoder = new DatatransImageDecoder();

        assertEquals( '7', decoder.decodeDigitImage( loadHashGif( "d6736380" ) ) );
        assertEquals( '9', decoder.decodeDigitImage( loadHashGif( "a6c5c8d9" ) ) );
        assertEquals( '8', decoder.decodeDigitImage( loadHashGif( "80d04a75" ) ) );
        assertEquals( '0', decoder.decodeDigitImage( loadHashGif( "5ae4417e" ) ) );
        assertEquals( '4', decoder.decodeDigitImage( loadHashGif( "e8cdb706" ) ) );
    }

    @Test
    public void decodeAttempt3CardNumber() throws IOException {
        DatatransImageDecoder decoder = new DatatransImageDecoder();
        String card = "4147342099191159";
        String[] hashes = {
                "e8cdb706", "162821cc", "e8cdb706", "d6736380", "de8935a6", "e8cdb706",
                "65c1bcb4", "5ae4417e", "a6c5c8d9", "a6c5c8d9", "162821cc", "a6c5c8d9",
                "162821cc", "162821cc", "ecb0b10f", "a6c5c8d9"
        };

        StringBuilder decoded = new StringBuilder();
        for ( String hashPrefix : hashes ) {
            decoded.append( decoder.decodeDigitImage( loadHashGif( hashPrefix ) ) );
        }
        assertEquals( card, decoded.toString() );
    }
}
