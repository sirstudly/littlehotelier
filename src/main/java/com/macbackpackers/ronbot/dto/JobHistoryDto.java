package com.macbackpackers.ronbot.dto;

import java.util.Map;

public class JobHistoryDto {

    private int jobId;
    private String classname;
    private String status;
    private String processedBy;
    private String createdDate;
    private String startDate;
    private String endDate;
    private String lastUpdatedDate;
    private Map<String, String> parameters;

    public int getJobId() {
        return jobId;
    }

    public void setJobId( int jobId ) {
        this.jobId = jobId;
    }

    public String getClassname() {
        return classname;
    }

    public void setClassname( String classname ) {
        this.classname = classname;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus( String status ) {
        this.status = status;
    }

    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy( String processedBy ) {
        this.processedBy = processedBy;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate( String createdDate ) {
        this.createdDate = createdDate;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate( String startDate ) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate( String endDate ) {
        this.endDate = endDate;
    }

    public String getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public void setLastUpdatedDate( String lastUpdatedDate ) {
        this.lastUpdatedDate = lastUpdatedDate;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters( Map<String, String> parameters ) {
        this.parameters = parameters;
    }
}
