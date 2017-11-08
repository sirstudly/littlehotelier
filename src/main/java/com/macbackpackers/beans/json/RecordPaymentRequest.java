
package com.macbackpackers.beans.json;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Request object for recording a payment in LH.
 */
public class RecordPaymentRequest {

    static class Payment {

        public final BigDecimal amount;
        public final boolean apply_surcharge = true;
        public final String card_type;
        public final BigDecimal credit_card_surcharge_amount = BigDecimal.ZERO;
        public final BigDecimal credit_card_surcharge_percentage = BigDecimal.ZERO;
        public final String description;
        public final Date paid_at;
        public final String payment_method;
        public final boolean payment_type; // aka deposit checkbox
        public final boolean processed = false;
        public final int reservation_id;
        public final boolean show_note_on_invoice = false;
        public final BigDecimal total_amount;

        public Payment( int reservationId, String cardType, BigDecimal amount, String description, boolean isDeposit ) {
            this.reservation_id = reservationId;
            if ( "Other".equals( cardType ) ) {
                this.card_type = "";
                this.payment_method = "Other";
            }
            else {
                this.card_type = cardType;
                this.payment_method = "Card";
            }
            this.amount = amount;
            this.total_amount = amount;
            this.description = description;
            this.payment_type = isDeposit;
            this.paid_at = new Date();
        }
    }

    public final Payment payment;

    public RecordPaymentRequest( int reservationId, String cardType, BigDecimal amount, String description, boolean isDeposit ) {
        this.payment = new Payment( reservationId, cardType, amount, description, isDeposit );
    }

}
