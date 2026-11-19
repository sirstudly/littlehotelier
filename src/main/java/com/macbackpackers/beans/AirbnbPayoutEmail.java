package com.macbackpackers.beans;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Processed Airbnb bank-payout Gmail message (idempotency cursor).
 */
@Entity
@Table( name = "wp_lh_airbnb_payout_email" )
public class AirbnbPayoutEmail {

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private long id;

    @Column( name = "gmail_message_id", nullable = false, unique = true )
    private String gmailMessageId;

    @Column( name = "subject" )
    private String subject;

    @Column( name = "processed_date", insertable = false, updatable = false )
    private Timestamp processedDate;

    public long getId() {
        return id;
    }

    public void setId( long id ) {
        this.id = id;
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public void setGmailMessageId( String gmailMessageId ) {
        this.gmailMessageId = gmailMessageId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject( String subject ) {
        this.subject = subject;
    }

    public Timestamp getProcessedDate() {
        return processedDate;
    }
}
