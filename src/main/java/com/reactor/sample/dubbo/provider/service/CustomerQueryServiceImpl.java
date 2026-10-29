package com.reactor.sample.dubbo.provider.service;

import com.reactor.rust.dubbo.sample.CustomerQueryService;
import com.reactor.rust.dubbo.sample.dto.CustomerStats;
import com.reactor.rust.dubbo.sample.dto.CustomerSummary;
import com.reactor.rust.dubbo.sample.dto.CustomerSegmentQuery;
import com.reactor.sample.utility.json.SampleJsonCodec;
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
    public byte[] findCustomersBySegmentJson(byte[] queryJson) {
        if (queryJson == null || queryJson.length == 0 || queryJson.length > 4096) {
            throw new IllegalArgumentException("Segment query must contain 1..4096 JSON bytes");
        }
        CustomerSegmentQuery query = SampleJsonCodec.read(queryJson, CustomerSegmentQuery.class);
        if (query == null) {
            throw new IllegalArgumentException("Segment query must not be null");
        }
        return jsonWriter.customerSummaries(customerRepository.findCustomersBySegment(query.segment(), query.limit()));
    }

    @Override
    public byte[] findCustomersBySegmentUtf8(long limit, byte[] segmentUtf8) {
        if (limit < Integer.MIN_VALUE || limit > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Segment limit must fit a signed integer");
        }
        return jsonWriter.customerSummaries(customerRepository.findCustomersBySegment(
                decodeSegment(segmentUtf8), (int) limit));
    }

    static String decodeSegment(byte[] segmentUtf8) {
        if (segmentUtf8 == null || segmentUtf8.length > 4096) {
            throw new IllegalArgumentException("Segment must contain at most 4096 UTF-8 bytes");
        }
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(segmentUtf8)).toString();
        } catch (java.nio.charset.CharacterCodingException invalid) {
            throw new IllegalArgumentException("Segment must be valid UTF-8", invalid);
        }
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
