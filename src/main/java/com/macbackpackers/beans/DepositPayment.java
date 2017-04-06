
package com.macbackpackers.beans;

import java.math.BigDecimal;

/**
 * Holds the requisite deposit payment and the card details to charge it.
 *
 */
public class DepositPayment {

    // amount to charge as a deposit
    private BigDecimal depositAmount;

    // full card details
    private CardDetails cardDetails;
    
    public DepositPayment() {
        // default constructor
    }

    public DepositPayment( BigDecimal depositAmount, CardDetails cardDetails ) {
        this.depositAmount = depositAmount;
        this.cardDetails = cardDetails;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount( BigDecimal depositAmount ) {
        this.depositAmount = depositAmount;
    }

    public CardDetails getCardDetails() {
        return cardDetails;
    }

    public void setCardDetails( CardDetails cardDetails ) {
        this.cardDetails = cardDetails;
    }

}
