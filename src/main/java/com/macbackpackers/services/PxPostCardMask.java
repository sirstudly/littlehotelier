
package com.macbackpackers.services;

/**
 * Used for masking card details during a PxPost request.
 */
public class PxPostCardMask extends CardMask {

    @Override
    public String getCardMaskMatchRegex() {
        return "<CardNumber>(\\d+)</CardNumber>";
    }

    @Override
    public String getCardSecurityCodeRegex() {
        return "<Cvc2>(\\d+)</Cvc2>";
    }

}
