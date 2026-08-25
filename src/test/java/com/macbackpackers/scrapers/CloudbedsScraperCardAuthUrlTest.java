package com.macbackpackers.scrapers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CloudbedsScraperCardAuthUrlTest {

    @Test
    void extractsCombinedRequestUrlFromApproveButton() {
        // Same shape as sample_3ds_approval.html: Approve and Decline share the request URL (no /approve)
        String html = "<a class=\"button raised\" href=\"https://hotels.cloudbeds.com/payment/request/7WZjIT/1bc1d614-3038-4c42-a239-27a64113b3e9\" target=\"_blank\">\n"
                + "                        Approve                                              </a>\n"
                + "<a class=\"button\" href=\"https://hotels.cloudbeds.com/payment/request/7WZjIT/1bc1d614-3038-4c42-a239-27a64113b3e9\">Decline</a>";
        assertEquals( "https://hotels.cloudbeds.com/payment/request/7WZjIT/1bc1d614-3038-4c42-a239-27a64113b3e9",
                CloudbedsScraper.extractCardAuthApproveUrl( html ) );
    }

    @Test
    void extractsDirectApprovePath() {
        String html = "<a href=\"https://hotels.cloudbeds.com/payment/request/abc/uuid/approve\">Approve</a>";
        assertEquals( "https://hotels.cloudbeds.com/payment/request/abc/uuid/approve",
                CloudbedsScraper.extractCardAuthApproveUrl( html ) );
    }

    @Test
    void returnsNullWhenNoApproveLink() {
        assertNull( CloudbedsScraper.extractCardAuthApproveUrl( "<a href=\"https://example.com\">View</a>" ) );
    }
}
