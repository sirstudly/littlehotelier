
package com.macbackpackers.beans.expedia.response;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "PerDayRates" )
@XmlAccessorType( XmlAccessType.FIELD )
public class PerDayRates {

    @XmlAttribute( name = "currency" )
    private String currency;

    @XmlElement( name = "PerDayRate" )
    private List<PerDayRate> rates;

    public String getCurrency() {
        return currency;
    }

    public void setCurrency( String currency ) {
        this.currency = currency;
    }

    public List<PerDayRate> getRates() {
        return rates;
    }

    public void setRates( List<PerDayRate> rates ) {
        this.rates = rates;
    }

}
