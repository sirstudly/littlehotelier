
package com.macbackpackers.beans.json;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class JsonSerialisationTest {

    final Logger LOGGER = LoggerFactory.getLogger( getClass() );

    @Test
    public void testSerializeDeserialize() throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject reservationRoot = gson.fromJson(
                IOUtils.toString( JsonSerialisationTest.class.getClassLoader()
                        .getResourceAsStream( "test_load_page_paid_bdc.json" ),
                        Charset.defaultCharset() ),
                JsonElement.class ).getAsJsonObject();
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

    @Test
    public void testRecordDeposit() throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject reservationRoot = gson.fromJson(
                IOUtils.toString( JsonSerialisationTest.class.getClassLoader()
                        .getResourceAsStream( "test_hwl_unconfirmed_load.json" ),
                        Charset.defaultCharset() ),
                JsonElement.class ).getAsJsonObject();

        JsonArray arr = reservationRoot.getAsJsonArray( "pending_payments" );
        if ( arr.size() == 0 ) {
            return; // nothing to do
        }
        else if ( arr.size() > 1 ) {
            LOGGER.error( "More than one pending payment?" );
            LOGGER.error( arr.toString() );
            // throw exception here
        }
        JsonObject pendingPayment = arr.get( 0 ).getAsJsonObject();
        LOGGER.info( ToStringBuilder.reflectionToString( pendingPayment ) );

        // root element
        JsonObject recordPayment = new JsonObject();

        // main payment element
        JsonObject payment = new JsonObject();
        payment.add( "id", new JsonPrimitive( pendingPayment.get( "id" ).getAsNumber() ) );
        BigDecimal amount = new BigDecimal( pendingPayment.get( "amount" ).getAsString().replaceAll( "£", "" ) );
        payment.add( "amount", new JsonPrimitive( amount ) );
        payment.add( "total_amount", new JsonPrimitive( amount ) );
        payment.add( "paid_at", new JsonPrimitive( pendingPayment.get( "paid_at" ).getAsString() ) );
        payment.add( "payment_type", new JsonPrimitive( true ) );
        payment.add( "payment_method", new JsonPrimitive( pendingPayment.get( "payment_method" ).getAsString() ) );
        payment.add( "card_type", new JsonPrimitive( "" ) );
        payment.add( "show_note_on_invoice", new JsonPrimitive( false ) );
        payment.add( "apply_surcharge", new JsonPrimitive( true ) );
        payment.add( "credit_card_surcharge_percentage", new JsonPrimitive( 0 ) );
        payment.add( "credit_card_surcharge_amount", new JsonPrimitive( 0 ) );
        payment.add( "description", new JsonPrimitive( "HW automated deposit" ) );
        recordPayment.add( "payment", payment );

        // empty card details element
        JsonObject creditCard = new JsonObject();
        creditCard.add( "card_number", new JsonPrimitive( "" ) );
        creditCard.add( "cvv", new JsonPrimitive( "" ) );
        creditCard.add( "expiry_year", new JsonPrimitive( "" ) );
        creditCard.add( "expiry_month", new JsonPrimitive( "" ) );
        creditCard.add( "name_on_card", new JsonPrimitive( "" ) );
        creditCard.add( "card_type", new JsonPrimitive( "" ) );
        creditCard.add( "use_card_on_file", new JsonPrimitive( false ) );
        creditCard.add( "guest_email", new JsonPrimitive( "" ) );
        creditCard.add( "email_invoice", new JsonPrimitive( false ) );
        payment.add( "credit_card", creditCard );

        LOGGER.info( gson.toJson( recordPayment ) );
    }

    @Test
    public void testRecordDepositObject() throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject reservationRoot = gson.fromJson(
                IOUtils.toString( JsonSerialisationTest.class.getClassLoader()
                        .getResourceAsStream( "test_hwl_unconfirmed_load.json" ),
                        Charset.defaultCharset() ),
                JsonElement.class ).getAsJsonObject();

        JsonArray arr = reservationRoot.getAsJsonArray( "pending_payments" );
        JsonObject pendingPayment = arr.get( 0 ).getAsJsonObject();
        LOGGER.info( ToStringBuilder.reflectionToString( pendingPayment ) );

        HWLDepositPaymentRequest req = new HWLDepositPaymentRequest(
                pendingPayment.get( "id" ).getAsInt(),
                pendingPayment.get( "paid_at" ).getAsString(),
                new BigDecimal( pendingPayment.get( "amount" ).getAsString().replaceAll( "£", "" ) ) );
        LOGGER.info( gson.toJson( req ) );
    }

}
