package com.macbackpackers.beans;

import java.util.Date;

public class BedSheetEntry {

	private int id;
	private int jobId;
	private String room;
	private String bedName;
	private Date checkoutDate;
	private BedChange status;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getJobId() {
		return jobId;
	}

	public void setJobId(int jobId) {
		this.jobId = jobId;
	}

	public String getRoom() {
		return room;
	}

	public void setRoom(String room) {
		this.room = room;
	}

	public String getBedName() {
		return bedName;
	}

	public void setBedName(String bedName) {
		this.bedName = bedName;
	}

	public Date getCheckoutDate() {
		return checkoutDate;
	}

	public void setCheckoutDate(Date checkoutDate) {
		this.checkoutDate = checkoutDate;
	}

	public BedChange getStatus() {
		return status;
	}

	public void setStatus(BedChange status) {
		this.status = status;
	}
}