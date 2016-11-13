
package com.macbackpackers.beans;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table( name = "wp_lh_rpt_guest_comments" )
public class GuestCommentReportEntry {

    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "id", nullable = false )
    private int id;

    @Column( name = "reservation_id" )
    private int reservationId;

    @Column( name = "comments" )
    private String comments;

    @Column( name = "acknowledged_date" )
    private java.util.Date acknowledgedDate;

    @Column( name = "created_date" )
    private Timestamp createdDate;

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public int getReservationId() {
        return reservationId;
    }

    public void setReservationId( int reservationId ) {
        this.reservationId = reservationId;
    }

    public String getComments() {
        return comments;
    }

    public void setComments( String comments ) {
        this.comments = comments;
    }

    public java.util.Date getAcknowledgedDate() {
        return acknowledgedDate;
    }

    public void setAcknowledgedDate( java.util.Date acknowledgedDate ) {
        this.acknowledgedDate = acknowledgedDate;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate( Timestamp createdDate ) {
        this.createdDate = createdDate;
    }

}
