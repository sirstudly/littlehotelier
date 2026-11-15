package com.macbackpackers.dao;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.macbackpackers.beans.analytics.BookingCreatedFact;
import com.macbackpackers.beans.analytics.ChannelStayDayFact;
import com.macbackpackers.beans.analytics.CompetitorBedPriceFact;
import com.macbackpackers.beans.analytics.HwSearchRankFact;
import com.macbackpackers.beans.analytics.PropertyMatch;

/**
 * Writes to the shared {@code mac_analytics} MariaDB warehouse.
 */
@Repository
public class AnalyticsDAO {

    private static final String UPSERT_BOOKING_CREATED =
            "INSERT INTO fact_booking_created ("
                    + "property_code, reservation_id, booked_on, booking_source, status, grand_total, "
                    + "checkin_date, checkout_date, first_seen_at, last_seen_at, updated_at"
                    + ") VALUES (?,?,?,?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "booked_on=VALUES(booked_on), booking_source=VALUES(booking_source), status=VALUES(status), "
                    + "grand_total=VALUES(grand_total), checkin_date=VALUES(checkin_date), "
                    + "checkout_date=VALUES(checkout_date), "
                    + "last_seen_at=VALUES(last_seen_at), updated_at=VALUES(updated_at)";

    private static final String INSERT_CHANNEL_STAY_DAY =
            "INSERT INTO fact_channel_stay_day ("
                    + "property_code, stay_date, booking_source, source_category, room_revenue, rooms_sold, adr, synced_at"
                    + ") VALUES (?,?,?,?,?,?,?,?)";

    private static final String END_DATE_COMPETITOR_BED_PRICE =
            "UPDATE fact_competitor_bed_price SET valid_to = ? "
                    + "WHERE property_code = ? AND source = ? AND stay_date = ? AND valid_to IS NULL";

    private static final String INSERT_COMPETITOR_BED_PRICE =
            "INSERT INTO fact_competitor_bed_price ("
                    + "property_code, competitor_name, room_category, stay_date, lowest_bed_price, "
                    + "currency, source, is_own, scraped_at, valid_from, valid_to"
                    + ") VALUES (?,?,?,?,?,?,?,?,?,?,NULL)";

    private static final String UPSERT_HW_SEARCH_RANK =
            "INSERT INTO fact_hw_search_rank ("
                    + "property_code, search_date, arrival_date, query_label, position, "
                    + "result_count, page_url, scraped_at"
                    + ") VALUES (?,?,?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE "
                    + "position=VALUES(position), result_count=VALUES(result_count), "
                    + "page_url=VALUES(page_url), scraped_at=VALUES(scraped_at)";

    @Autowired
    @Qualifier( "analyticsJdbcTemplate" )
    private JdbcTemplate jdbcTemplate;

    public void upsertBookingCreated( List<BookingCreatedFact> facts ) {
        if ( facts == null || facts.isEmpty() ) {
            return;
        }
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        jdbcTemplate.batchUpdate( UPSERT_BOOKING_CREATED, facts, facts.size(),
                ( PreparedStatement ps, BookingCreatedFact f ) -> {
                    ps.setString( 1, f.getPropertyCode() );
                    ps.setString( 2, f.getReservationId() );
                    ps.setDate( 3, Date.valueOf( f.getBookedOn() ) );
                    ps.setString( 4, f.getBookingSource() );
                    ps.setString( 5, f.getStatus() );
                    ps.setBigDecimal( 6, f.getGrandTotal() );
                    setDateOrNull( ps, 7, f.getCheckinDate() );
                    setDateOrNull( ps, 8, f.getCheckoutDate() );
                    ps.setTimestamp( 9, now );
                    ps.setTimestamp( 10, now );
                    ps.setTimestamp( 11, now );
                } );
    }

    /**
     * Replaces all channel stay-day rows for {@code propertyCode} whose stay_date is in
     * {@code [from, to]} inclusive, then inserts {@code facts}.
     */
    public void replaceChannelStayDays( String propertyCode, LocalDate from, LocalDate to,
            List<ChannelStayDayFact> facts ) {
        jdbcTemplate.update(
                "DELETE FROM fact_channel_stay_day WHERE property_code = ? AND stay_date >= ? AND stay_date <= ?",
                propertyCode, Date.valueOf( from ), Date.valueOf( to ) );
        if ( facts == null || facts.isEmpty() ) {
            return;
        }
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        jdbcTemplate.batchUpdate( INSERT_CHANNEL_STAY_DAY, facts, facts.size(),
                ( PreparedStatement ps, ChannelStayDayFact f ) -> {
                    ps.setString( 1, f.getPropertyCode() );
                    ps.setDate( 2, Date.valueOf( f.getStayDate() ) );
                    ps.setString( 3, f.getBookingSource() );
                    ps.setString( 4, f.getSourceCategory() );
                    ps.setBigDecimal( 5, f.getRoomRevenue() );
                    ps.setInt( 6, f.getRoomsSold() );
                    if ( f.getAdr() == null ) {
                        ps.setNull( 7, Types.DECIMAL );
                    }
                    else {
                        ps.setBigDecimal( 7, f.getAdr() );
                    }
                    ps.setTimestamp( 8, now );
                } );
    }

