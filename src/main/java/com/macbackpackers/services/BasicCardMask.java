package com.macbackpackers.services;

/**
 * A straightforward card mask that blanks the card number/cvv.
 *
 */
public class BasicCardMask extends CardMask {

    @Override
    public String getCardMaskMatchRegex() {
        return "(\\d+)";
    }

    @Override
    public String getCardSecurityCodeRegex() {
        return "(\\d+)";
    }

}
