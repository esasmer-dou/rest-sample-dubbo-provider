package com.reactor.sample.dubbo.provider.service;

import com.reactor.rust.dubbo.sample.CatalogJsonService;

public final class CatalogJsonServiceImpl implements CatalogJsonService {

    @Override
    public byte[] getNestedCatalogJson() {
        return CatalogPayloads.nestedCatalogJson();
    }
}
