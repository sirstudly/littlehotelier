
package com.macbackpackers.beans;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Used as a lookup key for the corresponding {@link RoomBed} by room and bedName.
 *
 */
public class RoomBedLookup extends RoomBed {

    public RoomBedLookup( String room, String bedName ) {
        setRoom( StringUtils.trim( room ) );
        setBedName( StringUtils.trim( bedName ) );
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append( getRoom() )
                .append( getBedName() )
                .toHashCode();
    }

    @Override
    public boolean equals( Object obj ) {
        if ( obj instanceof RoomBed ) {
            RoomBed other = RoomBed.class.cast( obj );
            return new EqualsBuilder()
                    .append( getRoom(), other.getRoom() )
                    .append( getBedName(), other.getBedName() )
                    .build();
        }
        return false;
    }

    @Override
    public String toString() {
        return getRoom() +
                Optional.ofNullable( getBedName() )
                        .map( bed -> new String( ": " + bed ) )
                        .orElse( "" );
    }
}
