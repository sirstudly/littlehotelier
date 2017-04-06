
package com.macbackpackers.beans.expedia.response;

import java.math.BigDecimal;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

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
