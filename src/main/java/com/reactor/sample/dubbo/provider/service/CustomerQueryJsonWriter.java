package com.reactor.sample.dubbo.provider.service;

import com.reactor.sample.model.customer.SampleCustomer;
import com.reactor.sample.utility.json.SampleJsonWriter;

import java.time.Instant;
import java.util.List;

final class CustomerQueryJsonWriter extends SampleJsonWriter {

    byte[] databaseCustomers(List<SampleCustomer> customers) {
        StringBuilder json = json(512 + customers.size() * 160);
        json.append('{');
        stringField(json, "source", "rest-sample-dubbo-provider").append(',');
        stringField(json, "service", "CustomerQueryService").append(',');
        stringField(json, "storage", "postgresql-jdbc-hikari").append(',');
        stringField(json, "generatedAt", Instant.now().toString()).append(',');
        json.append("\"customers\":[");
        for (int index = 0; index < customers.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            appendCustomer(json, customers.get(index));
        }
        return utf8(json.append("]}"));
    }

    private void appendCustomer(StringBuilder json, SampleCustomer customer) {
        json.append('{');
        field(json, "id", customer.id()).append(',');
        stringField(json, "customerNo", customer.customerNo()).append(',');
        stringField(json, "fullName", customer.fullName()).append(',');
        stringField(json, "segment", customer.segment()).append(',');
        stringField(json, "email", customer.email()).append(',');
        stringField(json, "status", customer.status()).append(',');
        stringField(json, "createdAt", customer.createdAt().toString()).append(',');
        stringField(json, "updatedAt", customer.updatedAt().toString());
        json.append('}');
    }
}
