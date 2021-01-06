
package com.macbackpackers.beans.cloudbeds.requests;

import com.macbackpackers.beans.cloudbeds.responses.Guest;

public class CustomerInfo {

    private String autoCustomerId;
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
    private String zip;
    private String documentType;
    private boolean documentTypeIsDirty = true;
    private String documentIssueDate;
    private String documentIssuingCountry;
    private String documentExpirationDate;
    private int isoDates = 1;

    public CustomerInfo( Guest guest ) {
        setAutoCustomerId( guest.getId() );
        setFirstName( guest.getFirstName() );
        setLastName( guest.getLastName() );
        setEmail( guest.getEmail() );
        setPhone( guest.getPhone() );
        setCellPhone( guest.getCellPhone() );
        setBirthday( guest.getBirthday() );
        setGender( guest.getGender() );
        setAddress1( guest.getAddress1() );
        setAddress2( guest.getAddress2() );
        setCity( guest.getCity() );
        setCountryName( guest.getCountryName() );
        setCountry( guest.getCountry() );
        setZip( guest.getZip() );
        setDocumentType( guest.getDocumentType() );
        setDocumentIssueDate( guest.getDocumentIssueDate() );
        setDocumentIssuingCountry( guest.getDocumentIssuingCountry() );
        setDocumentExpirationDate( guest.getDocumentExpirationDate() );
        // document number is left out so we don't overwrite it
    }

    public String getAutoCustomerId() {
        return autoCustomerId;
    }

    public void setAutoCustomerId( String autoCustomerId ) {
        this.autoCustomerId = autoCustomerId;
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

    public boolean isDocumentTypeIsDirty() {
        return documentTypeIsDirty;
    }

    public void setDocumentTypeIsDirty( boolean documentTypeIsDirty ) {
        this.documentTypeIsDirty = documentTypeIsDirty;
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

    public String getDocumentExpirationDate() {
        return documentExpirationDate;
    }

    public void setDocumentExpirationDate( String documentExpirationDate ) {
        this.documentExpirationDate = documentExpirationDate;
    }

    public int getIsoDates() {
        return isoDates;
    }

    public void setIsoDates( int isoDates ) {
        this.isoDates = isoDates;
    }
}
