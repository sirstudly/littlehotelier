
package com.macbackpackers.beans.json;

import java.io.File;
import java.io.FileReader;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class JsonSerialisationTest {

    final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Test
    public void testSerializeDeserialize() throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject reservationRoot = gson.fromJson( new FileReader( "test_load_page_paid_bdc.json" ), JsonElement.class ).getAsJsonObject();
        reservationRoot.remove( "payments" );
        reservationRoot.remove( "invoices" );
        reservationRoot.remove( "property" );
        reservationRoot.remove( "cards_on_file" );

        reservationRoot.getAsJsonArray( "reservation_room_types" ).forEach(
                e -> {
                    e.getAsJsonObject().remove( "room_type_name" );
                    e.getAsJsonObject().remove( "rate_plan_name" );
                    e.getAsJsonObject().remove( "rate_plan" );
                    e.getAsJsonObject().remove( "room_name" );
                    e.getAsJsonObject().remove( "pictures" );
                    e.getAsJsonObject().remove( "extra_occupants_total" );
                    e.getAsJsonObject().remove( "rate_plans" );
                    e.getAsJsonObject().remove( "rooms" );
                } );
        reservationRoot.getAsJsonObject().addProperty( "notes", "new comment here" );

        JsonObject newRoot = new JsonObject();
        newRoot.add( "form-info", new JsonPrimitive( true ) );
        newRoot.add( "reservation", gson.toJsonTree( reservationRoot ) );
        LOGGER.info( gson.toJson( newRoot ) );
    }

    @Test
    public void testVerifyErrorObject() throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject responseObj = gson.fromJson( "{\"message\":\"Validation failed: Payment card number card is not accepted\",\"errors\":{\"payment_card_number\":[\"card is not accepted\"],\"payment_card_type\":[],\"reservation_room_types\":[{}],\"reservation_extras\":[],\"reservation_guests\":{\"70349797745040\":{}}}}", JsonElement.class ).getAsJsonObject();
        Assert.assertFalse( "One or more errors found in response", responseObj.get( "errors" ).getAsJsonObject().entrySet().isEmpty() );

        responseObj = gson.fromJson( FileUtils.readFileToString( new File( "test_load_page.json" ), "UTF-8" ), JsonElement.class ).getAsJsonObject();
        Assert.assertNull( "No errors found", responseObj.get( "errors" ) );
    }

}
