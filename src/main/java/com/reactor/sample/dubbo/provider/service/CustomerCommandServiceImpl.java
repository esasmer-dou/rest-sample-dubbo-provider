package com.reactor.sample.dubbo.provider.service;

import com.reactor.rust.dubbo.sample.CustomerCommandService;
import com.reactor.rust.dubbo.sample.dto.CreateCustomerCommand;
import com.reactor.rust.dubbo.sample.dto.CustomerMutationResult;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.model.customer.SampleCustomer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class CustomerCommandServiceImpl implements CustomerCommandService {

    private final PostgresCustomerRepository customerRepository;

    public CustomerCommandServiceImpl(PostgresCustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public byte[] createCustomer(byte[] commandJson) {
        JsonCommand command = JsonCommand.parse(commandJson);
        String customerNo = command.required("customerNo");
        String fullName = command.required("fullName");
        String segment = command.valueOrDefault("segment", "standard");
        String email = command.valueOrDefault("email", "");
        if (customerNo.isBlank() || fullName.isBlank()) {
            return error("invalid_customer_command", "customerNo and fullName are required");
        }
        SampleCustomer customer = customerRepository.createCustomer(customerNo, fullName, segment, email);
        return success("customer_created_or_updated", customer, command);
    }

    @Override
    public byte[] patchCustomerSegment(long customerId, byte[] commandJson) {
        JsonCommand command = JsonCommand.parse(commandJson);
        String segment = command.required("segment");
        if (segment.isBlank()) {
            return error("invalid_segment_command", "segment is required");
        }
        SampleCustomer customer = customerRepository.updateSegment(customerId, segment);
        return customer == null
                ? notFound(customerId, "customer_not_found")
                : success("customer_segment_updated", customer, command);
    }

    @Override
    public byte[] patchCustomerStatus(long customerId, byte[] commandJson) {
        JsonCommand command = JsonCommand.parse(commandJson);
        String status = command.required("status");
        if (status.isBlank()) {
            return error("invalid_status_command", "status is required");
        }
        SampleCustomer customer = customerRepository.updateStatus(customerId, status);
        return customer == null
                ? notFound(customerId, "customer_not_found")
                : success("customer_status_updated", customer, command);
    }

    @Override
    public byte[] deleteCustomer(long customerId, byte[] commandJson) {
        JsonCommand command = JsonCommand.parse(commandJson);
        boolean deleted = customerRepository.deleteCustomer(customerId);
        StringBuilder json = new StringBuilder(240);
        json.append('{');
        appendString(json, "source", "rest-sample-dubbo-provider").append(',');
        appendString(json, "service", "CustomerCommandService").append(',');
        appendString(json, "operation", "customer_deleted").append(',');
        appendBoolean(json, "deleted", deleted).append(',');
        appendNumber(json, "customerId", customerId).append(',');
        appendString(json, "reason", command.valueOrDefault("reason", "")).append(',');
        appendString(json, "requestId", command.valueOrDefault("requestId", "")).append(',');
        appendString(json, "generatedAt", Instant.now().toString());
        json.append('}');
        return bytes(json);
    }

    @Override
    public CustomerMutationResult createCustomerTyped(CreateCustomerCommand command) {
        if (command == null || blank(command.customerNo()) || blank(command.fullName())) {
            return failure(
                    "customer_create_typed",
                    command == null ? "" : command.requestId(),
                    "customerNo and fullName are required"
            );
        }
        SampleCustomer customer = customerRepository.createCustomer(
                command.customerNo(),
                command.fullName(),
                blank(command.segment()) ? "standard" : command.segment(),
                command.email() == null ? "" : command.email()
        );
        return mutation("customer_created_or_updated_typed", command.requestId(), true, customer, "ok");
    }

    @Override
    public CustomerMutationResult patchCustomerStatusTyped(long customerId, String status, String requestId) {
        if (blank(status)) {
            return failure("customer_status_typed", requestId, "status is required");
        }
        SampleCustomer customer = customerRepository.updateStatus(customerId, status);
        if (customer == null) {
            return new CustomerMutationResult(
                    "customer_status_typed",
                    valueOrEmpty(requestId),
                    false,
                    customerId,
                    "",
                    "",
                    "",
                    "",
                    "customer_not_found",
                    Instant.now().toString()
            );
        }
        return mutation("customer_status_updated_typed", requestId, true, customer, "ok");
    }

    private static byte[] success(String operation, SampleCustomer customer, JsonCommand command) {
        StringBuilder json = new StringBuilder(384);
        json.append('{');
        appendString(json, "source", "rest-sample-dubbo-provider").append(',');
        appendString(json, "service", "CustomerCommandService").append(',');
        appendString(json, "operation", operation).append(',');
        appendString(json, "requestId", command.valueOrDefault("requestId", "")).append(',');
        appendString(json, "generatedAt", Instant.now().toString()).append(',');
        json.append("\"customer\":{");
        appendNumber(json, "id", customer.id()).append(',');
        appendString(json, "customerNo", customer.customerNo()).append(',');
        appendString(json, "fullName", customer.fullName()).append(',');
        appendString(json, "segment", customer.segment()).append(',');
        appendString(json, "email", customer.email()).append(',');
        appendString(json, "status", customer.status()).append(',');
        appendString(json, "createdAt", customer.createdAt().toString()).append(',');
        appendString(json, "updatedAt", customer.updatedAt().toString());
        json.append("}}");
        return bytes(json);
    }

    private static byte[] notFound(long customerId, String code) {
        StringBuilder json = new StringBuilder(160);
        json.append('{');
        appendString(json, "source", "rest-sample-dubbo-provider").append(',');
        appendString(json, "service", "CustomerCommandService").append(',');
        appendString(json, "code", code).append(',');
        appendNumber(json, "customerId", customerId).append(',');
        appendString(json, "generatedAt", Instant.now().toString());
        json.append('}');
        return bytes(json);
    }

    private static byte[] error(String code, String message) {
        StringBuilder json = new StringBuilder(176);
        json.append('{');
        appendString(json, "source", "rest-sample-dubbo-provider").append(',');
        appendString(json, "service", "CustomerCommandService").append(',');
        appendString(json, "code", code).append(',');
        appendString(json, "message", message).append(',');
        appendString(json, "generatedAt", Instant.now().toString());
        json.append('}');
        return bytes(json);
    }

    private static CustomerMutationResult mutation(
            String operation,
            String requestId,
            boolean success,
            SampleCustomer customer,
            String message) {
        return new CustomerMutationResult(
                operation,
                valueOrEmpty(requestId),
                success,
                customer.id(),
                customer.customerNo(),
                customer.fullName(),
                customer.segment(),
                customer.status(),
                message,
                Instant.now().toString()
        );
    }

    private static CustomerMutationResult failure(String operation, String requestId, String message) {
        return new CustomerMutationResult(
                operation,
                valueOrEmpty(requestId),
                false,
                null,
                "",
                "",
                "",
                "",
                message,
                Instant.now().toString()
        );
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static StringBuilder appendString(StringBuilder json, String name, String value) {
        json.append('"').append(name).append("\":\"").append(escapeJson(value)).append('"');
        return json;
    }

    private static StringBuilder appendNumber(StringBuilder json, String name, long value) {
        json.append('"').append(name).append("\":").append(value);
        return json;
    }

    private static StringBuilder appendBoolean(StringBuilder json, String name, boolean value) {
        json.append('"').append(name).append("\":").append(value);
        return json;
    }

    private static byte[] bytes(StringBuilder json) {
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private record JsonCommand(String json) {
        static JsonCommand parse(byte[] body) {
            return new JsonCommand(body == null ? "" : new String(body, StandardCharsets.UTF_8));
        }

        String required(String name) {
            return valueOrDefault(name, "");
        }

        String valueOrDefault(String name, String defaultValue) {
            String value = stringField(json, name);
            return value == null ? defaultValue : value;
        }
    }

    private static String stringField(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String needle = "\"" + fieldName + "\"";
        int field = json.indexOf(needle);
        if (field < 0) {
            return null;
        }
        int colon = json.indexOf(':', field + needle.length());
        if (colon < 0) {
            return null;
        }
        int pos = colon + 1;
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
        if (pos >= json.length() || json.charAt(pos) != '"') {
            return null;
        }
        StringBuilder value = new StringBuilder();
        for (int i = pos + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                return value.toString();
            }
            if (ch == '\\' && i + 1 < json.length()) {
                char escaped = json.charAt(++i);
                value.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"', '\\' -> escaped;
                    default -> escaped;
                });
            } else {
                value.append(ch);
            }
        }
        return null;
    }
}
