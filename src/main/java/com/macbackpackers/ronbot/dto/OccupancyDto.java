package com.macbackpackers.ronbot.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Beds-occupied rate for a property over an inclusive stay-date range, with a monthly breakdown.
 */
public class OccupancyDto {

    private String property;
    private String from;
    private String to;
    /** Night-weighted across the range, 0–100; null when no data. */
    private BigDecimal occupancyPct;
    private int bedsBooked;
    private BigDecimal revenue;
    private List<Month> months = new ArrayList<>();

    public String getProperty() {
        return property;
    }

    public void setProperty( String property ) {
        this.property = property;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom( String from ) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo( String to ) {
        this.to = to;
    }

    public BigDecimal getOccupancyPct() {
        return occupancyPct;
    }

    public void setOccupancyPct( BigDecimal occupancyPct ) {
        this.occupancyPct = occupancyPct;
    }

    public int getBedsBooked() {
        return bedsBooked;
    }

    public void setBedsBooked( int bedsBooked ) {
        this.bedsBooked = bedsBooked;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue( BigDecimal revenue ) {
        this.revenue = revenue;
    }

    public List<Month> getMonths() {
        return months;
    }

    public void setMonths( List<Month> months ) {
        this.months = months;
    }

    public static class Month {
        /** YYYY-MM */
        private String month;
        private BigDecimal occupancyPct;
        private int bedsBooked;
        private BigDecimal revenue;

        public String getMonth() {
            return month;
        }

        public void setMonth( String month ) {
            this.month = month;
        }

        public BigDecimal getOccupancyPct() {
            return occupancyPct;
        }

        public void setOccupancyPct( BigDecimal occupancyPct ) {
            this.occupancyPct = occupancyPct;
        }

        public int getBedsBooked() {
            return bedsBooked;
        }

        public void setBedsBooked( int bedsBooked ) {
            this.bedsBooked = bedsBooked;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public void setRevenue( BigDecimal revenue ) {
            this.revenue = revenue;
        }
    }
}
