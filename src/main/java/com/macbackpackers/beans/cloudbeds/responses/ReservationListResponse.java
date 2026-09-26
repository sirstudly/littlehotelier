
package com.macbackpackers.beans.cloudbeds.responses;

import java.util.List;

/**
 * A single page of results from the {@code mapi/reservation/list} endpoint.
 */
public class ReservationListResponse extends CloudbedsJsonResponse {

    private List<ReservationListItem> data;
    private int total;
    private int page;
    private int count;

    public List<ReservationListItem> getData() {
        return data;
    }

    public void setData( List<ReservationListItem> data ) {
        this.data = data;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal( int total ) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage( int page ) {
        this.page = page;
    }

    public int getCount() {
        return count;
    }

    public void setCount( int count ) {
        this.count = count;
    }
}
