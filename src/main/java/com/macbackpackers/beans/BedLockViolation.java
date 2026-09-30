package com.macbackpackers.beans;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A locked reservation moved off its bed on the Cloudbeds calendar. Open while {@code resolved_date}
 * is null; the destination is updated if the reservation keeps moving.
 */
@Entity
@Table( name = "wp_lh_bed_lock_violation" )
public class BedLockViolation {

    public static final String RESOLUTION_MOVED_BACK = "moved_back";

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private long id;

    @Column( name = "bed_lock_id", nullable = false )
    private long bedLockId;

    @Column( name = "reservation_id", nullable = false )
    private long reservationId;

    @Column( name = "guest_name" )
    private String guestName;

    @Column( name = "from_room_id" )
    private String fromRoomId;

    @Column( name = "from_room" )
    private String fromRoom;

    @Column( name = "from_bed_name" )
    private String fromBedName;

    /** Null when the reservation was unassigned from the bed. */
    @Column( name = "to_room_id" )
    private String toRoomId;

    @Column( name = "to_room" )
    private String toRoom;

    @Column( name = "to_bed_name" )
    private String toBedName;

    @Column( name = "detected_date", nullable = false )
    private Timestamp detectedDate;

    @Column( name = "resolved_date" )
    private Timestamp resolvedDate;

    @Column( name = "resolution" )
    private String resolution;

    public long getId() {
        return id;
    }

    public void setId( long id ) {
        this.id = id;
    }

    public long getBedLockId() {
        return bedLockId;
    }

    public void setBedLockId( long bedLockId ) {
        this.bedLockId = bedLockId;
    }

    public long getReservationId() {
        return reservationId;
    }

    public void setReservationId( long reservationId ) {
        this.reservationId = reservationId;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName( String guestName ) {
        this.guestName = guestName;
    }

    public String getFromRoomId() {
        return fromRoomId;
    }

    public void setFromRoomId( String fromRoomId ) {
        this.fromRoomId = fromRoomId;
    }

    public String getFromRoom() {
        return fromRoom;
    }

    public void setFromRoom( String fromRoom ) {
        this.fromRoom = fromRoom;
    }

    public String getFromBedName() {
        return fromBedName;
    }

    public void setFromBedName( String fromBedName ) {
        this.fromBedName = fromBedName;
    }

    public String getToRoomId() {
        return toRoomId;
    }

    public void setToRoomId( String toRoomId ) {
        this.toRoomId = toRoomId;
    }

    public String getToRoom() {
        return toRoom;
    }

    public void setToRoom( String toRoom ) {
        this.toRoom = toRoom;
    }

    public String getToBedName() {
        return toBedName;
    }

    public void setToBedName( String toBedName ) {
        this.toBedName = toBedName;
    }

    public Timestamp getDetectedDate() {
        return detectedDate;
    }

    public void setDetectedDate( Timestamp detectedDate ) {
        this.detectedDate = detectedDate;
    }

    public Timestamp getResolvedDate() {
        return resolvedDate;
    }

    public void setResolvedDate( Timestamp resolvedDate ) {
        this.resolvedDate = resolvedDate;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution( String resolution ) {
        this.resolution = resolution;
    }
}
