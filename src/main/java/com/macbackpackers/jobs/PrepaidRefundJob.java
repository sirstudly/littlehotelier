
package com.macbackpackers.jobs;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.PaymentProcessorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;
import java.math.BigDecimal;

/**
 * Job that refunds a booking with the current card details.
 */
@Entity
@DiscriminatorValue(value = "com.macbackpackers.jobs.PrepaidRefundJob")
public class PrepaidRefundJob extends AbstractJob {

    @Autowired
    @Transient
    private PaymentProcessorService paymentProcessor;

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean("webClientForCloudbeds", WebClient.class)) {
            paymentProcessor.processPrepaidRefund(webClient, getReservationId(), getAmount(),
                    "As req by BDC: " + getReason() + " -RONBOT");
        }
    }

    public String getReservationId() {
        return getParameter("reservation_id");
    }

    public void setReservationId(String reservationId) {
        setParameter("reservation_id", reservationId);
    }

    public String getReason() {
        return getParameter("reason");
    }

    public void setReason(String reason) {
        setParameter("reason", reason);
    }

    public BigDecimal getAmount() {
        return new BigDecimal(getParameter("amount"));
    }

    public void setAmount(BigDecimal amount) {
        setParameter("amount", amount.toString());
    }

    @Override
    public int getRetryCount() {
        return 1; // limit failed email attempts
    }
}
