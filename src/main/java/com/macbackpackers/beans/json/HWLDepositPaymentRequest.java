
package com.macbackpackers.beans.json;

import java.math.BigDecimal;

/**
 * Request object for confirming the HWL deposit payment in LH.
 */
public class HWLDepositPaymentRequest {

    static class CreditCard {

        public final String card_number = "";
        public final String cvv = "";
        public final String expiry_year = "";
        public final String expiry_month = "";
        public final String name_on_card = "";
        public final String card_type = "";
        public final boolean use_card_on_file = false;
        public final String guest_email = "";
        public final boolean email_invoice = false;
    }

    static class Payment {

        public final int id;
        public final BigDecimal amount;
        public final String paid_at;
        public final boolean payment_type = true; // aka deposit checkbox
        public final String payment_method = "Other";
        public final String card_type = "";
        public final boolean show_note_on_invoice = false;
        public final boolean apply_surcharge = true;
        public final BigDecimal credit_card_surcharge_percentage = BigDecimal.ZERO;
        public final BigDecimal credit_card_surcharge_amount = BigDecimal.ZERO;
        public final BigDecimal total_amount;
        public final CreditCard credit_card = new CreditCard();
        public final String description = "HW automated deposit";

        public Payment( int id, String paidAt, BigDecimal amount ) {
            this.id = id;
            this.amount = amount;
            this.total_amount = amount;
            this.paid_at = paidAt;
        }
    }

    public final Payment payment;

    public HWLDepositPaymentRequest( int id, String paidAt, BigDecimal amount ) {
        this.payment = new Payment( id, paidAt, amount );
    }

}
