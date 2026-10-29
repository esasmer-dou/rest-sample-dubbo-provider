package com.reactor.sample.dubbo.provider.nativeprovider;

import com.reactor.rust.dubbo.provider.GenerateNativeProvider;
import com.reactor.rust.dubbo.sample.StreamCatalogService;

@GenerateNativeProvider(service = StreamCatalogService.class, group = "", version = "0.0.0")
final class StreamCatalogNativeDefinition {
}
