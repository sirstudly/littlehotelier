
package com.macbackpackers.services;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic regex matcher masker.
 *
 */
public class RegexMask {

    private String matchRegex;
    private Function<Matcher, String> applyMaskFn;

    /**
     * Sets up a login mask. All regexes must contain a single matching group (that will be replaced
     * by {@link #getMaskValue()}).
     * 
     * @param matchRegex the regex to use to find a mask
     * @param applyMaskFn function that takes a Matcher with the regex applied and returns a String.
     *            If this function returns null, then the mask won't be applied and the input string
     *            is returned.
     */
    public RegexMask( String matchRegex, Function<Matcher, String> applyMaskFn ) {
        this.matchRegex = matchRegex;
        this.applyMaskFn = applyMaskFn;
    }

    /**
     * Returns the matching regex to be used.
     * 
     * @return matching regex
     */
    public String getMatchRegex() {
        return matchRegex;
    }

    /**
     * Returns the function that applies the mask on the input string.
     * 
     * @return function (function returns null to do nothing)
     */
    public Function<Matcher, String> getApplyMaskFn() {
        return applyMaskFn;
    }

    /**
     * Masks the given input character sequence using this mask.
     * 
     * @param stringToMask the string to mask
     * @return the masked string
     */
    public String applyMask( String stringToMask ) {
        Pattern p = Pattern.compile( getMatchRegex() );
        Matcher m = p.matcher( stringToMask );
        String maskedResult = applyMaskFn.apply( m );
        return maskedResult == null ? stringToMask : 
            stringToMask.substring( 0, m.start( 1 ) )
                + maskedResult
                + stringToMask.substring( m.end() );
    }
}
