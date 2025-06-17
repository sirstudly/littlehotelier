
package com.macbackpackers.beans.expedia.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "CardHolder" )
@XmlAccessorType( XmlAccessType.FIELD )
public class CardHolder {

    @XmlAttribute( name = "name" )
    private String name;

    @XmlAttribute( name = "address" )
    private String address;

    @XmlAttribute( name = "city" )
    private String city;

    @XmlAttribute( name = "stateProv" )
    private String stateProv;

    @XmlAttribute( name = "country" )
    private String country;

    @XmlAttribute( name = "postalCode" )
    private String postalCode;

    public String getName() {
        return name;
    }

    public void setName( String name ) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress( String address ) {
        this.address = address;
    }

    public String getCity() {
        return city;
    }

    public void setCity( String city ) {
        this.city = city;
    }

    public String getStateProv() {
        return stateProv;
    }

    public void setStateProv( String stateProv ) {
        this.stateProv = stateProv;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry( String country ) {
        this.country = country;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode( String postalCode ) {
        this.postalCode = postalCode;
    }

}
