package com.macbackpackers.services.shuffle;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Runs the shuffle search against TSV dumps (see {@link ShuffleDumps} for the columns) in {@code shuffle.dir}:
 * rooms.tsv and assignments.tsv. Opt-in:
 * <pre>
 * mvn -Dtest=BedShuffleDumpTest -Dshuffle.dir=/tmp/shuffle -Dshuffle.reservation=187493873 test
 * </pre>
 */
@EnabledIfSystemProperty( named = "shuffle.dir", matches = ".+" )
public class BedShuffleDumpTest {

    @Test
    public void suggestFromDump() throws IOException {
        Path dir = Path.of( System.getProperty( "shuffle.dir" ) );
        long reservationId = Long.parseLong( System.getProperty( "shuffle.reservation" ) );
        int horizonDays = Integer.parseInt( System.getProperty( "shuffle.horizonDays", "14" ) );
        LocalDate today = LocalDate.parse( System.getProperty( "shuffle.today", LocalDate.now().toString() ) );

        BedCalendar calendar = ShuffleDumps.calendar( dir.resolve( "rooms.tsv" ), dir.resolve( "assignments.tsv" ), today );

        calendar.bookings().stream()
                .filter( b -> b.reservationId() == reservationId )
                .forEach( b -> {
                    RoomTypeFeasibility f = RoomTypeFeasibility.check( calendar, b.roomTypeId(), b.checkin(), b.checkout() );
                    System.out.println( b.label() + " room type " + b.roomTypeId() + " capacity " + f.getCapacity()
                            + " demand " + f.getDemandByNight() );
                    printFreeNights( calendar, b );
                } );

        long start = System.currentTimeMillis();
        ShuffleSuggestion s = BedShuffleService.suggest( calendar, reservationId,
                new BedShuffleService.Options().today( today ).horizonDays( horizonDays ) );
        System.out.println( s.describe() );
        System.out.println( "Took " + ( System.currentTimeMillis() - start ) + "ms" );
    }

    /** Per bed of the booking's room type, which of its nights are free and who blocks the others. */
    private static void printFreeNights( BedCalendar calendar, ShuffleBooking booking ) {
        Map<String, String> rows = new TreeMap<>();
        for ( ShuffleBed bed : calendar.bedsOfRoomType( booking.roomTypeId() ) ) {
            StringBuilder sb = new StringBuilder();
            for ( LocalDate n = booking.checkin(); n.isBefore( booking.checkout() ); n = n.plusDays( 1 ) ) {
                ShuffleBooking night = new ShuffleBooking( "night", -1, "", booking.roomTypeId(), n, n.plusDays( 1 ), false );
                List<ShuffleBooking> occ = calendar.occupants( bed.id(), night, calendar.currentAssignment() );
                sb.append( n ).append( '=' ).append( occ.isEmpty() ? "FREE"
                        : occ.get( 0 ).label() + ( occ.get( 0 ).pinned() ? "[pinned]" : "" )
                                + "[" + occ.get( 0 ).checkin() + ".." + occ.get( 0 ).checkout() + "]" )
                        .append( "  " );
            }
            rows.put( bed.label(), sb.toString() );
        }
        rows.forEach( ( bed, s ) -> System.out.println( "  " + bed + ": " + s ) );
    }
}
