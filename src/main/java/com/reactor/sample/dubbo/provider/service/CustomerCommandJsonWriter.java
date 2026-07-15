package com.reactor.sample.dubbo.provider.service;

import com.reactor.sample.model.customer.SampleCustomer;
import com.reactor.sample.utility.json.SampleJsonWriter;

import java.time.Instant;

final class CustomerCommandJsonWriter extends SampleJsonWriter {

    byte[] deleted(long customerId, boolean deleted, String reason, String requestId) {
        StringBuilder json = response(240, "customer_deleted");
        field(json, "deleted", deleted).append(',');
        field(json, "customerId", customerId).append(',');
        stringField(json, "reason", reason).append(',');
        stringField(json, "requestId", requestId).append(',');
        return complete(json);
    }

    byte[] success(String operation, SampleCustomer customer, String requestId) {
        StringBuilder json = response(384, operation);
        stringField(json, "requestId", requestId).append(',');
        stringField(json, "generatedAt", Instant.now().toString()).append(',');
        json.append("\"customer\":{");
        field(json, "id", customer.id()).append(',');
        stringField(json, "customerNo", customer.customerNo()).append(',');
        stringField(json, "fullName", customer.fullName()).append(',');
        stringField(json, "segment", customer.segment()).append(',');
        stringField(json, "email", customer.email()).append(',');
        stringField(json, "status", customer.status()).append(',');
        stringField(json, "createdAt", customer.createdAt().toString()).append(',');
        stringField(json, "updatedAt", customer.updatedAt().toString());
        return utf8(json.append("}}"));
    }

    byte[] notFound(long customerId, String code) {
        StringBuilder json = base(160);
        stringField(json, "code", code).append(',');
        field(json, "customerId", customerId).append(',');
        return complete(json);
    }

    byte[] error(String code, String message) {
        StringBuilder json = base(176);
        stringField(json, "code", code).append(',');
        stringField(json, "message", message).append(',');
        return complete(json);
    }

    private StringBuilder response(int capacity, String operation) {
        StringBuilder json = base(capacity);
        stringField(json, "operation", operation).append(',');
        return json;
    }

    private StringBuilder base(int capacity) {
        StringBuilder json = json(capacity);
        json.append('{');
        stringField(json, "source", "rest-sample-dubbo-provider").append(',');
        stringField(json, "service", "CustomerCommandService").append(',');
        return json;
    }

    private byte[] complete(StringBuilder json) {
        stringField(json, "generatedAt", Instant.now().toString());
        return utf8(json.append('}'));
    }
}