    /**
     * SCD2 replace for one stay night: end-dates all current rows for
     * {@code propertyCode}/{@code source}/{@code stayDate}, then inserts {@code facts} as the new
     * current snapshot ({@code valid_to} NULL). Empty {@code facts} still end-dates (sold-out /
     * empty SERP clears current prices for that night).
     */
    public void replaceCompetitorBedPricesForStayDate( String propertyCode, String source,
            LocalDate stayDate, List<CompetitorBedPriceFact> facts ) {
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        jdbcTemplate.update( END_DATE_COMPETITOR_BED_PRICE, now, propertyCode, source,
                Date.valueOf( stayDate ) );
        if ( facts == null || facts.isEmpty() ) {
            return;
        }
        jdbcTemplate.batchUpdate( INSERT_COMPETITOR_BED_PRICE, facts, facts.size(),
                ( PreparedStatement ps, CompetitorBedPriceFact f ) -> {
                    ps.setString( 1, f.getPropertyCode() );
                    ps.setString( 2, f.getCompetitorName() );
                    ps.setString( 3, f.getRoomCategory() );
                    ps.setDate( 4, Date.valueOf( f.getStayDate() ) );
                    if ( f.getLowestBedPrice() == null ) {
                        ps.setNull( 5, Types.DECIMAL );
                    }
                    else {
                        ps.setBigDecimal( 5, f.getLowestBedPrice() );
                    }
                    ps.setString( 6, f.getCurrency() );
                    ps.setString( 7, f.getSource() );
                    ps.setInt( 8, f.isOwn() ? 1 : 0 );
                    ps.setTimestamp( 9, now );
                    ps.setTimestamp( 10, now );
                } );
    }

    public void upsertHwSearchRanks( List<HwSearchRankFact> facts ) {
        if ( facts == null || facts.isEmpty() ) {
            return;
        }
        Timestamp now = new Timestamp( System.currentTimeMillis() );
        jdbcTemplate.batchUpdate( UPSERT_HW_SEARCH_RANK, facts, facts.size(),
                ( PreparedStatement ps, HwSearchRankFact f ) -> {
                    ps.setString( 1, f.getPropertyCode() );
                    ps.setDate( 2, Date.valueOf( f.getSearchDate() ) );
                    ps.setDate( 3, Date.valueOf( f.getArrivalDate() ) );
                    ps.setString( 4, f.getQueryLabel() );
                    if ( f.getPosition() == null ) {
                        ps.setNull( 5, Types.INTEGER );
                    }
                    else {
                        ps.setInt( 5, f.getPosition() );
                    }
                    if ( f.getResultCount() == null ) {
                        ps.setNull( 6, Types.INTEGER );
                    }
                    else {
                        ps.setInt( 6, f.getResultCount() );
                    }
                    ps.setString( 7, f.getPageUrl() );
                    ps.setTimestamp( 8, now );
                } );
    }

    public List<PropertyMatch> listEdinburghPropertyMatches() {
        return jdbcTemplate.query(
                "SELECT property_code, display_name, hw_hostel_number, name_aliases, edinburgh_yn "
                        + "FROM dim_property_match WHERE edinburgh_yn = 'Y' ORDER BY property_code",
                ( rs, rowNum ) -> {
                    PropertyMatch m = new PropertyMatch();
                    m.setPropertyCode( rs.getString( "property_code" ) );
                    m.setDisplayName( rs.getString( "display_name" ) );
                    m.setHwHostelNumber( rs.getString( "hw_hostel_number" ) );
                    m.setNameAliases( PropertyMatch.parseAliases( rs.getString( "name_aliases" ) ) );
                    m.setEdinburgh( "Y".equalsIgnoreCase( rs.getString( "edinburgh_yn" ) ) );
                    return m;
                } );
    }

    private static void setDateOrNull( PreparedStatement ps, int index, LocalDate date ) throws SQLException {
        if ( date == null ) {
            ps.setNull( index, Types.DATE );
        }
        else {
            ps.setDate( index, Date.valueOf( date ) );
        }
    }
}
