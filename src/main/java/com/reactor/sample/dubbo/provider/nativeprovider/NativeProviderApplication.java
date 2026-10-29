package com.reactor.sample.dubbo.provider.nativeprovider;

import com.reactor.rust.dubbo.config.DubboApplicationProperties;
import com.reactor.rust.dubbo.provider.NativeProviderFactory;
import com.reactor.sample.dubbo.provider.service.CatalogJsonServiceImpl;
import com.reactor.sample.dubbo.provider.service.StreamCatalogServiceImpl;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/** Dedicated process owner for the one-shot shared native registry, not a second network stack. */
public final class NativeProviderApplication {

    private NativeProviderApplication() {}

    public static void main(String[] args) throws InterruptedException {
        var configuration = NativeProviderConfiguration.from(
                DubboApplicationProperties.load("native-provider.properties"));
        String nativePath = System.getProperty("reactor.dubbo.native.path");
        if (nativePath == null || nativePath.isBlank()) {
            throw new IllegalArgumentException("Select the accepted ABI-3 library explicitly with -Dreactor.dubbo.native.path");
        }
        var stopped = new CountDownLatch(1);
        try (var provider = NativeProviderFactory.start(configuration.options(), List.of(
                CatalogNativeDefinition__NativeProvider.bind(new CatalogJsonServiceImpl(), configuration.execution()),
                StreamCatalogNativeDefinition__NativeProvider.bind(new StreamCatalogServiceImpl(), configuration.execution())), null)) {
            Thread hook = new Thread(() -> {
                try { provider.close(); }
                finally { stopped.countDown(); }
            }, "sample-native-provider-shutdown");
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                System.out.println("Native catalog provider ready: port=" + configuration.options().port()
                        + ", engine=shared-ABI3, registry=false, services=CatalogJsonService,StreamCatalogService");
                stopped.await();
            } finally {
                try { Runtime.getRuntime().removeShutdownHook(hook); }
                catch (IllegalStateException shuttingDown) { stopped.await(); }
            }
        }
    }
}
