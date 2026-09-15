package com.macbackpackers.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.macbackpackers.beans.HousekeepingBed;
import com.macbackpackers.beans.OccupancyVersion;
import com.macbackpackers.beans.RoomBed;
import com.macbackpackers.dao.WordPressDAO;

/**
 * Projects current {@link OccupancyVersion} rows onto housekeeping bedsheet badges for a selected date.
 */
@Service
public class HousekeepingStatusService {

    private static final List<String> EXCLUDED_ROOM_TYPES = List.of( "LT_MALE", "LT_FEMALE", "LT_MIXED", "OVERFLOW" );

    @Autowired
    private WordPressDAO dao;

    @Autowired( required = false )
    private MercurePublisher mercurePublisher;

    /**
     * Recomputes all housekeeping beds for {@code selectedDate}, persists projection, publishes Mercure.
     *
     * @return projected beds
     */
    public List<HousekeepingBed> recomputeAndPublish( LocalDate selectedDate ) {
        List<HousekeepingBed> beds = recompute( selectedDate );
        dao.replaceHousekeepingBeds( beds );
        if ( mercurePublisher != null ) {
            mercurePublisher.publishHousekeepingSnapshot( beds, selectedDate );
        }
        return beds;
    }

    /**
     * Recomputes bedsheet badges without publishing.
     */
    public List<HousekeepingBed> recompute( LocalDate selectedDate ) {
        int nDayChange = resolveNDayChange();
        List<OccupancyVersion> currents = dao.fetchCurrentOccupancy();
        Map<String, List<OccupancyVersion>> byRoom = indexByRoomId( currents );

        List<HousekeepingBed> result = new ArrayList<>();
        for ( RoomBed roomBed : dao.fetchActiveHousekeepingRooms() ) {
            if ( EXCLUDED_ROOM_TYPES.contains( roomBed.getRoomType() ) ) {
                continue;
            }
            List<OccupancyVersion> roomOccupancy = byRoom.getOrDefault( roomBed.getId(), List.of() );
            OccupancyVersion overnight = findOvernightGuest( roomOccupancy, selectedDate );
            OccupancyVersion continuation = overnight == null ? null
                    : findStayContinuation( overnight, currents, selectedDate );
            OccupancyVersion closure = findClosureForDate( roomOccupancy, selectedDate );

            HousekeepingBed bed = new HousekeepingBed();
            bed.setRoomId( roomBed.getId() );
            bed.setRoom( roomBed.getRoom() );
            bed.setBedName( roomBed.getBedName() );
            bed.setRoomType( roomBed.getRoomType() );
            bed.setCapacity( roomBed.getCapacity() );
            bed.setSelectedDate( selectedDate );
            bed.setUpdatedAt( new java.sql.Timestamp( System.currentTimeMillis() ) );

            applyOccupancyToBed( bed, overnight, continuation, closure, selectedDate, nDayChange );
            result.add( bed );
        }
        return result;
    }

    void applyOccupancyToBed( HousekeepingBed bed, OccupancyVersion overnight, OccupancyVersion continuation,
            OccupancyVersion closure, LocalDate selectedDate, int nDayChange ) {
        LocalDate effectiveCheckout = overnight == null ? null
                : ( continuation != null ? continuation.getCheckoutLocalDate() : overnight.getCheckoutLocalDate() );

        if ( overnight != null ) {
            bed.setGuestName( overnight.getGuestName() );
            bed.setCheckinDate( overnight.getCheckinLocalDate() );
            bed.setCheckoutDate( effectiveCheckout );
            bed.setDataHref( overnight.isClosure() ? "room_closures" : null );
        }
        else if ( closure != null ) {
            bed.setGuestName( closure.getGuestName() );
            bed.setCheckinDate( closure.getCheckinLocalDate() );
            bed.setCheckoutDate( closure.getCheckoutLocalDate() );
            bed.setDataHref( "room_closures" );
        }

        bed.setBedsheet( computeBedsheet( overnight, continuation, closure, selectedDate, nDayChange ) );
    }

