
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

    // whether this is against a "virtual" cc
    private boolean virtual;

    public Payment() {
        // default constructor
    }

    public Payment( CardDetails cardDetails ) {
        this.cardDetails = cardDetails;
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

    public boolean isVirtual() {
        return virtual;
    }

    public void setVirtual( boolean virtual ) {
        this.virtual = virtual;
    }

}
