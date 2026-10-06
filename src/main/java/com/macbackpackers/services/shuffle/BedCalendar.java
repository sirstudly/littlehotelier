package com.macbackpackers.services.shuffle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Beds, bookings and the current booking-to-bed assignment. An assignment is a map of booking key to bed id;
 * a booking absent from the map (or mapped to null) is unassigned.
 */
public class BedCalendar {

    /** Back-to-back bookings of one reservation and room type ({@code first} checks out as {@code second} checks in) that belong on one bed. */
    public record Chain( String first, String second ) {
    }

    private final Map<String, ShuffleBed> beds = new LinkedHashMap<>();
    private final Map<String, ShuffleBooking> bookings = new LinkedHashMap<>();
    private final Map<String, String> current = new HashMap<>();
    private final Map<Integer, RoomTypeInfo> roomTypes = new HashMap<>();
    private final Set<String> consolidateGroups = new LinkedHashSet<>();
    private final List<Chain> chains = new ArrayList<>();

    public BedCalendar( Collection<ShuffleBed> beds ) {
        beds.forEach( b -> this.beds.put( b.id(), b ) );
    }

    /** Key of the group (see {@link #groups()}) a booking belongs to. */
    public static String groupKey( ShuffleBooking b ) {
        return b.reservationId() + ":" + b.roomTypeId();
    }

    /**
     * Marks groups to bring into as few rooms as they need, ignoring the rooms they span now (rooms held by pinned
     * members still count), and chains that must stay on one bed. Used by the solver and {@link #validate}.
     */
    public void consolidate( Set<String> groupKeys, List<Chain> chains ) {
        this.consolidateGroups.addAll( groupKeys );
        this.chains.addAll( chains );
    }

    public void clearConsolidation() {
        consolidateGroups.clear();
        chains.clear();
    }

    public Set<String> consolidateGroups() {
        return Collections.unmodifiableSet( consolidateGroups );
    }

    public List<Chain> chains() {
        return Collections.unmodifiableList( chains );
    }

    public Collection<ShuffleBed> beds() {
        return Collections.unmodifiableCollection( beds.values() );
    }

    public RoomTypeInfo roomType( int roomTypeId ) {
        return roomTypes.computeIfAbsent( roomTypeId, id -> RoomTypeInfo.of( id, beds.values() ) );
    }

    /**
     * @param bedId null when unassigned
     */
    public void addBooking( ShuffleBooking booking, String bedId ) {
        bookings.put( booking.key(), booking );
        if ( bedId != null ) {
            if ( false == beds.containsKey( bedId ) ) {
                throw new IllegalArgumentException( "Unknown bed " + bedId + " for booking " + booking.key() );
            }
            current.put( booking.key(), bedId );
        }
    }

    public ShuffleBed bed( String bedId ) {
        return bedId == null ? null : beds.get( bedId );
    }

    public ShuffleBooking booking( String key ) {
        return bookings.get( key );
    }

    public Collection<ShuffleBooking> bookings() {
        return Collections.unmodifiableCollection( bookings.values() );
    }

    public Map<String, String> currentAssignment() {
        return Collections.unmodifiableMap( current );
    }

    public Map<String, String> copyCurrentAssignment() {
        return new HashMap<>( current );
    }

    /** Replaces the current assignment (e.g. after applying a suggestion so the next one builds on it). */
    public void applyAssignment( Map<String, String> assignment ) {
        current.clear();
        assignment.forEach( ( k, v ) -> {
            if ( v != null ) {
                current.put( k, v );
            }
        } );
    }

    public List<ShuffleBed> bedsOfRoomType( int roomTypeId ) {
        return beds.values().stream()
                .filter( b -> b.roomTypeId() == roomTypeId )
                .collect( Collectors.toList() );
    }

    public List<ShuffleBooking> bookingsOfRoomType( int roomTypeId ) {
        return bookings.values().stream()
                .filter( b -> b.roomTypeId() == roomTypeId )
                .collect( Collectors.toList() );
    }

    /**
     * Bookings on {@code bedId} in {@code assignment} that overlap {@code booking} (excluding itself). Beds of the
     * same reservation sharing a private room (a group moved into a Quad) don't block each other.
     */
    public List<ShuffleBooking> occupants( String bedId, ShuffleBooking booking, Map<String, String> assignment ) {
        List<ShuffleBooking> result = new ArrayList<>();
        for ( Map.Entry<String, String> e : assignment.entrySet() ) {
            if ( bedId.equals( e.getValue() ) && false == e.getKey().equals( booking.key() ) ) {
                ShuffleBooking other = bookings.get( e.getKey() );
                if ( other.overlaps( booking ) && false == sharesPrivateRoom( bedId, booking, other ) ) {
                    result.add( other );
                }
            }
        }
        return result;
    }

    private boolean sharesPrivateRoom( String bedId, ShuffleBooking a, ShuffleBooking b ) {
        return a.reservationId() == b.reservationId() && false == roomType( beds.get( bedId ).roomTypeId() ).isGuestDorm();
    }

    /**
     * Checks {@code assignment} against the hard constraints. A group may not use more rooms than
     * {@link #allowedGroupRooms} allows.
     *
     * @return empty when valid
     */
    public List<String> validate( Map<String, String> assignment ) {
        return validate( assignment, Set.of(), false );
    }

    /**
     * @param relaxedKeys     bookings the solver deliberately placed outside their room type (fallback ladder or a
     *                        rule-breaking alternative); skipped by the room type and group checks
     * @param allowGroupSplit true for the "split a group across two rooms" alternative
     */
    public List<String> validate( Map<String, String> assignment, Set<String> relaxedKeys, boolean allowGroupSplit ) {
        return validate( assignment, relaxedKeys, allowGroupSplit, false );
    }

