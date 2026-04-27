package com.vmudrud.daftiescanner.client.dto;

import java.util.List;

public record SearchRequest(
        String section,
        List<NameValues> filters,
        List<Object> andFilters,
        List<RangeParam> ranges,
        PagingParam paging,
        GeoFilter geoFilter,
        String terms,
        String sort
) {
    public record NameValues(String name, List<String> values) {}

    public record RangeParam(String from, String to, String name) {}

    public record PagingParam(String from, String pageSize) {}

    public record GeoFilter(List<String> storedShapeIds, String geoSearchType) {}
}
