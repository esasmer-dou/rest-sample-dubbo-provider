package com.reactor.rust.dubbo.sample;

import com.reactor.rust.dubbo.sample.dto.CreateCustomerCommand;
import com.reactor.rust.dubbo.sample.dto.CustomerMutationResult;

public interface CustomerCommandService {

    byte[] createCustomer(byte[] commandJson);

    byte[] patchCustomerSegment(long customerId, byte[] commandJson);

    byte[] patchCustomerStatus(long customerId, byte[] commandJson);

    byte[] deleteCustomer(long customerId, byte[] commandJson);

    CustomerMutationResult createCustomerTyped(CreateCustomerCommand command);

    CustomerMutationResult patchCustomerStatusTyped(long customerId, String status, String requestId);
}
