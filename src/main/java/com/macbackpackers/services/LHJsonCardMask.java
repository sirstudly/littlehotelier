
package com.macbackpackers.services;

/**
 * Used for masking LH JSON responses.
 */
public class LHJsonCardMask extends CardMask {

    @Override
    public String getCardMaskMatchRegex() {
        return "\"payment_card_number\"\\s*:\\s*\"(\\d+)\"";
    }

    @Override
    public String getCardSecurityCodeRegex() {
        throw new UnsupportedOperationException( "CVC not available in LH" );
    }

}
