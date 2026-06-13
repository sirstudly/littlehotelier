
package com.macbackpackers.beans.cloudbeds.responses;

import java.math.BigDecimal;

public class TaxBreakdownItem {

    private String name;
    private BigDecimal amount;

    public String getName() {
        return name;
    }

    public void setName( String name ) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount( BigDecimal amount ) {
        this.amount = amount;
    }
}
