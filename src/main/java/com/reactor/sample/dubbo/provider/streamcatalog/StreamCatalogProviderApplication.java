package com.reactor.sample.dubbo.provider.streamcatalog;

import com.reactor.rust.dubbo.config.DubboApplicationProperties;
import com.reactor.rust.dubbo.provider.DubboProviderApplication;
import com.reactor.rust.dubbo.sample.CatalogJsonService;
import com.reactor.rust.dubbo.sample.StreamCatalogService;
import com.reactor.sample.dubbo.provider.service.CatalogJsonServiceImpl;
import com.reactor.sample.dubbo.provider.service.StreamCatalogServiceImpl;

import static com.reactor.rust.dubbo.provider.DubboServiceBinding.service;

/** Explicit official-engine baseline; never selected by existing sample entrypoints. */
public final class StreamCatalogProviderApplication {

    private StreamCatalogProviderApplication() {}

    public static void main(String[] args) throws Exception {
        var properties = DubboApplicationProperties.load("stream-catalog-provider.properties");
        if (!"official".equals(properties.get("sample.provider.engine"))
                || properties.getBoolean("reactor.dubbo.registry-enabled", false)) {
            throw new IllegalArgumentException("Stream catalog baseline requires engine=official and registry-enabled=false");
        }
        DubboProviderApplication.run(properties, "stream-catalog-official", context -> context.services(
                service(CatalogJsonService.class, new CatalogJsonServiceImpl()),
                service(StreamCatalogService.class, new StreamCatalogServiceImpl())));
    }
}
