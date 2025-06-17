
package com.macbackpackers.beans.expedia.request;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "Authentication" )
@XmlAccessorType( XmlAccessType.FIELD )
public class Authentication {

    @XmlAttribute( name = "username" )
    private String username;

    @XmlAttribute( name = "password" )
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername( String username ) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword( String password ) {
        this.password = password;
    }

}
