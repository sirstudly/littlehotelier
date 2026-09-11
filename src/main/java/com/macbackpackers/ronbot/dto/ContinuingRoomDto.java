package com.macbackpackers.ronbot.dto;

/**
 * Per-bed stay-continuation result for a source reservation.
 */
public class ContinuingRoomDto {

    private String roomId;
    private String roomNumber;
    private String roomTypeName;
    private boolean continues;
    /** same_reservation | linked_reservation | vacant | different_guest | inactive_follow_on | already_checked_out | ends_on_or_before_asOf */
    private String reason;
    /** Follow-on reservation id when reason is linked_reservation. */
    private String followingReservationId;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId( String roomId ) {
        this.roomId = roomId;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber( String roomNumber ) {
        this.roomNumber = roomNumber;
    }

    public String getRoomTypeName() {
        return roomTypeName;
    }

    public void setRoomTypeName( String roomTypeName ) {
        this.roomTypeName = roomTypeName;
    }

    public boolean isContinues() {
        return continues;
    }

    public void setContinues( boolean continues ) {
        this.continues = continues;
    }

    public String getReason() {
        return reason;
    }

    public void setReason( String reason ) {
        this.reason = reason;
    }

    public String getFollowingReservationId() {
        return followingReservationId;
    }

    public void setFollowingReservationId( String followingReservationId ) {
        this.followingReservationId = followingReservationId;
    }
}
