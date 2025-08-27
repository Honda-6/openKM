package com.openkm.rest.util;

import com.openkm.bean.DashboardDocumentResult;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "dashboardDocumentResults")
public class DashboardDocumentResultList {

	@XmlElement(name = "dashboardDocumentResult", required = true)
	List<DashboardDocumentResult> dashboardDocumentResults = new ArrayList<>();

	public List<DashboardDocumentResult> getList() {
		return dashboardDocumentResults;
	}
}
