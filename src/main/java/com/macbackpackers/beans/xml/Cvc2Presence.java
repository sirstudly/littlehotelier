
package com.macbackpackers.beans.xml;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;

/**
 * This value provides an indication of that the person participating in a transaction had physical
 * possession of the card at some point in time.
 *
 */
@XmlType
@XmlEnum(Integer.class)
public enum Cvc2Presence {

    @XmlEnumValue("0") NOT_SUBMITTED_BY_MERCHANT (0), 
    @XmlEnumValue("1") INCLUDED_BY_MERCHANT (1), 
    @XmlEnumValue("2") CVC_IS_ILLEGIBLE (2), 
    @XmlEnumValue("9") CVC_NOT_PRESENT (9);

    private int code;

    private Cvc2Presence( int code ) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

}
