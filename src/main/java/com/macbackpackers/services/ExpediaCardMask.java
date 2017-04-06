
package com.macbackpackers.services;

/**
 * Used for masking expedia booking responses.
 */
public class ExpediaCardMask extends CardMask {

    @Override
    public String getCardMaskMatchRegex() {
        return "cardNumber=\"(\\d+)\"";
    }

}
