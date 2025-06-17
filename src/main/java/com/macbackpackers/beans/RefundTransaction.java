
package com.macbackpackers.beans;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

@Entity
@Inheritance( strategy = InheritanceType.JOINED )
@Table( name = "wp_tx_refund" )
public class RefundTransaction {

    @Id
    @GeneratedValue( strategy = GenerationType.AUTO )
    @Column( name = "id", nullable = false )
    private int id;

    @Column( name = "reservation_id", nullable = false )
    private String reservationId;

    @Column( name = "email", nullable = false )
    private String email;

    @Column( name = "first_name" )
    private String firstName;

    @Column( name = "last_name" )
    private String lastName;

    @Column( name = "amount", nullable = false )
    private BigDecimal amount;

    @Column( name = "description" )
    private String description;

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId( String reservationId ) {
        this.reservationId = reservationId;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public long getAmountInBaseUnits() {
        return amount.multiply( new BigDecimal( "100" ) ).longValue();
    }

    public void setAmount( BigDecimal amount ) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription( String description ) {
        this.description = description;
    }
}
