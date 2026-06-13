
package com.macbackpackers.beans.cloudbeds.responses;

import java.math.BigDecimal;
import java.util.List;

public class BalanceDetails {

    private BigDecimal grandTotal;
    private BigDecimal subtotal;
    private BigDecimal balanceDue;
    private List<TaxBreakdownItem> taxBreakdown;

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal( BigDecimal grandTotal ) {
        this.grandTotal = grandTotal;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal( BigDecimal subtotal ) {
        this.subtotal = subtotal;
    }

    public BigDecimal getBalanceDue() {
        return balanceDue;
    }

    public void setBalanceDue( BigDecimal balanceDue ) {
        this.balanceDue = balanceDue;
    }

    public List<TaxBreakdownItem> getTaxBreakdown() {
        return taxBreakdown;
    }

    public void setTaxBreakdown( List<TaxBreakdownItem> taxBreakdown ) {
        this.taxBreakdown = taxBreakdown;
    }
}
