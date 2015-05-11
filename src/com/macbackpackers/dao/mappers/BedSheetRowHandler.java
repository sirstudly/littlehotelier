package com.macbackpackers.dao.mappers;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.dbutils.RowProcessor;

import com.macbackpackers.beans.BedChange;
import com.macbackpackers.beans.BedSheetEntry;

public class BedSheetRowHandler implements RowProcessor {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T toBean(ResultSet rs, Class<T> type) throws SQLException {
        return (T) toBean(rs); // we don't support <T> being anything other than BedSheetEntry
    }

    public BedSheetEntry toBean(ResultSet rs) throws SQLException {
        BedSheetEntry bs = new BedSheetEntry();
        bs.setId(rs.getInt("id"));
        bs.setJobId(rs.getInt("job_id"));
        bs.setRoom(rs.getString("room"));
        bs.setBedName(rs.getString("bed_name"));
        bs.setCheckoutDate(rs.getDate("checkout_date"));
        bs.setStatus(BedChange.fromValue(rs.getString("change_status")));
        return bs;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> toBeanList(ResultSet rs, Class<T> type) throws SQLException {
        List<BedSheetEntry> result = new ArrayList<BedSheetEntry>();
        while (rs.next()) {
            result.add(toBean(rs));
        }
        return (List<T>) result; // we don't support <T> being anything other than BedSheetEntry
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