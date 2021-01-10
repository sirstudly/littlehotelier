
package com.macbackpackers.beans.cloudbeds.responses;

public class BookingSource {

    private String id;
    private String subSource;
    private String name;
    private int originalId;
    private int parentId;
    private String isActive;

    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

    public String getSubSource() {
        return subSource;
    }

    public void setSubSource( String subSource ) {
        this.subSource = subSource;
    }

    public String getName() {
        return name;
    }

    public void setName( String name ) {
        this.name = name;
    }

    public int getOriginalId() {
        return originalId;
    }

    public void setOriginalId( int originalId ) {
        this.originalId = originalId;
    }

    public int getParentId() {
        return parentId;
    }

    public void setParentId( int parentId ) {
        this.parentId = parentId;
    }

    public String getIsActive() {
        return isActive;
    }

    public void setIsActive( String isActive ) {
        this.isActive = isActive;
    }
    
    public boolean isActive() {
        return "1".equals( getIsActive() );
    }
}
