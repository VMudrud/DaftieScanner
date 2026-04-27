package com.vmudrud.daftiescanner.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResult(List<ListingWrapper> listings, PagingResult paging) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListingWrapper(ListingResult listing) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PagingResult(int totalPages, int currentPage, int totalResults) {}
}
