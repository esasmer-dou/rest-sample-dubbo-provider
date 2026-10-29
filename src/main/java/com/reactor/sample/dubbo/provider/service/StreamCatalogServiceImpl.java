package com.reactor.sample.dubbo.provider.service;

import com.reactor.rust.dubbo.sample.StreamCatalogService;
import com.reactor.rust.dubbo.sample.dto.StreamCatalogDetails;
import com.reactor.rust.dubbo.sample.dto.StreamCatalogRow;
import java.util.ArrayList;
import java.util.List;

/** Identical, bounded, no-DB business implementation for official/native engine comparisons. */
public final class StreamCatalogServiceImpl implements StreamCatalogService {

    public static final int MAX_ROWS = 10_000;
    private static final int PRECOMPUTED_ROWS = 512;
    private static final List<StreamCatalogRow> ROWS = createRows();
    private static final List<StreamCatalogRow> SMALL = ROWS.subList(0, 32);

    @Override
    public List<StreamCatalogRow> read(int count) {
        if (count < 0 || count > MAX_ROWS) {
            throw new IllegalArgumentException("count must be between 0 and " + MAX_ROWS);
        }
        if (count == 0) return List.of();
        if (count == 32) return SMALL;
        if (count == PRECOMPUTED_ROWS) return ROWS;
        if (count < PRECOMPUTED_ROWS) return ROWS.subList(0, count);
        var rows = new ArrayList<StreamCatalogRow>(count);
        rows.addAll(ROWS);
        for (int index = PRECOMPUTED_ROWS; index < count; index++) rows.add(createRow(index));
        return List.copyOf(rows);
    }

    private static List<StreamCatalogRow> createRows() {
        var rows = new ArrayList<StreamCatalogRow>(PRECOMPUTED_ROWS);
        for (int index = 0; index < PRECOMPUTED_ROWS; index++) rows.add(createRow(index));
        return List.copyOf(rows);
    }

    private static StreamCatalogRow createRow(int index) {
        double amount = switch (index % 6) {
            case 0 -> 0.1d;
            case 1 -> -0.0d;
            case 2 -> 1.2345678901234567d;
            case 3 -> Double.MIN_VALUE;
            case 4 -> Double.MAX_VALUE;
            default -> -1234.567d;
        };
        var details = new StreamCatalogDetails(
                index % 2 == 0 ? "\u0130stanbul" : "\u015eanl\u0131urfa",
                List.of("T\u00fcrk\u00e7e", "\ud83d\ude80", "quote\" slash\\ line\n"),
                index % 2 == 0 ? null : "\u00e7\u011f\u0131\u00f6\u015f\u00fc \u0130\u011e\u00d6\u015e\u00dc");
        return new StreamCatalogRow(index + 1L, "\u00dcr\u00fcn \u0130pek " + index,
                amount, index % 2 == 0, details);
    }
}
