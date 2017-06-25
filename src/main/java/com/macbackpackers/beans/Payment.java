
package com.macbackpackers.beans;

import java.math.BigDecimal;

/**
 * Holds the requisite payment and the card details to charge it.
 *
 */
public class Payment {

    // amount to charge
    private BigDecimal amount;

    // full card details
    private CardDetails cardDetails;
    
    public Payment() {
        // default constructor
    }

    public Payment( BigDecimal amount, CardDetails cardDetails ) {
        this.amount = amount;
        this.cardDetails = cardDetails;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount( BigDecimal amount ) {
        this.amount = amount;
    }

    public CardDetails getCardDetails() {
        return cardDetails;
    }

    public void setCardDetails( CardDetails cardDetails ) {
        this.cardDetails = cardDetails;
    }

}
