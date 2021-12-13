
package com.macbackpackers.jobs;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

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
