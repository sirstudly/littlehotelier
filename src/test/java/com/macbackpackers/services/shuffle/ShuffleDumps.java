package com.macbackpackers.services.shuffle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.io.IOUtils;

import com.macbackpackers.beans.BookingAssignment;
import com.macbackpackers.beans.RoomBed;

/**
 * Reads TSV dumps of {@code wp_lh_rooms} and current {@code wp_lh_booking_assignment} rows (header line, tab
 * separated, "null" for NULL).
 * <p>
 * rooms: id, room, bed_name, room_type_id[, room_type, capacity]<br>
 * assignments: assignment_key, reservation_id, guest_name, room_id, room, room_type_id, checkin_date, checkout_date,
 * source, bed_status, in_house_yn
 */
final class ShuffleDumps {

    private ShuffleDumps() {
    }

    static BedCalendar calendar( Path rooms, Path assignments, LocalDate today ) throws IOException {
        return BedShuffleService.buildCalendar( rooms( Files.readAllLines( rooms, StandardCharsets.UTF_8 ) ),
                assignments( Files.readAllLines( assignments, StandardCharsets.UTF_8 ), a -> true ), today );
    }

    /** Loads classpath fixtures, leaving out assignment rows matching {@code exclude}. */
    static BedCalendar calendar( String rooms, String assignments, LocalDate today, Predicate<BookingAssignment> exclude ) {
        return BedShuffleService.buildCalendar( rooms( resource( rooms ) ),
                assignments( resource( assignments ), exclude.negate() ), today );
    }

    private static List<String> resource( String name ) {
        try ( InputStream in = ShuffleDumps.class.getResourceAsStream( name ) ) {
            if ( in == null ) {
                throw new IllegalArgumentException( "Missing test resource " + name );
            }
            return IOUtils.readLines( in, StandardCharsets.UTF_8 );
        }
        catch ( IOException ex ) {
            throw new UncheckedIOException( ex );
        }
    }

    private static List<RoomBed> rooms( List<String> lines ) {
        List<RoomBed> rooms = new ArrayList<>();
        for ( String[] f : read( lines ) ) {
            RoomBed r = new RoomBed();
            r.setId( f[0] );
            r.setRoom( f[1] );
            r.setBedName( f[2] );
            r.setRoomTypeId( Integer.parseInt( f[3] ) );
            if ( f.length > 5 && f[4] != null ) {
                r.setRoomType( f[4] );
                r.setCapacity( Integer.parseInt( f[5] ) );
            }
            r.setActive( "Y" );
            rooms.add( r );
        }
        return rooms;
    }

    private static List<BookingAssignment> assignments( List<String> lines, Predicate<BookingAssignment> include ) {
        List<BookingAssignment> rows = new ArrayList<>();
        long id = 1;
        for ( String[] f : read( lines ) ) {
            BookingAssignment a = new BookingAssignment();
            a.setId( id++ );
            a.setAssignmentKey( f[0] );
            a.setReservationId( f[1] == null ? null : Long.valueOf( f[1] ) );
            a.setGuestName( f[2] );
            a.setRoomId( f[3] );
            a.setRoom( f[4] );
            a.setRoomTypeId( f[5] == null ? null : Integer.valueOf( f[5] ) );
            a.setCheckinDate( LocalDate.parse( f[6] ) );
            a.setCheckoutDate( LocalDate.parse( f[7] ) );
            a.setSource( f[8] );
            a.setBedStatus( f[9] );
            a.setInHouseYn( f[10] );
            if ( include.test( a ) ) {
                rows.add( a );
            }
        }
        return rows;
    }

    private static List<String[]> read( List<String> lines ) {
        List<String[]> rows = new ArrayList<>();
        int columns = lines.get( 0 ).split( "\t", -1 ).length;
        String partial = null;
        for ( String line : lines.subList( 1, lines.size() ) ) {
            // a newline inside a value (e.g. a block's reason) splits one row over several lines
            line = partial == null ? line : partial + " " + line;
            if ( line.isBlank() ) {
                partial = null;
                continue;
            }
            String[] f = line.split( "\t", -1 );
            if ( f.length < columns ) {
                partial = line;
                continue;
            }
            partial = null;
            for ( int i = 0; i < f.length; i++ ) {
                if ( "null".equals( f[i] ) ) {
                    f[i] = null;
                }
            }
            rows.add( f );
        }
        return rows;
    }
}
