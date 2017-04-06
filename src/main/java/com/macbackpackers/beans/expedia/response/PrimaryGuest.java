
package com.macbackpackers.beans.expedia.response;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "PrimaryGuest" )
@XmlAccessorType( XmlAccessType.FIELD )
public class PrimaryGuest {

    @XmlElement( name = "Name" )
    private Name name;

    @XmlElement( name = "Email" )
    private String email;

    public Name getName() {
        return name;
    }

    public void setName( Name name ) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail( String email ) {
        this.email = email;
    }
}
