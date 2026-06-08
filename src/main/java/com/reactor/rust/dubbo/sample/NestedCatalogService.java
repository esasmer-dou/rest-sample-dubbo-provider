package com.reactor.rust.dubbo.sample;

import com.reactor.rust.dubbo.sample.dto.CatalogInfo;
import com.reactor.rust.dubbo.sample.dto.CatalogItem;

import java.util.List;
import java.util.Map;

public interface NestedCatalogService {
    byte[] getNestedCatalogJson();

    String getCatalogTitle();

    int countCatalogItems();

    CatalogInfo getCatalogInfo();

    List<CatalogItem> listFeaturedItems(int limit);

    Map<String, String> getCatalogAttributes();
}
