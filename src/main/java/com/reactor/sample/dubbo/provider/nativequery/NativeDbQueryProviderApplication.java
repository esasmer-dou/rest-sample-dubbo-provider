package com.reactor.sample.dubbo.provider.nativequery;

import com.reactor.rust.dubbo.config.DubboApplicationProperties;
import com.reactor.rust.dubbo.provider.NativeProviderFactory;
import com.reactor.sample.dubbo.provider.db.PostgresCustomerRepository;
import com.reactor.sample.dubbo.provider.nativeprovider.NativeProviderConfiguration;
import com.reactor.sample.dubbo.provider.service.CustomerQueryServiceImpl;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Same Java query contract and database pool; generated Hessian transport replaces the official engine. */
public final class NativeDbQueryProviderApplication {
    private NativeDbQueryProviderApplication() {}

    public static void main(String[] args) throws InterruptedException {
        var properties = DubboApplicationProperties.load("native-db-query-provider.properties");
        var configuration = NativeProviderConfiguration.from(properties);
        var stopped = new CountDownLatch(1);
        try (var repository = PostgresCustomerRepository.fromProperties(properties)) {
            var service = new CustomerQueryServiceImpl(repository);
            service.warmupDatabase();
            try (var provider = NativeProviderFactory.start(configuration.options(), List.of(
                    CustomerQueryNativeDefinition__NativeProvider.bind(service, configuration.execution())), null)) {
                Thread hook = new Thread(() -> {
                    try { provider.close(); }
                    finally { stopped.countDown(); }
                }, "sample-native-query-shutdown");
                Runtime.getRuntime().addShutdownHook(hook);
                try {
                    System.out.println("Native database query provider ready: port=" + configuration.options().port()
                            + ", engine=shared-ABI3, registry=false, service=CustomerQueryService");
                    stopped.await();
                } finally {
                    try { Runtime.getRuntime().removeShutdownHook(hook); }
                    catch (IllegalStateException shuttingDown) { stopped.await(); }
                }
            }
        }
    }
}
