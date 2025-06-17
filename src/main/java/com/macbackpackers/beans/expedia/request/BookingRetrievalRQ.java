
package com.macbackpackers.beans.expedia.request;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import com.macbackpackers.beans.expedia.Hotel;

@XmlRootElement( name = "BookingRetrievalRQ" )
@XmlAccessorType( XmlAccessType.FIELD )
public class BookingRetrievalRQ {

    @XmlAttribute( name = "xmlns" )
    private String xmlns = "http://www.expediaconnect.com/EQC/BR/2014/01";

    @XmlElement( name = "Authentication" )
    private Authentication authentication;

    @XmlElement( name = "Hotel" )
    private Hotel hotel;

    @XmlElement( name = "ParamSet" )
    private ParamSet paramSet;

    public String getXmlns() {
        return xmlns;
    }

    public void setXmlns( String xmlns ) {
        this.xmlns = xmlns;
    }

    public Authentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication( Authentication authentication ) {
        this.authentication = authentication;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public void setHotel( Hotel hotel ) {
        this.hotel = hotel;
    }

    public ParamSet getParamSet() {
        return paramSet;
    }

    public void setParamSet( ParamSet paramSet ) {
        this.paramSet = paramSet;
    }

}
