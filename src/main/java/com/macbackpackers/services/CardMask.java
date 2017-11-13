
package com.macbackpackers.services;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Used for logging. Mask card details using regex.
 *
 */
public abstract class CardMask {

    /**
     * Returns the regex that will match the contents of the card number (if specified).
     * 
     * @return regex with the card number in the first capturing group
     */
    public abstract String getCardMaskMatchRegex();

    /**
     * Returns the regex that will match the contents of the CVC/CVV code (if specified).
     * 
     * @return regex with CVV/CVC in the first capturing group
     */
    public abstract String getCardSecurityCodeRegex();

    /**
     * Masks a card number with periods. The first 6 and last 2 digits are left as is. e.g.
     * 1234567890123456 will return 123456........56 If the passed in number is not a number or is
     * not at least 8 characters, this will return the entire string masked with periods.
     * 
     * @param cardNum plaintext card number
     * @return masked card number
     */
    public String replaceCardWith( String cardNum ) {
        final String CARD_NUMBER_MASK = "(\\d{6})(\\d+)(\\d{2})";
        Pattern p = Pattern.compile( CARD_NUMBER_MASK );
        Matcher m = p.matcher( cardNum );
        if ( m.find() ) {
            return m.group( 1 ) + StringUtils.repeat( '.', m.group( 2 ).length() ) + m.group( 3 );
        }
        // does not have at least 8 characters or not a number; mask entire string
        return StringUtils.repeat( '.', cardNum.length() );
    }

    /**
     * Masks the security code with periods.
     * 
     * @param securityCode the (non-null) value of the security code to mask
     * @return periods equal to the length of securityCode
     */
    public String replaceCardSecurityCodeWith( String securityCode ) {
        // mask out entire string
        return StringUtils.repeat( '.', securityCode.length() );
    }

    /**
     * Applies this card mask regex to the given text.
     * 
     * @param textToApply text to mask
     * @return masked text
     */
    public String applyCardMask( String textToApply ) {
        return applyMask( textToApply, this::getCardMaskMatchRegex, this::replaceCardWith );
    }

    /**
     * Applies this card security code regex to the given text.
     * 
     * @param textToApply text to mask
     * @return masked text
     */
    public String applyCardSecurityCodeMask( String textToApply ) {
        return applyMask( textToApply, this::getCardSecurityCodeRegex, this::replaceCardSecurityCodeWith );
    }

    /**
     * Applies the given mask regex to the given text and returns it.
     * 
     * @param textToApply (unmasked) text
     * @param regex the regex pattern that matches what is to masked
     * @param replaceWithFn function that replaces any matching groups in {@code regex}
     * @return the masked text
     */
    public String applyMask( String textToApply, Supplier<String> regex,
            Function<String, String> replaceWithFn ) {
        StringBuffer buf = new StringBuffer();
        Matcher m = Pattern.compile( regex.get() ).matcher( textToApply );
        while ( m.find() ) {
            m.appendReplacement( buf, textToApply.substring( m.start(), m.start( 1 ) ) +
                    replaceWithFn.apply( m.group( 1 ) ) +
                    textToApply.substring( m.end( 1 ), m.end() ) );
        }
        return m.appendTail( buf ).toString();
    }
}
