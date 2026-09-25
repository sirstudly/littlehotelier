package com.macbackpackers.ronbot.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Channel Production (Data Insights stock report 191) totals by source for one stay-date month.
 */
public class ChannelProductionDto {

    private String property;
    /** YYYY-MM */
    private String month;
    private List<Source> sources = new ArrayList<>();
    private Totals totals = new Totals();

    public String getProperty() {
        return property;
    }

    public void setProperty( String property ) {
        this.property = property;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth( String month ) {
        this.month = month;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void setSources( List<Source> sources ) {
        this.sources = sources;
    }

    public Totals getTotals() {
        return totals;
    }

    public void setTotals( Totals totals ) {
        this.totals = totals;
    }

    public static class Source {
        private String source;
        private BigDecimal revenue;
        /** Share of total revenue, 0–100. */
        private BigDecimal revenuePct;
        private int roomsSold;
        /** Share of total rooms sold, 0–100. */
        private BigDecimal roomsSoldPct;
        private BigDecimal adr;
        /** Revenue / the source's commission divisor; null when no commission applies. */
        private BigDecimal commission;

        public BigDecimal getCommission() {
            return commission;
        }

        public void setCommission( BigDecimal commission ) {
            this.commission = commission;
        }

        public String getSource() {
            return source;
        }

        public void setSource( String source ) {
            this.source = source;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public void setRevenue( BigDecimal revenue ) {
            this.revenue = revenue;
        }

        public BigDecimal getRevenuePct() {
            return revenuePct;
        }

        public void setRevenuePct( BigDecimal revenuePct ) {
            this.revenuePct = revenuePct;
        }

        public int getRoomsSold() {
            return roomsSold;
        }

        public void setRoomsSold( int roomsSold ) {
            this.roomsSold = roomsSold;
        }

        public BigDecimal getRoomsSoldPct() {
            return roomsSoldPct;
        }

        public void setRoomsSoldPct( BigDecimal roomsSoldPct ) {
            this.roomsSoldPct = roomsSoldPct;
        }

        public BigDecimal getAdr() {
            return adr;
        }

        public void setAdr( BigDecimal adr ) {
            this.adr = adr;
        }
    }

    public static class Totals {
        private BigDecimal revenue = BigDecimal.ZERO;
        private int roomsSold;
        /** Sum of per-source commission (wp_lh_booking_source_lookup divisors). */
        private BigDecimal commission = BigDecimal.ZERO;
        /** Revenue less commission. */
        private BigDecimal netRevenue = BigDecimal.ZERO;
        /** Net revenue / rooms sold; null when nothing was sold. */
        private BigDecimal avgPricePerBed;
        /** Beds occupied for the month, 0–100; null if unavailable. */
        private BigDecimal occupancyPct;

        public BigDecimal getOccupancyPct() {
            return occupancyPct;
        }

        public void setOccupancyPct( BigDecimal occupancyPct ) {
            this.occupancyPct = occupancyPct;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }

        public void setRevenue( BigDecimal revenue ) {
            this.revenue = revenue;
        }

        public int getRoomsSold() {
            return roomsSold;
        }

        public void setRoomsSold( int roomsSold ) {
            this.roomsSold = roomsSold;
        }

        public BigDecimal getCommission() {
            return commission;
        }

        public void setCommission( BigDecimal commission ) {
            this.commission = commission;
        }

        public BigDecimal getNetRevenue() {
            return netRevenue;
        }

        public void setNetRevenue( BigDecimal netRevenue ) {
            this.netRevenue = netRevenue;
        }

        public BigDecimal getAvgPricePerBed() {
            return avgPricePerBed;
        }

        public void setAvgPricePerBed( BigDecimal avgPricePerBed ) {
            this.avgPricePerBed = avgPricePerBed;
        }
    }
}
