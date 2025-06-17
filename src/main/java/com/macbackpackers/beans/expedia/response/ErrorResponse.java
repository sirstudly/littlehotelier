
package com.macbackpackers.beans.expedia.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlValue;

@XmlRootElement( name = "Error" )
@XmlAccessorType( XmlAccessType.FIELD )
public class ErrorResponse {

    @XmlAttribute( name = "code" )
    private String code;

    @XmlValue
    private String text;

    public String getCode() {
        return code;
    }

    public void setCode( String code ) {
        this.code = code;
    }

    public String getText() {
        return text;
    }

    public void setText( String text ) {
        this.text = text;
    }
}
