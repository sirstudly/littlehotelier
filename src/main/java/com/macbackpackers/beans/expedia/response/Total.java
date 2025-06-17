
package com.macbackpackers.beans.expedia.response;

import java.math.BigDecimal;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "Total" )
@XmlAccessorType( XmlAccessType.FIELD )
public class Total {

    @XmlAttribute( name = "amountAfterTaxes" )
    private BigDecimal amountAfterTaxes;

    @XmlAttribute( name = "currency" )
    private String currency;

    public BigDecimal getAmountAfterTaxes() {
        return amountAfterTaxes;
    }

    public void setAmountAfterTaxes( BigDecimal amountAfterTaxes ) {
        this.amountAfterTaxes = amountAfterTaxes;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency( String currency ) {
        this.currency = currency;
    }
}
