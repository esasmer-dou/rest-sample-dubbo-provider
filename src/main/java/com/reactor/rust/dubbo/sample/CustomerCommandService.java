package com.reactor.rust.dubbo.sample;

public interface CustomerCommandService {

    byte[] createCustomer(byte[] commandJson);

    byte[] patchCustomerSegment(long customerId, byte[] commandJson);

    byte[] patchCustomerStatus(long customerId, byte[] commandJson);

    byte[] deleteCustomer(long customerId, byte[] commandJson);
}
