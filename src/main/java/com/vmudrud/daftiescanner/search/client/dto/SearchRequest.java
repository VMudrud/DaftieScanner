package com.vmudrud.daftiescanner.search.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GeoFilter(List<String> storedShapeIds, String geoSearchType) {}
}
