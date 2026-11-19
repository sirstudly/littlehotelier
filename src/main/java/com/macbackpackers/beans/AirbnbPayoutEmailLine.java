package com.macbackpackers.beans;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One confirmation-code remittance line from an Airbnb bank-payout email.
 * Amount is signed (resolution clawbacks are negative). CSV import for the same
 * confirmation code marks {@code superseded_yn='Y'}.
 */
@Entity
@Table( name = "wp_lh_airbnb_payout_email_line" )
public class AirbnbPayoutEmailLine {

    public static final String TYPE_RESERVATION = "reservation";
    public static final String TYPE_RESOLUTION = "resolution";

    @Id
    @GeneratedValue( strategy = GenerationType.IDENTITY )
    @Column( name = "id", nullable = false )
    private long id;

    @Column( name = "payout_email_id", nullable = false )
    private long payoutEmailId;

    @Column( name = "confirmation_code", nullable = false )
    private String confirmationCode;

    @Column( name = "amount", nullable = false )
    private BigDecimal amount;

    @Column( name = "guest_name" )
    private String guestName;

    @Column( name = "line_type", nullable = false )
    private String lineType = TYPE_RESERVATION;

    @Column( name = "superseded_yn", nullable = false )
    private String supersededYn = "N";

    public long getId() {
        return id;
    }

    public void setId( long id ) {
        this.id = id;
    }

    public long getPayoutEmailId() {
        return payoutEmailId;
    }

    public void setPayoutEmailId( long payoutEmailId ) {
        this.payoutEmailId = payoutEmailId;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public void setConfirmationCode( String confirmationCode ) {
        this.confirmationCode = confirmationCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount( BigDecimal amount ) {
        this.amount = amount;
    }

    public String getGuestName() {
        return guestName;
    }

    public void setGuestName( String guestName ) {
        this.guestName = guestName;
    }

    public String getLineType() {
        return lineType;
    }

    public void setLineType( String lineType ) {
        this.lineType = lineType;
    }

    public String getSupersededYn() {
        return supersededYn;
    }

    public void setSupersededYn( String supersededYn ) {
        this.supersededYn = supersededYn;
    }

    public boolean isSuperseded() {
        return "Y".equalsIgnoreCase( supersededYn );
    }
}
