package com.macbackpackers.beans.analytics;

import java.time.LocalDate;

/**
 * Hostelworld consumer search position for one of our properties on a given scrape day.
 */
public class HwSearchRankFact {

    private String propertyCode;
    private LocalDate searchDate;
    private LocalDate arrivalDate;
    private String queryLabel;
    private Integer position;
    private Integer resultCount;
    private String pageUrl;

    public String getPropertyCode() {
        return propertyCode;
    }

    public void setPropertyCode( String propertyCode ) {
        this.propertyCode = propertyCode;
    }

    public LocalDate getSearchDate() {
        return searchDate;
    }

    public void setSearchDate( LocalDate searchDate ) {
        this.searchDate = searchDate;
    }

    public LocalDate getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate( LocalDate arrivalDate ) {
        this.arrivalDate = arrivalDate;
    }

    public String getQueryLabel() {
        return queryLabel;
    }

    public void setQueryLabel( String queryLabel ) {
        this.queryLabel = queryLabel;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition( Integer position ) {
        this.position = position;
    }

    public Integer getResultCount() {
        return resultCount;
    }

    public void setResultCount( Integer resultCount ) {
        this.resultCount = resultCount;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl( String pageUrl ) {
        this.pageUrl = pageUrl;
    }
}
