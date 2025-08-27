package com.openkm.rest.util;

import com.openkm.bean.DashboardFolderResult;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "dashboardFolderResults")
public class DashboardFolderResultList {

	@XmlElement(name = "dashboardFolderResult", required = true)
	List<DashboardFolderResult> dashboardFolderResults = new ArrayList<>();

	public List<DashboardFolderResult> getList() {
		return dashboardFolderResults;
	}
}
