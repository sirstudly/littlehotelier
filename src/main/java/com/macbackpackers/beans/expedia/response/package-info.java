@XmlSchema( 
    namespace = "http://www.expediaconnect.com/EQC/BR/2014/01", 
    elementFormDefault = XmlNsForm.QUALIFIED )

package com.macbackpackers.beans.expedia.response;

import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;

/**
 * Specify default namespace for Expedia response XML so we
 * don't get serialization error when xmlns is specified.
 */