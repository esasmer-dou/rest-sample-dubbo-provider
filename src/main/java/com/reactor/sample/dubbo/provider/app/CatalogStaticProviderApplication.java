package com.reactor.sample.dubbo.provider.app;

import com.reactor.rust.dubbo.sample.CatalogJsonService;
import com.reactor.rust.dubbo.provider.DubboProviderSupport;
import com.reactor.rust.dubbo.provider.PlainDubboProvider;
import com.reactor.sample.dubbo.provider.config.ProviderRuntimeTuning;
import com.reactor.sample.dubbo.provider.config.ProviderProperties;
import com.reactor.sample.dubbo.provider.service.CatalogJsonServiceImpl;

import java.util.List;

public final class CatalogStaticProviderApplication {

    private CatalogStaticProviderApplication() {}

    public static void main(String[] args) throws Exception {
        ProviderRuntimeTuning.apply();

        DubboProviderSupport support = DubboProviderSupport.fromProperties(ProviderProperties.asProperties());
        PlainDubboProvider.ProviderConfig config = support.providerConfig(false);
        List<DubboProviderSupport.ServicePlan<?>> services = List.of(
                support.service(CatalogJsonService.class, new CatalogJsonServiceImpl()));
        List<DubboProviderSupport.ExportedService<?>> exported = support.exportAll(config, null, services);

        support.logStartup("catalog-static", config, false, exported);
        support.awaitShutdown("sample-catalog-static-provider-shutdown", exported);
    }
}
