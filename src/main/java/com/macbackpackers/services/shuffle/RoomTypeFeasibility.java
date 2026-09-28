package com.macbackpackers.services.shuffle;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Per-night demand (assigned + unassigned beds) against bed count for one room type.
 * <p>
 * For single-bed bookings within one room type, a split-free assignment exists exactly when no night is over
 * capacity (interval graph colouring; in-house guests just act as beds freeing up on their checkout date). Group and
 * pinned-bed constraints can still make an under-capacity room type unsolvable, so this is a necessary check only.
 */
public class RoomTypeFeasibility {

    private final int roomTypeId;
    private final int capacity;
    private final Map<LocalDate, Integer> demandByNight;

    private RoomTypeFeasibility( int roomTypeId, int capacity, Map<LocalDate, Integer> demandByNight ) {
        this.roomTypeId = roomTypeId;
        this.capacity = capacity;
        this.demandByNight = demandByNight;
    }

    /**
     * @param from first night (inclusive)
     * @param to last checkout date (exclusive)
     */
    public static RoomTypeFeasibility check( BedCalendar calendar, int roomTypeId, LocalDate from, LocalDate to ) {
        return check( calendar, roomTypeId, from, to, b -> true );
    }

    /**
     * @param include bookings to count (e.g. assigned ones plus the target, leaving out other unassigned bookings
     *                that may stay unassigned)
     */
    public static RoomTypeFeasibility check( BedCalendar calendar, int roomTypeId, LocalDate from, LocalDate to,
            Predicate<ShuffleBooking> include ) {
        List<ShuffleBooking> bookings = calendar.bookingsOfRoomType( roomTypeId ).stream()
                .filter( include )
                .collect( Collectors.toList() );
        Map<LocalDate, Integer> demand = new LinkedHashMap<>();
        for ( LocalDate night = from; night.isBefore( to ); night = night.plusDays( 1 ) ) {
            LocalDate n = night;
            demand.put( night, (int) bookings.stream().filter( b -> b.overlaps( n, n.plusDays( 1 ) ) ).count() );
        }
        return new RoomTypeFeasibility( roomTypeId, calendar.bedsOfRoomType( roomTypeId ).size(), demand );
    }

    public int getRoomTypeId() {
        return roomTypeId;
    }

    public int getCapacity() {
        return capacity;
    }

    public Map<LocalDate, Integer> getDemandByNight() {
        return Collections.unmodifiableMap( demandByNight );
    }

    public List<LocalDate> getOverbookedNights() {
        return demandByNight.entrySet().stream()
                .filter( e -> e.getValue() > capacity )
                .map( Map.Entry::getKey )
                .collect( Collectors.toList() );
    }

    public boolean isFeasible() {
        return getOverbookedNights().isEmpty();
    }
}
