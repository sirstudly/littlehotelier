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

    private static void setDateOrNull( PreparedStatement ps, int index, LocalDate date ) throws SQLException {
        if ( date == null ) {
            ps.setNull( index, Types.DATE );
        }
        else {
            ps.setDate( index, Date.valueOf( date ) );
        }
    }
}
