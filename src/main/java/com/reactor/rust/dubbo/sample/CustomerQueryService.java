package com.reactor.rust.dubbo.sample;

import com.reactor.rust.dubbo.sample.dto.CustomerStats;
import com.reactor.rust.dubbo.sample.dto.CustomerSummary;

import java.util.List;

public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();

    CustomerSummary getCustomer(long customerId);

    List<CustomerSummary> findCustomersBySegment(String segment, int limit);

    CustomerStats getCustomerStats();

    boolean customerExists(long customerId);

    String getCustomerDisplayName(long customerId);
}
