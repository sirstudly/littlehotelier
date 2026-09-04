package com.macbackpackers.ronbot.dto;

import java.util.ArrayList;
import java.util.List;

public class BookingTimelineDto {

    private BookingSummaryDto booking;
    private List<TransactionDto> transactions = new ArrayList<>();
    private List<JobHistoryDto> jobs = new ArrayList<>();

    public BookingSummaryDto getBooking() {
        return booking;
    }

    public void setBooking( BookingSummaryDto booking ) {
        this.booking = booking;
    }

    public List<TransactionDto> getTransactions() {
        return transactions;
    }

    public void setTransactions( List<TransactionDto> transactions ) {
        this.transactions = transactions;
    }

    public List<JobHistoryDto> getJobs() {
        return jobs;
    }

    public void setJobs( List<JobHistoryDto> jobs ) {
        this.jobs = jobs;
    }
}
