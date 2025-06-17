
package com.macbackpackers.beans;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents a single email to be sent (or has been sent).
 * 
 */
@Entity
@Table( name = "wp_lh_send_email" )
public class SendEmailEntry {

    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "id", nullable = false )
    private int id;

    @Column( name = "email" )
    private String email;

    @Column( name = "first_name" )
    private String firstName;

    @Column( name = "last_name" )
    private String lastName;

    @Column( name = "send_date" )
    private Timestamp sendDate;

    @Column( name = "send_subject" )
    private String sendSubject;

    @Column( name = "send_body" )
    private String sendBody;

    @Column( name = "created_date" )
    private Timestamp createdDate = new Timestamp( System.currentTimeMillis() );

    @Column( name = "last_updated_date" )
    private Timestamp lastUpdatedDate;

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail( String email ) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName( String firstName ) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName( String lastName ) {
        this.lastName = lastName;
    }

    public Timestamp getSendDate() {
        return sendDate;
    }

    public void setSendDate( Timestamp sendDate ) {
        this.sendDate = sendDate;
    }

    public String getSendSubject() {
        return sendSubject;
    }

    public void setSendSubject( String sendSubject ) {
        this.sendSubject = sendSubject;
    }

    public String getSendBody() {
        return sendBody;
    }

    public void setSendBody( String sendBody ) {
        this.sendBody = sendBody;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate( Timestamp createdDate ) {
        this.createdDate = createdDate;
    }

    public Timestamp getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate( Timestamp lastUpdatedDate ) {
        this.lastUpdatedDate = lastUpdatedDate;
    }

    /**
     * Replaces all placeholders within the email subject/body
     * with first name, last name, etc.. This will blow up if
     * any of sendSubject, sendBody, firstName, lastName are null.
     */
    public void replaceAllPlaceholders() {
        setSendSubject(getSendSubject()
                .replaceAll( "%%GUEST_FIRSTNAME%%", getFirstName() )
                .replaceAll( "%%GUEST_LASTNAME%%", getLastName() )
                .replaceAll( "\\'", "'" )); // wordpress update_option() inserts quoted strings
        setSendBody(getSendBody()
                .replaceAll( "%%GUEST_FIRSTNAME%%", getFirstName() )
                .replaceAll( "%%GUEST_LASTNAME%%", getLastName() )
                .replaceAll( "\\'", "'" ));
    }

}
