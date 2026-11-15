package com.macbackpackers.beans.analytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How we recognise one of our hostels on GHS / Hostelworld search pages.
 */
public class PropertyMatch {

    private String propertyCode;
    private String displayName;
    private String hwHostelNumber;
    private List<String> nameAliases = new ArrayList<>();
    private boolean edinburgh;

    public String getPropertyCode() {
        return propertyCode;
    }

    public void setPropertyCode( String propertyCode ) {
        this.propertyCode = propertyCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName( String displayName ) {
        this.displayName = displayName;
    }

    public String getHwHostelNumber() {
        return hwHostelNumber;
    }

    public void setHwHostelNumber( String hwHostelNumber ) {
        this.hwHostelNumber = hwHostelNumber;
    }

    public List<String> getNameAliases() {
        return nameAliases;
    }

    public void setNameAliases( List<String> nameAliases ) {
        this.nameAliases = nameAliases == null ? new ArrayList<>() : nameAliases;
    }

    public boolean isEdinburgh() {
        return edinburgh;
    }

    public void setEdinburgh( boolean edinburgh ) {
        this.edinburgh = edinburgh;
    }

    public boolean matchesName( String listingName ) {
        if ( listingName == null || listingName.isBlank() ) {
            return false;
        }
        String norm = normalize( listingName );
        if ( norm.equals( normalize( displayName ) ) ) {
            return true;
        }
        for ( String alias : nameAliases ) {
            if ( alias != null && !alias.isBlank() && norm.contains( normalize( alias ) ) ) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesHwNumber( String hwNumber ) {
        return hwHostelNumber != null && hwHostelNumber.equals( hwNumber );
    }

    public static String normalize( String s ) {
        return s.toLowerCase( Locale.ROOT ).replaceAll( "[^a-z0-9]+", " " ).trim();
    }

    public static List<String> parseAliases( String csv ) {
        if ( csv == null || csv.isBlank() ) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for ( String part : csv.split( "," ) ) {
            String t = part.trim();
            if ( !t.isEmpty() ) {
                out.add( t );
            }
        }
        return out;
    }
}
