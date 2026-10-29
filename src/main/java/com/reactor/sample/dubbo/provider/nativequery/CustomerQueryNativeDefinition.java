package com.reactor.sample.dubbo.provider.nativequery;

import com.reactor.rust.dubbo.provider.GenerateNativeProvider;
import com.reactor.rust.dubbo.sample.CustomerQueryService;

@GenerateNativeProvider(service = CustomerQueryService.class, group = "", version = "0.0.0")
final class CustomerQueryNativeDefinition {}
