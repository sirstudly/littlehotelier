
package com.macbackpackers.beans.expedia.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "PaymentCard" )
@XmlAccessorType( XmlAccessType.FIELD )
public class PaymentCard {

    @XmlAttribute( name = "cardCode" )
    private String cardCode;

    @XmlAttribute( name = "cardNumber" )
    private String cardNumber;

    @XmlAttribute( name = "seriesCode" )
    private String seriesCode;

    @XmlAttribute( name = "expireDate" )
    private String expireDate;

    @XmlElement( name = "CardHolder" )
    private CardHolder cardHolder;

    public String getCardCode() {
        return cardCode;
    }

    public void setCardCode( String cardCode ) {
        this.cardCode = cardCode;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber( String cardNumber ) {
        this.cardNumber = cardNumber;
    }

    public String getSeriesCode() {
        return seriesCode;
    }

    public void setSeriesCode( String seriesCode ) {
        this.seriesCode = seriesCode;
    }

    public String getExpireDate() {
        return expireDate;
    }

    public void setExpireDate( String expireDate ) {
        this.expireDate = expireDate;
    }

    public CardHolder getCardHolder() {
        return cardHolder;
    }

    public void setCardHolder( CardHolder cardHolder ) {
        this.cardHolder = cardHolder;
    }
}
