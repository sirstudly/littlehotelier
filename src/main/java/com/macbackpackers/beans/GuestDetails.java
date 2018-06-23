
package com.macbackpackers.beans;

public class GuestDetails {

    private String firstName;
    private String lastName;
    private CardDetails cardDetails;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName( String firstName ) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void setLastName( String lastName ) {
        this.lastName = lastName;
    }

    public CardDetails getCardDetails() {
        return cardDetails;
    }

    public void setCardDetails( CardDetails cardDetails ) {
        this.cardDetails = cardDetails;
    }

}
