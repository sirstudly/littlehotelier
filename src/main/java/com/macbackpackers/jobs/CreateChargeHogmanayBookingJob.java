
package com.macbackpackers.jobs;

import com.macbackpackers.services.CloudbedsService;
import org.htmlunit.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

@Entity
@DiscriminatorValue(value = "com.macbackpackers.jobs.CreateChargeHogmanayBookingJob")
public class CreateChargeHogmanayBookingJob extends AbstractJob {

    @Autowired
    @Transient
    @Qualifier("webClientForCloudbeds")
    private WebClient cbWebClient;

    @Autowired
    @Transient
    private CloudbedsService service;

    @Override
    @Transactional
    public void processJob() throws Exception {
        service.createChargeHogmanayBookingJobs( cbWebClient );
    }

    @Override
    public void finalizeJob() {
        cbWebClient.close(); // cleans up JS threads
    }

    @Override
    public int getRetryCount() {
        return 1;
    }
}
