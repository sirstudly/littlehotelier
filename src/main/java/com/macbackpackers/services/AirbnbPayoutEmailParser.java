package com.macbackpackers.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Parses Airbnb host bank-payout emails ({@code PAYMENTS_HOST_PAYOUT_SENT*}) into
 * per-confirmation-code remittance lines (reservation and resolution), and extracts
 * confirmation codes from cancel-notice subjects.
 */
public final class AirbnbPayoutEmailParser {

    public static final String SUBJECT_PREFIX = "We sent a payout";

    public static final String LINE_RESERVATION = "reservation";
    public static final String LINE_RESOLUTION = "resolution";

    private static final Pattern CONFIRMATION_CODE = Pattern.compile( "^\\s*(HM[A-Z0-9]{8})\\s*$" );
    private static final Pattern CONFIRMATION_CODE_INLINE = Pattern.compile( "\\b(HM[A-Z0-9]{8})\\b" );
    /** Guest/label, optional minus, optional currency glyph, amount, GBP. */
    private static final Pattern AMOUNT_LINE = Pattern.compile(
            "^(.*?)\\s*(-)?\\s*[£$€]?\\s*(\\d+[.,]\\d{2})\\s*GBP\\s*$", Pattern.CASE_INSENSITIVE );
    private static final Pattern TOTAL_PAID = Pattern.compile( "^\\s*Total\\s+paid\\s*:", Pattern.CASE_INSENSITIVE );
    private static final Pattern RESOLUTION_HINT = Pattern.compile(
            "Resolution\\s+Adjustment", Pattern.CASE_INSENSITIVE );
    private static final Pattern CANCEL_SUBJECT = Pattern.compile(
            "(?i)(?:Canceled:\\s*Reservation|Reservation\\s+canceled)[^\\n]*?\\b(HM[A-Z0-9]{8})\\b" );

    private AirbnbPayoutEmailParser() {
    }

    /** One remittance line inside a bank-payout email. */
    public static final class LineItem {
        private final String confirmationCode;
        private final BigDecimal amount;
        private final String guestName;
        private final String lineType;

        public LineItem( String confirmationCode, BigDecimal amount, String guestName, String lineType ) {
            this.confirmationCode = confirmationCode;
            this.amount = amount;
            this.guestName = guestName;
            this.lineType = lineType;
        }

        public String getConfirmationCode() {
            return confirmationCode;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getGuestName() {
            return guestName;
        }

        public String getLineType() {
            return lineType;
        }

        public boolean isResolution() {
            return LINE_RESOLUTION.equals( lineType );
        }

        public boolean isReservation() {
            return LINE_RESERVATION.equals( lineType );
        }
    }

    public static final class ParseResult {
        private final boolean payoutEmail;
        private final List<LineItem> lines;

        public ParseResult( boolean payoutEmail, List<LineItem> lines ) {
            this.payoutEmail = payoutEmail;
            this.lines = lines;
        }

        public boolean isPayoutEmail() {
            return payoutEmail;
        }

        public List<LineItem> getLines() {
            return lines;
        }
    }

    /**
     * Aggregated non-superseded email totals for one confirmation code.
     */
    public static final class EmailTotals {
        private final BigDecimal reservationSum;
        private final BigDecimal resolutionSum;
        private final String guestName;

        public EmailTotals( BigDecimal reservationSum, BigDecimal resolutionSum, String guestName ) {
            this.reservationSum = reservationSum;
            this.resolutionSum = resolutionSum;
            this.guestName = guestName;
        }

        public BigDecimal getReservationSum() {
            return reservationSum;
        }

        public BigDecimal getResolutionSum() {
            return resolutionSum;
        }

        public BigDecimal getNetAmount() {
            return reservationSum.add( resolutionSum ).setScale( 2, RoundingMode.HALF_UP );
        }

        public boolean hasReservation() {
            return reservationSum.compareTo( BigDecimal.ZERO ) != 0;
        }

        public boolean hasResolution() {
            return resolutionSum.compareTo( BigDecimal.ZERO ) != 0;
        }

        public String getGuestName() {
            return guestName;
        }
    }

