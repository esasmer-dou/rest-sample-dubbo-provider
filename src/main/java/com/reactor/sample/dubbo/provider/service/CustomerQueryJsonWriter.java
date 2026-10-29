package com.reactor.sample.dubbo.provider.service;

import com.reactor.sample.model.customer.SampleCustomer;
import com.reactor.sample.utility.json.SampleJsonWriter;

import java.time.Instant;
import java.util.List;

final class CustomerQueryJsonWriter extends SampleJsonWriter {

    byte[] customerSummaries(List<SampleCustomer> customers) {
        StringBuilder json = json(32 + customers.size() * 192);
        json.append("{\"customers\":[");
        for (int i = 0; i < customers.size(); i++) {
            if (i != 0) json.append(',');
            SampleCustomer row = customers.get(i);
            json.append('{');
            field(json, "id", row.id()).append(',');
            nullableField(json, "customerNo", row.customerNo()).append(',');
            nullableField(json, "fullName", row.fullName()).append(',');
            nullableField(json, "segment", row.segment()).append(',');
            nullableField(json, "email", row.email()).append(',');
            nullableField(json, "status", row.status()).append(',');
            nullableField(json, "updatedAt", row.updatedAt().toString());
            json.append('}');
        }
        return utf8(json.append("]}"));
    }

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

    private StringBuilder nullableField(StringBuilder json, String name, String value) {
        quote(json, name).append(':');
        return value == null ? json.append("null") : quote(json, value);
    }
}
