package com.macbackpackers.beans.analytics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;

import org.junit.jupiter.api.Test;

public class PropertyMatchTest {

    @Test
    public void matchesName_usesAliases() {
        PropertyMatch m = new PropertyMatch();
        m.setDisplayName( "Castle Rock Hostel" );
        m.setNameAliases( List.of( "castle rock", "castlerock" ) );
        assertThat( m.matchesName( "Castle Rock Hostel" ), is( true ) );
        assertThat( m.matchesName( "Castle Rock Hostel Edinburgh" ), is( true ) );
        assertThat( m.matchesName( "Kick Ass Grassmarket" ), is( false ) );
    }
}
