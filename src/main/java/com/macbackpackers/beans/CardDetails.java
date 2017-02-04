
package com.macbackpackers.beans;

/**
 * A guest's card details. Please don't save this anywhere...
 *
 */
public class CardDetails {

    private String name;
    private String cardNumber;
    private String expiry;
    private String cvv;
    
    public CardDetails() {
        // default constructor
    }
    
    public CardDetails(String name, String cardNumber, String expiry, String cvv) {
        this.name = name;
        this.cardNumber = cardNumber;
        this.expiry = expiry;
        this.cvv = cvv;
    }

    public String getName() {
        return name;
    }

    public void setName( String name ) {
        this.name = name;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber( String cardNumber ) {
        this.cardNumber = cardNumber;
    }

    public String getExpiry() {
        return expiry;
    }

    public void setExpiry( String expiry ) {
        this.expiry = expiry;
    }

    public String getCvv() {
        return cvv;
    }

    public void setCvv( String cvv ) {
        this.cvv = cvv;
    }

}
