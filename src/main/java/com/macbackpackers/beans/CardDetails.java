
package com.macbackpackers.beans;

/**
 * A guest's card details. Please don't save this anywhere...
 *
 */
public class CardDetails {

    private String cardType;
    private String name;
    private String cardNumber;
    private String expiry;
    private String cvv;
    
    public CardDetails() {
        // default constructor
    }
    
    public CardDetails( String cardType, String name, String cardNumber, String expiry, String cvv ) {
        this.cardType = cardType;
        this.name = name;
        this.cardNumber = cardNumber;
        this.expiry = expiry;
        this.cvv = cvv;
    }

    public String getCardType() {
        return cardType;
    }

    /**
     * Returns the card type string (used for Cloudbeds) based on the card number BIN.
     * See also {@link https://www.bincodes.com/bin-list/}
     * 
     * @return one of visa, master, maestro
     * @throws IllegalArgumentException if card not supported
     */
    public String getCloudbedsCardTypeFromBinRange() throws IllegalArgumentException {
        if ( cardNumber.matches( "^4\\d+" ) ) {
            return "visa";
        }
        if ( cardNumber.matches( "^((5[0,6-8])|6)\\d+" ) ) {
            return "maestro";
        }
        if ( cardNumber.matches( "^((2[2-7])|(5[1-5]))\\d+" ) ) {
            return "master";
        }
        throw new IllegalArgumentException( "Unsupported card found. BIN: " + cardNumber.substring( 0, 6 ) );
    }

    public void setCardType( String cardType ) {
        this.cardType = cardType;
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
