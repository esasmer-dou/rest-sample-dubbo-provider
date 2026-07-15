package com.reactor.sample.dubbo.provider.service;

import com.reactor.rust.dubbo.sample.CustomerCommandService;
import com.reactor.rust.dubbo.sample.dto.CreateCustomerCommand;
import com.reactor.rust.dubbo.sample.dto.CustomerCommandPayload;
import com.reactor.rust.dubbo.sample.dto.CustomerMutationResult;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.model.customer.SampleCustomer;
import com.reactor.sample.utility.json.SampleJsonCodec;

import java.time.Instant;

public final class CustomerCommandServiceImpl implements CustomerCommandService {

    private final PostgresCustomerRepository customerRepository;
    private final CustomerCommandJsonWriter jsonWriter = new CustomerCommandJsonWriter();

    public CustomerCommandServiceImpl(PostgresCustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public byte[] createCustomer(byte[] commandJson) {
        CreateCustomerCommand command = read(commandJson, CreateCustomerCommand.class);
        String customerNo = command == null ? "" : valueOrEmpty(command.customerNo());
        String fullName = command == null ? "" : valueOrEmpty(command.fullName());
        String segment = command == null || blank(command.segment()) ? "standard" : command.segment();
        String email = command == null ? "" : valueOrEmpty(command.email());
        if (customerNo.isBlank() || fullName.isBlank()) {
            return jsonWriter.error("invalid_customer_command", "customerNo and fullName are required");
        }
        SampleCustomer customer = customerRepository.createCustomer(customerNo, fullName, segment, email);
        return jsonWriter.success(
                "customer_created_or_updated",
                customer,
                command == null ? "" : valueOrEmpty(command.requestId()));
    }

    @Override
    public byte[] patchCustomerSegment(long customerId, byte[] commandJson) {
        CustomerCommandPayload command = read(commandJson, CustomerCommandPayload.class);
        String segment = command == null ? "" : valueOrEmpty(command.segment());
        if (segment.isBlank()) {
            return jsonWriter.error("invalid_segment_command", "segment is required");
        }
        SampleCustomer customer = customerRepository.updateSegment(customerId, segment);
        return customer == null
                ? jsonWriter.notFound(customerId, "customer_not_found")
                : jsonWriter.success(
                        "customer_segment_updated",
                        customer,
                        command == null ? "" : valueOrEmpty(command.requestId()));
    }

    @Override
    public byte[] patchCustomerStatus(long customerId, byte[] commandJson) {
        CustomerCommandPayload command = read(commandJson, CustomerCommandPayload.class);
        String status = command == null ? "" : valueOrEmpty(command.status());
        if (status.isBlank()) {
            return jsonWriter.error("invalid_status_command", "status is required");
        }
        SampleCustomer customer = customerRepository.updateStatus(customerId, status);
        return customer == null
                ? jsonWriter.notFound(customerId, "customer_not_found")
                : jsonWriter.success(
                        "customer_status_updated",
                        customer,
                        command == null ? "" : valueOrEmpty(command.requestId()));
    }

    @Override
    public byte[] deleteCustomer(long customerId, byte[] commandJson) {
        CustomerCommandPayload command = read(commandJson, CustomerCommandPayload.class);
        boolean deleted = customerRepository.deleteCustomer(customerId);
        return jsonWriter.deleted(
                customerId,
                deleted,
                command == null ? "" : valueOrEmpty(command.reason()),
                command == null ? "" : valueOrEmpty(command.requestId()));
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

    private static <T> T read(byte[] body, Class<T> type) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return SampleJsonCodec.read(body, type);
        } catch (IllegalArgumentException invalidJson) {
            return null;
        }
    }
}
