package com.macbackpackers.beans;

public enum BedChange {
    YES("Y"),
    NO("N"),
    THREE_DAY_CHANGE("3");

    private String value;

    private BedChange(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static BedChange fromValue(String value) {

        for (BedChange bc : BedChange.values()) {
            if (bc.getValue().equalsIgnoreCase(value)) {
                return bc;
            }
        }
        throw new IllegalArgumentException(value + " is not a valid entry!");
    }

}