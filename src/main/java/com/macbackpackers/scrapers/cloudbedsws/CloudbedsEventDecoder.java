
package com.macbackpackers.scrapers.cloudbedsws;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Decodes Cloudbeds calendar WebSocket payloads into {@link CloudbedsCalendarEvent}s.
 * <p>
 * See {@code CLOUDBEDS_WEBSOCKET_PROTOCOL.md} for the full protocol. Two shapes are handled:
 * <ul>
 * <li><b>on_migrate</b> – a base64 string that is <i>raw DEFLATE</i> (headerless zlib, decode with
 * {@code new Inflater(true)}) which inflates to JSON in a column-oriented {@code {keys, rows}}
 * layout. This is the full calendar snapshot.</li>
 * <li><b>changes / room_assign</b> – the inner {@code data.Events} is already a plain array of
 * objects (no columnar form).</li>
 * </ul>
 * This class is stateless; all methods are static.
 */
public final class CloudbedsEventDecoder {

    private CloudbedsEventDecoder() {
        // utility class
    }

    /**
     * Decodes the {@code data} field of an {@code on_migrate} message (the full calendar snapshot).
     *
     * @param base64Data the base64-encoded, raw-DEFLATE-compressed JSON snapshot
     * @return list of events parsed from {@code Events} (and {@code NonAssignedReservations}); never null
     * @throws DataFormatException if the payload cannot be inflated as raw DEFLATE
     */
    public static List<CloudbedsCalendarEvent> decodeOnMigrate( String base64Data ) throws DataFormatException {
        byte[] compressed = Base64.getDecoder().decode( base64Data.trim() );
        String json = inflateRawDeflate( compressed );
        JsonObject root = JsonParser.parseString( json ).getAsJsonObject();

        List<CloudbedsCalendarEvent> events = new ArrayList<>();
        events.addAll( decodeColumnar( root.get( "Events" ) ) );
        events.addAll( decodeColumnar( root.get( "NonAssignedReservations" ) ) );
        return events;
    }

    /**
     * Decodes the {@code data} object of a {@code changes} / {@code room_assign} payload.
     *
     * @param payloadData the {@code data} object (containing an {@code Events} array)
     * @return list of events; never null
     */
    public static List<CloudbedsCalendarEvent> decodeChanges( JsonObject payloadData ) {
        List<CloudbedsCalendarEvent> events = new ArrayList<>();
        if ( payloadData == null ) {
            return events;
        }
        JsonElement eventsEl = payloadData.get( "Events" );
        if ( eventsEl != null && eventsEl.isJsonArray() ) {
            for ( JsonElement el : eventsEl.getAsJsonArray() ) {
                if ( el.isJsonObject() ) {
                    events.add( new CloudbedsCalendarEvent( toStringMap( el.getAsJsonObject() ) ) );
                }
            }
        }
        return events;
    }

    /**
     * Decodes a value that may be in the columnar {@code {keys, rows}} form (as in the snapshot) or a
     * plain JSON array of objects (as in deltas). Anything else yields an empty list.
     *
     * @param element the JSON element to decode
     * @return list of events; never null
     */
    static List<CloudbedsCalendarEvent> decodeColumnar( JsonElement element ) {
        List<CloudbedsCalendarEvent> events = new ArrayList<>();
        if ( element == null || !element.isJsonObject() ) {
            // can be a boolean (e.g. NonAssignedReservations: false) or absent
            if ( element != null && element.isJsonArray() ) {
                for ( JsonElement el : element.getAsJsonArray() ) {
                    if ( el.isJsonObject() ) {
                        events.add( new CloudbedsCalendarEvent( toStringMap( el.getAsJsonObject() ) ) );
                    }
                }
            }
            return events;
        }

        JsonObject obj = element.getAsJsonObject();
        JsonElement keysEl = obj.get( "keys" );
        JsonElement rowsEl = obj.get( "rows" );
        if ( keysEl == null || !keysEl.isJsonArray() || rowsEl == null || !rowsEl.isJsonArray() ) {
            return events;
        }

        JsonArray keys = keysEl.getAsJsonArray();
        JsonArray rows = rowsEl.getAsJsonArray();
        for ( JsonElement rowEl : rows ) {
            if ( !rowEl.isJsonArray() ) {
                continue;
            }
            JsonArray row = rowEl.getAsJsonArray();
            Map<String, String> map = new LinkedHashMap<>();
            int cols = Math.min( keys.size(), row.size() );
            for ( int i = 0; i < cols; i++ ) {
                map.put( keys.get( i ).getAsString(), asString( row.get( i ) ) );
            }
            events.add( new CloudbedsCalendarEvent( map ) );
        }
        return events;
    }

    /**
     * Inflates raw DEFLATE data (no zlib/gzip header) to a UTF-8 string.
     *
     * @param compressed raw-DEFLATE bytes
     * @return inflated UTF-8 string
     * @throws DataFormatException if the data is not valid raw DEFLATE
     */
    static String inflateRawDeflate( byte[] compressed ) throws DataFormatException {
        Inflater inflater = new Inflater( true ); // true = nowrap = raw DEFLATE
        try {
            inflater.setInput( compressed );
            ByteArrayOutputStream out = new ByteArrayOutputStream( Math.max( 64, compressed.length * 4 ) );
            byte[] buf = new byte[65536];
            while ( !inflater.finished() ) {
                int n = inflater.inflate( buf );
                if ( n == 0 ) {
                    if ( inflater.finished() || inflater.needsDictionary() ) {
                        break;
                    }
                    if ( inflater.needsInput() ) {
                        break; // no more input available
                    }
                }
                out.write( buf, 0, n );
            }
            return out.toString( StandardCharsets.UTF_8 );
        }
        finally {
            inflater.end();
        }
    }

    private static Map<String, String> toStringMap( JsonObject obj ) {
        Map<String, String> map = new LinkedHashMap<>();
        for ( Map.Entry<String, JsonElement> e : obj.entrySet() ) {
            map.put( e.getKey(), asString( e.getValue() ) );
        }
        return map;
    }

    private static String asString( JsonElement el ) {
        if ( el == null || el.isJsonNull() ) {
            return null;
        }
        if ( el.isJsonPrimitive() ) {
            return el.getAsString();
        }
        return el.toString();
    }
}
