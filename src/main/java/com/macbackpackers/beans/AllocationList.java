
package com.macbackpackers.beans;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Convenience class for updating a batch of allocation elements at a time.
 *
 */
public class AllocationList extends ArrayList<Allocation> {

    private static final long serialVersionUID = 7798104646558665229L;

    public AllocationList() {
        super();
    }

    public AllocationList( Collection<? extends Allocation> elements ) {
        super( elements );
    }

    public void setViewed( boolean viewed ) {
        for ( Allocation a : this ) {
            a.setViewed( viewed );
        }
    }

    public void setStatus( String status ) {
        for ( Allocation a : this ) {
            a.setStatus( status );
        }
    }

    public void setBookingReference( String bookingRef ) {
        for ( Allocation a : this ) {
            a.setBookingReference( bookingRef );
        }
    }

    public void setBookingSource( String bookingSrc ) {
        for ( Allocation a : this ) {
            a.setBookingSource( bookingSrc );
        }
    }

    public void setBookedDate( String bookedDate ) {
        for ( Allocation a : this ) {
            a.setBookedDate( bookedDate );
        }
    }

    public void setEta( String eta ) {
        for ( Allocation a : this ) {
            a.setEta( eta );
        }
    }

    public void setCreatedDate( Timestamp createdDate ) {
        for ( Allocation a : this ) {
            a.setCreatedDate( createdDate );
        }
    }

}
