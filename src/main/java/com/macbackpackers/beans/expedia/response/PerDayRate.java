
package com.macbackpackers.beans.expedia.response;

import java.math.BigDecimal;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "PerDayRate" )
@XmlAccessorType( XmlAccessType.FIELD )
public class PerDayRate {

    @XmlAttribute( name = "stayDate" )
    private String stayDate;

    @XmlAttribute( name = "baseRate" )
    private BigDecimal baseRate;

    public String getStayDate() {
        return stayDate;
    }

    public void setStayDate( String stayDate ) {
        this.stayDate = stayDate;
    }

    public BigDecimal getBaseRate() {
        return baseRate;
    }

    public void setBaseRate( BigDecimal baseRate ) {
        this.baseRate = baseRate;
    }

}
