
package com.macbackpackers.jobs;

import org.springframework.beans.factory.annotation.Autowired;

import com.macbackpackers.services.GuestRequestExtractionService;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

/**
 * Extracts actionable guest requests (for the guest comments report) from new or changed
 * Cloudbeds special requests.
 */
@Entity
@DiscriminatorValue( value = "com.macbackpackers.jobs.ExtractGuestRequestsJob" )
public class ExtractGuestRequestsJob extends AbstractJob {

    @Autowired
    @Transient
    private GuestRequestExtractionService guestRequestExtractionService;

    @Override
    public void processJob() throws Exception {
        guestRequestExtractionService.extractPendingGuestRequests();
    }
}
