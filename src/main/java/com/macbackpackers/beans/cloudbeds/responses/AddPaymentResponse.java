package com.macbackpackers.beans.cloudbeds.responses;

public class AddPaymentResponse extends CloudbedsJsonResponse {

    private AddPaymentResponseData data;

    public AddPaymentResponseData getData() {
        return data;
    }

    public void setData( AddPaymentResponseData data ) {
        this.data = data;
    }
}
