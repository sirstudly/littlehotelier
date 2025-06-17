
package com.macbackpackers.beans.expedia.response;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import com.macbackpackers.beans.expedia.Hotel;

@XmlRootElement( name = "Booking" )
@XmlAccessorType( XmlAccessType.FIELD )
public class Booking {

    @XmlAttribute( name = "id" )
    private String id;

    @XmlAttribute( name = "type" )
    private String type;

    @XmlAttribute( name = "createDateTime" )
    private String createDateTime;

    @XmlAttribute( name = "source" )
    private String source;

    @XmlAttribute( name = "status" )
    private String status;

    @XmlAttribute( name = "confirmNumber" )
    private String confirmNumber;

    @XmlElement( name = "Hotel" )
    private Hotel hotel;

    @XmlElement( name = "RoomStay" )
    private RoomStay roomStay;

    @XmlElement( name = "PrimaryGuest" )
    private PrimaryGuest primaryGuest;

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType( String type ) {
        this.type = type;
    }

    public String getCreateDateTime() {
        return createDateTime;
    }

    public void setCreateDateTime( String createDateTime ) {
        this.createDateTime = createDateTime;
    }

    public String getSource() {
        return source;
    }

    public void setSource( String source ) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus( String status ) {
        this.status = status;
    }

    public String getConfirmNumber() {
        return confirmNumber;
    }

    public void setConfirmNumber( String confirmNumber ) {
        this.confirmNumber = confirmNumber;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public void setHotel( Hotel hotel ) {
        this.hotel = hotel;
    }

    public RoomStay getRoomStay() {
        return roomStay;
    }

    public void setRoomStay( RoomStay roomStay ) {
        this.roomStay = roomStay;
    }

    public PrimaryGuest getPrimaryGuest() {
        return primaryGuest;
    }

    public void setPrimaryGuest( PrimaryGuest primaryGuest ) {
        this.primaryGuest = primaryGuest;
    }

}
