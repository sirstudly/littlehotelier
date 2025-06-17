
package com.macbackpackers.beans.expedia.response;

import java.math.BigDecimal;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement( name = "RoomStay" )
@XmlAccessorType( XmlAccessType.FIELD )
public class RoomStay {

    @XmlAttribute( name = "roomTypeID" )
    private String roomTypeID;

    @XmlAttribute( name = "ratePlanID" )
    private String ratePlanID;

    @XmlElement( name = "StayDate" )
    private StayDate stayDate;

    @XmlElement( name = "GuestCount" )
    private GuestCount guestCount;

    @XmlElement( name = "PerDayRates" )
    private PerDayRates perDayRates;

    @XmlElement( name = "Total" )
    private Total total;

    @XmlElement( name = "PaymentCard" )
    private PaymentCard paymentCard;

    @XmlElement( name = "PrimaryGuest" )
    private PrimaryGuest primaryGuest;

    public String getRoomTypeID() {
        return roomTypeID;
    }

    public void setRoomTypeID( String roomTypeID ) {
        this.roomTypeID = roomTypeID;
    }

    public String getRatePlanID() {
        return ratePlanID;
    }

    public void setRatePlanID( String ratePlanID ) {
        this.ratePlanID = ratePlanID;
    }

    public StayDate getStayDate() {
        return stayDate;
    }

    public void setStayDate( StayDate stayDate ) {
        this.stayDate = stayDate;
    }

    public GuestCount getGuestCount() {
        return guestCount;
    }

    public void setGuestCount( GuestCount guestCount ) {
        this.guestCount = guestCount;
    }

    public PerDayRates getPerDayRates() {
        return perDayRates;
    }

    public void setPerDayRates( PerDayRates perDayRates ) {
        this.perDayRates = perDayRates;
    }

    public Total getTotal() {
        return total;
    }

    public void setTotal( Total total ) {
        this.total = total;
    }

    public PaymentCard getPaymentCard() {
        return paymentCard;
    }

    public void setPaymentCard( PaymentCard paymentCard ) {
        this.paymentCard = paymentCard;
    }

    public PrimaryGuest getPrimaryGuest() {
        return primaryGuest;
    }

    public void setPrimaryGuest( PrimaryGuest primaryGuest ) {
        this.primaryGuest = primaryGuest;
    }

    /**
     * Convenience method for calculating the first night of a booking.
     * @return (non-null) the amount for the first night
     */
    public BigDecimal getRateForFirstNight() {
        if ( false == "GBP".equals( getPerDayRates().getCurrency() ) ) {
            throw new IllegalStateException( "Unsupported currency! " + getPerDayRates().getCurrency() );
        }
        
        // find the matching day rate
        String arrivalDate = getStayDate().getArrival();
        for ( PerDayRate rate : getPerDayRates().getRates() ) {
            if ( arrivalDate.equals( rate.getStayDate() ) ) {
                // ** I have no data to check whether the base rate changes based on number of adults
                // ** Assuming the base rate is already multiplied by the number of guests **
                // ** If this is not correct, will need to multiply as below...
                return rate.getBaseRate(); //.multiply( new BigDecimal( getGuestCount().getAdult() ) );
            }
        }
        throw new IllegalStateException( "Missing PerDayRate for arrival date??" );
    }
}
