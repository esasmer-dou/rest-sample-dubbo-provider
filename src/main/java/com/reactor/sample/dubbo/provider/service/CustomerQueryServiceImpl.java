package com.reactor.sample.dubbo.provider.service;

import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.sample.dto.CustomerStats;
import com.reactor.rust.dubbo.sample.dto.CustomerSummary;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.model.customer.CustomerCounts;
import com.reactor.sample.model.customer.SampleCustomer;

import java.time.Instant;
import java.util.List;

public final class CustomerQueryServiceImpl implements CustomerQueryService, AutoCloseable {

    private final PostgresCustomerRepository customerRepository;
    private final CustomerQueryJsonWriter jsonWriter = new CustomerQueryJsonWriter();

    public CustomerQueryServiceImpl(PostgresCustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public void warmupDatabase() {
        customerRepository.findCustomers();
    }

    @Override
    public byte[] getDatabaseCustomersJson() {
        return jsonWriter.databaseCustomers(customerRepository.findCustomers());
    }

    @Override
    public CustomerSummary getCustomer(long customerId) {
        SampleCustomer customer = customerRepository.findCustomer(customerId);
        return customer == null ? null : summary(customer);
    }

    @Override
    public List<CustomerSummary> findCustomersBySegment(String segment, int limit) {
        return customerRepository.findCustomersBySegment(segment, limit)
                .stream()
                .map(CustomerQueryServiceImpl::summary)
                .toList();
    }

    @Override
    public CustomerStats getCustomerStats() {
        CustomerCounts counts = customerRepository.countCustomersByStatus();
        return new CustomerStats(
                counts.total(),
                counts.active(),
                counts.passive(),
                Instant.now().toString()
        );
    }

    @Override
    public boolean customerExists(long customerId) {
        return customerRepository.customerExists(customerId);
    }

    @Override
    public String getCustomerDisplayName(long customerId) {
        return customerRepository.customerDisplayName(customerId);
    }

    @Override
    public void close() {
        customerRepository.close();
    }

    private static CustomerSummary summary(SampleCustomer customer) {
        return new CustomerSummary(
                customer.id(),
                customer.customerNo(),
                customer.fullName(),
                customer.segment(),
                customer.email(),
                customer.status(),
                customer.updatedAt().toString()
        );
    }
}
