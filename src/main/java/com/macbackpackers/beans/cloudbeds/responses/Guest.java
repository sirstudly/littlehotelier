
package com.macbackpackers.beans.cloudbeds.responses;

import org.apache.commons.lang3.StringUtils;

public class Guest {

    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String cellPhone;
    private String birthday;
    private String gender;
    private String address1;
    private String address2;
    private String city;
    private String countryName;
    private String country;
    private String state;
    private String zip;
    private String documentType;
    private String documentNumber;
    private String documentIssueDate;
    private String documentIssuingCountry;
    private String documentIssuingCountryName;
    private String documentExpirationDate;
    private String deleted;

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName( String firstName ) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName( String lastName ) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail( String email ) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone( String phone ) {
        this.phone = phone;
    }

    public String getCellPhone() {
        return cellPhone;
    }

    public void setCellPhone( String cellPhone ) {
        this.cellPhone = cellPhone;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday( String birthday ) {
        this.birthday = birthday;
    }

    public String getGender() {
        return gender;
    }

    public void setGender( String gender ) {
        this.gender = gender;
    }

    public String getAddress1() {
        return address1;
    }

    public void setAddress1( String address1 ) {
        this.address1 = address1;
    }

    public String getAddress2() {
        return address2;
    }

    public void setAddress2( String address2 ) {
        this.address2 = address2;
    }

    public String getCity() {
        return city;
    }

    public void setCity( String city ) {
        this.city = city;
    }

    public String getCountryName() {
        return countryName;
    }

    public void setCountryName( String countryName ) {
        this.countryName = countryName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry( String country ) {
        this.country = country;
    }

    public String getState() {
        return state;
    }

    public void setState( String state ) {
        this.state = state;
    }

    public String getZip() {
        return zip;
    }

    public void setZip( String zip ) {
        this.zip = zip;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType( String documentType ) {
        this.documentType = documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber( String documentNumber ) {
        this.documentNumber = documentNumber;
    }

    public String getDocumentIssueDate() {
        return documentIssueDate;
    }

    public void setDocumentIssueDate( String documentIssueDate ) {
        this.documentIssueDate = documentIssueDate;
    }

    public String getDocumentIssuingCountry() {
        return documentIssuingCountry;
    }

    public void setDocumentIssuingCountry( String documentIssuingCountry ) {
        this.documentIssuingCountry = documentIssuingCountry;
    }

    public String getDocumentIssuingCountryName() {
        return documentIssuingCountryName;
    }

    public void setDocumentIssuingCountryName( String documentIssuingCountryName ) {
        this.documentIssuingCountryName = documentIssuingCountryName;
    }

    public String getDocumentExpirationDate() {
        return documentExpirationDate;
    }

    public void setDocumentExpirationDate( String documentExpirationDate ) {
        this.documentExpirationDate = documentExpirationDate;
    }

    public String getDeleted() {
        return deleted;
    }

    public void setDeleted( String deleted ) {
        this.deleted = deleted;
    }

    public boolean isDeleted() {
        return "1".equals( StringUtils.trimToEmpty( deleted ) );
    }

    /**
     * Same rules as Tampermonkey CloudbedsDisplayGuestRegistrationComplete:
     * document type and issuing country required; document number optional for UK/Ireland.
     */
    public boolean isIdentityDocumentComplete() {
        String type = StringUtils.trimToEmpty( documentType );
        if ( type.isEmpty() || "na".equalsIgnoreCase( type ) || "-".equals( type ) ) {
            return false;
        }
        String country = StringUtils.trimToEmpty( documentIssuingCountry );
        if ( country.isEmpty() || "na".equalsIgnoreCase( country ) ) {
            return false;
        }
        if ( false == isDocumentNumberOptional() && StringUtils.isBlank( documentNumber ) ) {
            return false;
        }
        return true;
    }

    private boolean isDocumentNumberOptional() {
        String code = StringUtils.trimToEmpty( documentIssuingCountry ).toUpperCase();
        if ( "GB".equals( code ) || "IE".equals( code ) ) {
            return true;
        }
        String name = StringUtils.trimToEmpty( documentIssuingCountryName ).toLowerCase();
        return "ireland".equals( name )
                || "united kingdom".equals( name )
                || "united kingdom of great britain and northern ireland".equals( name );
    }

}
