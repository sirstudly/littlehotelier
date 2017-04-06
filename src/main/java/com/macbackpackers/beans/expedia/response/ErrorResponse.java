
package com.macbackpackers.beans.expedia.response;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

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