    /**
     * @param allowUnlock true for the "remove a bed lock" alternative: locked bookings may move
     */
    public List<String> validate( Map<String, String> assignment, Set<String> relaxedKeys, boolean allowGroupSplit,
            boolean allowUnlock ) {
        List<String> errors = new ArrayList<>();
        Map<String, List<ShuffleBooking>> byBed = new HashMap<>();
        for ( ShuffleBooking b : bookings.values() ) {
            String bedId = assignment.get( b.key() );
            boolean mayMove = allowUnlock && b.locked();
            if ( b.pinned() && false == mayMove && false == Objects.equals( bedId, current.get( b.key() ) ) ) {
                errors.add( "Pinned booking moved: " + b.label() );
            }
            if ( bedId == null ) {
                continue;
            }
            ShuffleBed bed = beds.get( bedId );
            if ( bed.roomTypeId() != b.roomTypeId() && false == relaxedKeys.contains( b.key() ) ) {
                errors.add( b.label() + " placed in a different room type: " + bed.label() );
            }
            byBed.computeIfAbsent( bedId, k -> new ArrayList<>() ).add( b );
        }
        byBed.forEach( ( bedId, list ) -> {
            for ( int i = 0; i < list.size(); i++ ) {
                for ( int j = i + 1; j < list.size(); j++ ) {
                    ShuffleBooking a = list.get( i );
                    ShuffleBooking b = list.get( j );
                    boolean moved = false == bedId.equals( current.get( a.key() ) ) || false == bedId.equals( current.get( b.key() ) );
                    // double bookings already on the calendar aren't ours to report
                    if ( moved && a.overlaps( b ) && false == sharesPrivateRoom( bedId, a, b ) ) {
                        errors.add( "Overlap on " + beds.get( bedId ).label() + ": "
                                + list.get( i ).label() + " and " + list.get( j ).label() );
                    }
                }
            }
        } );
        for ( Chain c : chains ) {
            String a = assignment.get( c.first() );
            String b = assignment.get( c.second() );
            boolean bothPinned = bookings.get( c.first() ).pinned() && bookings.get( c.second() ).pinned();
            if ( a != null && b != null && false == a.equals( b ) && false == bothPinned ) {
                errors.add( "Bed change between " + bookings.get( c.first() ).label() + " on " + beds.get( a ).label()
                        + " and " + bookings.get( c.second() ).label() + " on " + beds.get( b ).label() );
            }
        }
        if ( allowGroupSplit ) {
            return errors;
        }
        for ( List<ShuffleBooking> group : groups().values() ) {
            List<ShuffleBooking> members = group.stream()
                    .filter( b -> false == relaxedKeys.contains( b.key() ) )
                    .collect( Collectors.toList() );
            Set<String> roomsNow = rooms( members, assignment );
            if ( roomsNow.size() > allowedGroupRooms( members ) ) {
                errors.add( "Group " + members.get( 0 ).reservationId() + " split across rooms " + roomsNow );
            }
        }
        return errors;
    }

    /** Beds of one reservation within one room type, for reservations with more than one bed. */
    public Map<String, List<ShuffleBooking>> groups() {
        Map<String, List<ShuffleBooking>> groups = new LinkedHashMap<>();
        for ( ShuffleBooking b : bookings.values() ) {
            if ( b.reservationId() > 0 ) {
                groups.computeIfAbsent( groupKey( b ), k -> new ArrayList<>() ).add( b );
            }
        }
        groups.values().removeIf( g -> g.size() < 2 );
        return groups;
    }

    /**
     * Most rooms a group may use: one, or as many as it needs when bigger than a room, or as many as it already
     * spans today (a group split across rooms by hand isn't made worse). A {@link #consolidate consolidated} group
     * only keeps the rooms its pinned (in-house) members hold.
     */
    public int allowedGroupRooms( List<ShuffleBooking> members ) {
        if ( members.isEmpty() ) {
            return 0;
        }
        if ( consolidateGroups.contains( groupKey( members.get( 0 ) ) ) ) {
            List<ShuffleBooking> pinned = members.stream().filter( ShuffleBooking::pinned ).collect( Collectors.toList() );
            return Math.max( roomsNeeded( members ), rooms( pinned, current ).size() );
        }
        return Math.max( roomsNeeded( members ), rooms( members, current ).size() );
    }

    /** One, or as many rooms as the group fills on its busiest night. */
    public int roomsNeeded( List<ShuffleBooking> members ) {
        int bedsPerRoom = Math.max( 1, roomType( members.get( 0 ).roomTypeId() ).bedsPerRoom() );
        return Math.max( 1, ( maxConcurrent( members ) + bedsPerRoom - 1 ) / bedsPerRoom );
    }

    /** Rooms of their own type the bookings are in now. */
    public Set<String> currentRooms( List<ShuffleBooking> members ) {
        return rooms( members, current );
    }

    private Set<String> rooms( List<ShuffleBooking> members, Map<String, String> assignment ) {
        Set<String> rooms = new LinkedHashSet<>();
        for ( ShuffleBooking m : members ) {
            ShuffleBed bed = bed( assignment.get( m.key() ) );
            if ( bed != null && bed.roomTypeId() == m.roomTypeId() ) {
                rooms.add( bed.room() );
            }
        }
        return rooms;
    }

    private static int maxConcurrent( List<ShuffleBooking> members ) {
        int max = 0;
        for ( ShuffleBooking m : members ) {
            int concurrent = (int) members.stream().filter( o -> o.overlaps( m.checkin(), m.checkin().plusDays( 1 ) ) ).count();
            max = Math.max( max, concurrent );
        }
        return max;
    }
}
