package com.macbackpackers.beans.cloudbeds.responses;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ReservationGuestRegistrationTest {

    private Gson gson;

    @BeforeEach
    public void setUp() {
        gson = new GsonBuilder()
                .setFieldNamingPolicy( FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES )
                .create();
    }

    @Test
    public void incompleteWhenDocumentTypeMissing() throws IOException {
        Reservation reservation = load( "get_reservation_with_evl.json" );
        assertThat( reservation.getNamedGuests().isEmpty(), is( false ) );
        assertThat( reservation.isGuestRegistrationComplete(), is( false ) );
    }

    @Test
    public void completeWhenUkGuestHasTypeAndCountryButNoNumber() {
        Reservation reservation = reservationWithGuests( 1, ukGuestWithoutNumber() );
        assertThat( reservation.isGuestRegistrationComplete(), is( true ) );
    }

    @Test
    public void completeWhenIrishGuestHasTypeAndCountryButNoNumber() {
        Guest guest = guest( "passport", "", "IE", "Ireland" );
        Reservation reservation = reservationWithGuests( 1, guest );
        assertThat( reservation.isGuestRegistrationComplete(), is( true ) );
    }

    @Test
    public void incompleteWhenNonUkGuestMissingDocumentNumber() {
        Guest guest = guest( "passport", "", "IT", "Italy" );
        Reservation reservation = reservationWithGuests( 1, guest );
        assertThat( reservation.isGuestRegistrationComplete(), is( false ) );
    }

    @Test
    public void incompleteWhenOccupancyExceedsNamedGuests() {
        Reservation reservation = reservationWithGuests( 3, ukGuestWithoutNumber() );
        assertThat( reservation.isGuestRegistrationComplete(), is( false ) );
    }

    @Test
    public void ignoresDeletedGuests() {
        Guest active = ukGuestWithoutNumber();
        Guest deleted = guest( "na", "", null, null );
        deleted.setDeleted( "1" );
        Reservation reservation = reservationWithGuests( 1, active, deleted );
        assertThat( reservation.getNamedGuests().size(), is( 1 ) );
        assertThat( reservation.isGuestRegistrationComplete(), is( true ) );
    }

    @Test
    public void completeWhenAllGuestsHaveDocs() {
        Guest g1 = guest( "passport", "*****", "IT", "Italy" );
        Guest g2 = guest( "driver_licence", "*****", "GB", "United Kingdom" );
        Reservation reservation = reservationWithGuests( 2, g1, g2 );
        assertThat( reservation.isGuestRegistrationComplete(), is( true ) );
    }

    private Reservation load( String resource ) throws IOException {
        String json = IOUtils.toString(
                getClass().getClassLoader().getResourceAsStream( resource ),
                StandardCharsets.UTF_8 );
        return gson.fromJson( json, Reservation.class );
    }

    private static Reservation reservationWithGuests( int adults, Guest... guests ) {
        Reservation reservation = new Reservation();
        reservation.setAdultsNumber( adults );
        reservation.setKidsNumber( 0 );
        reservation.setAdditionalGuests( guests == null ? Collections.emptyList() : Arrays.asList( guests ) );
        return reservation;
    }

    private static Guest ukGuestWithoutNumber() {
        return guest( "passport", "", "GB", "United Kingdom" );
    }

    private static Guest guest( String type, String number, String country, String countryName ) {
        Guest guest = new Guest();
        guest.setFirstName( "Test" );
        guest.setLastName( "Guest" );
        guest.setDeleted( "0" );
        guest.setDocumentType( type );
        guest.setDocumentNumber( number );
        guest.setDocumentIssuingCountry( country );
        guest.setDocumentIssuingCountryName( countryName );
        return guest;
    }
}
