package com.reactor.sample.dubbo.provider.dubbo;

import org.apache.dubbo.common.URL;

public interface ProviderRegistration extends AutoCloseable {

    void register(Class<?> serviceType, URL providerUrl) throws Exception;

    @Override
    void close() throws Exception;
}