    String computeBedsheet( OccupancyVersion overnight, OccupancyVersion continuation, OccupancyVersion closure,
            LocalDate selectedDate, int nDayChange ) {
        // Room closure that occupies the bed for the selected overnight window
        if ( closure != null && ( overnight == null || closure == overnight ) ) {
            if ( isDepartingToday( closure.getCheckoutLocalDate(), selectedDate )
                    || coversSelectedOvernight( closure, selectedDate ) ) {
                return HousekeepingBedsheet.CHANGE_ROOM_CLOSURE;
            }
        }

        if ( overnight == null ) {
            return HousekeepingBedsheet.EMPTY;
        }

        if ( overnight.isClosure() ) {
            return HousekeepingBedsheet.CHANGE_ROOM_CLOSURE;
        }

        String status = StringUtils.defaultString( overnight.getBedStatus() ).toLowerCase();
        // Booked but not checked in → treat as empty for cleaning
        if ( "confirmed".equals( status ) || "booked".equals( status ) || "not_confirmed".equals( status )
                || "courtesy_hold".equals( status ) ) {
            if ( false == overnight.isInHouse() ) {
                return HousekeepingBedsheet.EMPTY;
            }
        }

        LocalDate effectiveCheckout = continuation != null
                ? continuation.getCheckoutLocalDate()
                : overnight.getCheckoutLocalDate();
        LocalDate checkin = overnight.getCheckinLocalDate();

        if ( isDepartingToday( effectiveCheckout, selectedDate ) ) {
            if ( overnight.isInHouse() || "checked_in".equals( status ) ) {
                return HousekeepingBedsheet.CHANGE_IN_HOUSE;
            }
            return HousekeepingBedsheet.CHANGE_CHECKED_OUT;
        }

        if ( effectiveCheckout != null && effectiveCheckout.isAfter( selectedDate ) ) {
            if ( checkin != null && nDayChange > 0
                    && ChronoUnit.DAYS.between( checkin, selectedDate ) % nDayChange == 0
                    && ChronoUnit.DAYS.between( selectedDate, effectiveCheckout ) > 1 ) {
                return HousekeepingBedsheet.N_DAY_CHANGE;
            }
            return HousekeepingBedsheet.NO_CHANGE;
        }

        return HousekeepingBedsheet.EMPTY;
    }

    private int resolveNDayChange() {
        String opt = dao.getOption( "hbo_bedsheets_change_after_days" );
        if ( StringUtils.isBlank( opt ) ) {
            return 1000; // effectively off (same as PHP)
        }
        try {
            int n = Integer.parseInt( opt.trim() );
            return n > 0 ? n : 1000;
        }
        catch ( NumberFormatException e ) {
            return 1000;
        }
    }

    private static Map<String, List<OccupancyVersion>> indexByRoomId( List<OccupancyVersion> currents ) {
        Map<String, List<OccupancyVersion>> byRoom = new HashMap<>();
        for ( OccupancyVersion o : currents ) {
            if ( StringUtils.isBlank( o.getRoomId() ) ) {
                continue;
            }
            byRoom.computeIfAbsent( o.getRoomId(), k -> new ArrayList<>() ).add( o );
        }
        return byRoom;
    }

    /**
     * Overnight occupancy for housekeeping: checkin &lt; selected AND checkout &gt;= selected.
     */
    static OccupancyVersion findOvernightGuest( List<OccupancyVersion> roomOccupancy, LocalDate selectedDate ) {
        OccupancyVersion best = null;
        for ( OccupancyVersion o : roomOccupancy ) {
            if ( false == coversSelectedOvernight( o, selectedDate ) ) {
                continue;
            }
            // Prefer guest over closure when both present
            if ( best == null || ( best.isClosure() && false == o.isClosure() ) ) {
                best = o;
            }
        }
        return best;
    }

    static OccupancyVersion findClosureForDate( List<OccupancyVersion> roomOccupancy, LocalDate selectedDate ) {
        for ( OccupancyVersion o : roomOccupancy ) {
            if ( o.isClosure() && coversSelectedOvernight( o, selectedDate ) ) {
                return o;
            }
        }
        return null;
    }

    /**
     * Same guest / same bed continuing the day after checkout (existing SQL c2 join).
     */
    static OccupancyVersion findStayContinuation( OccupancyVersion overnight, List<OccupancyVersion> allCurrent,
            LocalDate selectedDate ) {
        if ( overnight == null || overnight.getCheckoutLocalDate() == null ) {
            return null;
        }
        LocalDate checkout = overnight.getCheckoutLocalDate();
        for ( OccupancyVersion o : allCurrent ) {
            if ( o == overnight || o.isClosure() ) {
                continue;
            }
            if ( false == Objects.equals( overnight.getRoomId(), o.getRoomId() ) ) {
                continue;
            }
            if ( false == Objects.equals( overnight.getGuestName(), o.getGuestName() ) ) {
                continue;
            }
            if ( checkout.equals( o.getCheckinLocalDate() ) ) {
                return o;
            }
        }
        return null;
    }

    static boolean coversSelectedOvernight( OccupancyVersion o, LocalDate selectedDate ) {
        LocalDate checkin = o.getCheckinLocalDate();
        LocalDate checkout = o.getCheckoutLocalDate();
        if ( checkin == null || checkout == null || selectedDate == null ) {
            return false;
        }
        return checkin.isBefore( selectedDate ) && false == checkout.isBefore( selectedDate );
    }

    static boolean isDepartingToday( LocalDate checkout, LocalDate selectedDate ) {
        return checkout != null && checkout.equals( selectedDate );
    }
}
