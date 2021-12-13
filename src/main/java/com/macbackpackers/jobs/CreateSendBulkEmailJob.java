
package com.macbackpackers.jobs;

import java.time.LocalDate;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.gargoylesoftware.htmlunit.WebClient;
import com.macbackpackers.services.CloudbedsService;

/**
 * Job that creates individual jobs for sending out emails to guests.
 */
@Entity
@DiscriminatorValue(value = "com.macbackpackers.jobs.CreateSendBulkEmailJob")
public class CreateSendBulkEmailJob extends AbstractJob {

    @Autowired
    @Transient
    private ApplicationContext appContext;

    @Autowired
    @Transient
    private CloudbedsService cloudbedsService;

    @Override
    public void processJob() throws Exception {
        try (WebClient webClient = appContext.getBean("webClientForCloudbeds", WebClient.class)) {
            cloudbedsService.createSendTemplatedEmailJobs(webClient,
                    getEmailTemplate(), getStayDateStart(), getStayDateEnd(),
                    getCheckinDateStart(), getCheckinDateEnd(), getStatuses());
        }
    }

    public LocalDate getStayDateStart() {
        String stayDateStart = getParameter("stay_date_start");
        return StringUtils.isBlank(stayDateStart) ? null : LocalDate.parse(stayDateStart);
    }

    public void setStayDateStart(LocalDate stayDateStart) {
        setParameter("stay_date_start", stayDateStart.toString());
    }

    public LocalDate getStayDateEnd() {
        String stayDateEnd = getParameter("stay_date_end");
        return StringUtils.isBlank(stayDateEnd) ? null : LocalDate.parse(stayDateEnd);
    }

    public void setStayDateEnd(LocalDate stayDateEnd) {
        setParameter("stay_date_end", stayDateEnd.toString());
    }

    public LocalDate getCheckinDateStart() {
        String checkinDateStart = getParameter("checkin_date_start");
        return StringUtils.isBlank(checkinDateStart) ? null : LocalDate.parse(checkinDateStart);
    }

    public void setCheckinDateStart(LocalDate checkinDateStart) {
        setParameter("checkin_date_start", checkinDateStart.toString());
    }

    public LocalDate getCheckinDateEnd() {
        String checkinDateEnd = getParameter("checkin_date_end");
        return StringUtils.isBlank(checkinDateEnd) ? null : LocalDate.parse(checkinDateEnd);
    }

    public void setCheckinDateEnd(LocalDate checkinDateEnd) {
        setParameter("checkin_date_end", checkinDateEnd.toString());
    }

    public String getStatuses() {
        String statuses = getParameter("statuses");
        if (StringUtils.isBlank(statuses)) {
            throw new IllegalArgumentException("Missing value for statuses");
        }
        return statuses;
    }

    public void setStatuses(String statuses) {
        setParameter("statuses", statuses);
    }

    public String getEmailTemplate() {
        return getParameter("email_template");
    }

    public void setEmailTemplate(String template) {
        setParameter("email_template", template);
    }
}
