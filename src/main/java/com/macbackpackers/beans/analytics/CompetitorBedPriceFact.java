package com.macbackpackers.beans.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One Google Hotels (or similar) listing price for a stay date and room category.
 */
public class CompetitorBedPriceFact {

    private String propertyCode = "edn";
    private String competitorName;
    private String roomCategory;
    private LocalDate stayDate;
    private BigDecimal lowestBedPrice;
    private String currency = "GBP";
    private String source = "ghs";
    private boolean own;

    public String getPropertyCode() {
        return propertyCode;
    }

    public void setPropertyCode( String propertyCode ) {
        this.propertyCode = propertyCode;
    }

    public String getCompetitorName() {
        return competitorName;
    }

    public void setCompetitorName( String competitorName ) {
        this.competitorName = competitorName;
    }

    public String getRoomCategory() {
        return roomCategory;
    }

    public void setRoomCategory( String roomCategory ) {
        this.roomCategory = roomCategory;
    }

    public LocalDate getStayDate() {
        return stayDate;
    }

    public void setStayDate( LocalDate stayDate ) {
        this.stayDate = stayDate;
    }

    public BigDecimal getLowestBedPrice() {
        return lowestBedPrice;
    }

    public void setLowestBedPrice( BigDecimal lowestBedPrice ) {
        this.lowestBedPrice = lowestBedPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency( String currency ) {
        this.currency = currency;
    }

    public String getSource() {
        return source;
    }

    public void setSource( String source ) {
        this.source = source;
    }

    public boolean isOwn() {
        return own;
    }

    public void setOwn( boolean own ) {
        this.own = own;
    }
}
