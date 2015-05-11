package com.macbackpackers.dao.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.dbutils.RowProcessor;

import com.macbackpackers.beans.Allocation;

public class AllocationRowHandler implements RowProcessor {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T toBean(ResultSet rs, Class<T> type) throws SQLException {
        return (T) toBean(rs); // we don't support <T> being anything other than Allocation
    }

    public Allocation toBean(ResultSet rs) throws SQLException {
        Allocation alloc = new Allocation();
        alloc.setId(rs.getInt("id"));
        alloc.setJobId(rs.getInt("job_id"));
        alloc.setRoom(rs.getString("room"));
        alloc.setBedName(rs.getString("bed_name"));
        alloc.setCheckinDate(rs.getDate("checkin_date"));
        alloc.setCheckoutDate(rs.getDate("checkout_date"));
        alloc.setPaymentTotal(rs.getBigDecimal("payment_total"));
        alloc.setPaymentOutstanding(rs.getBigDecimal("payment_outstanding"));
        alloc.setRatePlanName(rs.getString("rate_plan_name"));
        alloc.setPaymentStatus(rs.getString("payment_status"));
        alloc.setNumberGuests(rs.getInt("num_guests"));
        alloc.setDataHref(rs.getString("data_href"));
        alloc.setCreatedDate(rs.getDate("created_date"));
        return alloc;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> toBeanList(ResultSet rs, Class<T> type) throws SQLException {
        List<Allocation> result = new ArrayList<Allocation>();
        while (rs.next()) {
            result.add(toBean(rs));
        }
        return (List<T>) result; // we don't support <T> being anything other than Allocation
    }

    @Override
    public Object[] toArray(ResultSet arg0) throws SQLException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> toMap(ResultSet arg0) throws SQLException {
        throw new UnsupportedOperationException();
    }

}