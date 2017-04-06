
package com.macbackpackers.beans.expedia.response;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "Name" )
@XmlAccessorType( XmlAccessType.FIELD )
public class Name {

    @XmlAttribute( name = "givenName" )
    private String givenName;

    @XmlAttribute( name = "surname" )
    private String surname;

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName( String givenName ) {
        this.givenName = givenName;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname( String surname ) {
        this.surname = surname;
    }

}
