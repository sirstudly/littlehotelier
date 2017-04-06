
package com.macbackpackers.beans.expedia.request;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

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