    /**
     * @param subject email subject (may include encoded currency glyph)
     * @param body HTML or plain body
     */
    public static ParseResult parse( String subject, String body ) {
        if ( false == isPayoutSubject( subject ) ) {
            return new ParseResult( false, Collections.emptyList() );
        }
        List<String> textLines = toTextLines( body );
        List<LineItem> items = new ArrayList<>();
        for ( int i = 0; i < textLines.size(); i++ ) {
            String line = textLines.get( i );
            if ( TOTAL_PAID.matcher( line ).find() ) {
                continue;
            }
            Matcher amountMatcher = AMOUNT_LINE.matcher( line );
            if ( false == amountMatcher.matches() ) {
                continue;
            }
            String guestRaw = StringUtils.trimToNull( amountMatcher.group( 1 ) );
            boolean negative = StringUtils.isNotBlank( amountMatcher.group( 2 ) );
            // Also treat a trailing minus on the guest label as negative (e.g. "Rocky Henry -")
            if ( guestRaw != null && guestRaw.endsWith( "-" ) ) {
                negative = true;
                guestRaw = StringUtils.trimToNull( guestRaw.substring( 0, guestRaw.length() - 1 ) );
            }
            BigDecimal amount = parseMoney( amountMatcher.group( 3 ) );
            if ( amount.compareTo( BigDecimal.ZERO ) <= 0 ) {
                continue;
            }
            if ( negative ) {
                amount = amount.negate();
            }

            String code = null;
            boolean resolutionNearby = false;
            for ( int j = i + 1; j < textLines.size() && j <= i + 4; j++ ) {
                String next = textLines.get( j );
                if ( RESOLUTION_HINT.matcher( next ).find() ) {
                    resolutionNearby = true;
                    continue;
                }
                Matcher codeMatcher = CONFIRMATION_CODE.matcher( next );
                if ( codeMatcher.matches() ) {
                    code = codeMatcher.group( 1 );
                    break;
                }
                if ( AMOUNT_LINE.matcher( next ).matches() ) {
                    break;
                }
            }
            if ( code == null ) {
                continue;
            }
            if ( guestRaw == null
                    || guestRaw.toLowerCase().contains( "was sent" )
                    || guestRaw.toLowerCase().startsWith( "total" ) ) {
                continue;
            }

            String lineType = ( negative || resolutionNearby ) ? LINE_RESOLUTION : LINE_RESERVATION;
            // Strip "Resolution Adjustment …" if it was glued into guest somehow
            if ( RESOLUTION_HINT.matcher( guestRaw ).find() ) {
                lineType = LINE_RESOLUTION;
            }
            items.add( new LineItem( code, amount, guestRaw, lineType ) );
        }
        return new ParseResult( true, items );
    }

    public static boolean isPayoutSubject( String subject ) {
        if ( StringUtils.isBlank( subject ) ) {
            return false;
        }
        String normalized = subject.replace( '\u00a0', ' ' ).trim();
        return StringUtils.containsIgnoreCase( normalized, SUBJECT_PREFIX );
    }

    public static boolean isCancelSubject( String subject ) {
        return StringUtils.isNotBlank( subject ) && CANCEL_SUBJECT.matcher( subject ).find();
    }

    /**
     * Extracts the Airbnb confirmation code from a cancel-notice subject, or null.
     */
    public static String parseCancelConfirmationCode( String subject ) {
        if ( StringUtils.isBlank( subject ) ) {
            return null;
        }
        Matcher m = CANCEL_SUBJECT.matcher( subject );
        if ( m.find() ) {
            return m.group( 1 );
        }
        Matcher inline = CONFIRMATION_CODE_INLINE.matcher( subject );
        if ( inline.find() && StringUtils.containsIgnoreCase( subject, "cancel" ) ) {
            return inline.group( 1 );
        }
        return null;
    }

    static List<String> toTextLines( String body ) {
        if ( StringUtils.isBlank( body ) ) {
            return Collections.emptyList();
        }
        String text = body;
        text = text.replaceAll( "(?i)<br\\s*/?>", "\n" );
        text = text.replaceAll( "(?i)</(p|div|tr|td|li|h[1-6])>", "\n" );
        text = text.replaceAll( "(?i)<[^>]+>", " " );
        text = text.replace( "&nbsp;", " " );
        text = text.replace( '\u00a0', ' ' );
        text = text.replace( "&#163;", "£" );
        text = text.replace( "&pound;", "£" );
        text = text.replaceAll( "[ \\t]+", " " );
        List<String> lines = new ArrayList<>();
        for ( String raw : text.split( "\\R" ) ) {
            String line = raw.trim();
            if ( false == line.isEmpty() ) {
                lines.add( line );
            }
        }
        return lines;
    }

    static BigDecimal parseMoney( String raw ) {
        String cleaned = raw.replace( ",", "" ).trim();
        return new BigDecimal( cleaned ).setScale( 2, RoundingMode.HALF_UP );
    }
}
