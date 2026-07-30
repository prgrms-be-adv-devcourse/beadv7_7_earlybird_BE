package com.growmighty.lectures.firstday.settlement.application.port;

import java.util.List;
import java.util.Set;

public interface ProjectOrderReader {

    List<ProjectOrders> findProjectOrders(Set<Long> projectIds);
}
