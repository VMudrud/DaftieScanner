package com.vmudrud.daftiescanner.client;

import com.vmudrud.daftiescanner.client.dto.SearchRequest;
import com.vmudrud.daftiescanner.client.dto.SearchRequest.GeoFilter;
import com.vmudrud.daftiescanner.client.dto.SearchRequest.NameValues;
import com.vmudrud.daftiescanner.client.dto.SearchRequest.PagingParam;
import com.vmudrud.daftiescanner.client.dto.SearchRequest.RangeParam;
import com.vmudrud.daftiescanner.client.dto.SearchResult;
import com.vmudrud.daftiescanner.config.dto.FilterSpec;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

@RequiredArgsConstructor
public class DaftClient {

    private static final String LISTINGS_PATH = "/api/v2/ads/listings";
    private static final String MEDIA_PARAMS = "?mediaSizes=size720x480&mediaSizes=size72x52";
    private static final String GEO_SEARCH_TYPE = "STORED_SHAPES";
    private static final String SORT = "publishDateDesc";
    private static final String AD_STATE = "published";
    private static final String FILTER_AD_STATE = "adState";
    private static final String RANGE_RENTAL_PRICE = "rentalPrice";
    private static final String RANGE_NUM_BEDS = "numBeds";
    private static final String PAGE_FROM = "0";
    private static final String PAGE_SIZE = "20";

    private final RestClient restClient;

    public SearchResult search(FilterSpec filter) {
        try {
            return doSearch(filter);
        } catch (ResourceAccessException e) {
            return doSearch(filter);
        }
    }

    private SearchResult doSearch(FilterSpec filter) {
        return restClient.post()
                .uri(LISTINGS_PATH + MEDIA_PARAMS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toRequest(filter))
                .retrieve()
                .body(SearchResult.class);
    }

    private SearchRequest toRequest(FilterSpec filter) {
        return new SearchRequest(
                filter.section(),
                List.of(new NameValues(FILTER_AD_STATE, List.of(AD_STATE))),
                List.of(),
                List.of(
                        new RangeParam(str(filter.rentalPrice().from()), str(filter.rentalPrice().to()), RANGE_RENTAL_PRICE),
                        new RangeParam(str(filter.numBeds().from()), str(filter.numBeds().to()), RANGE_NUM_BEDS)
                ),
                new PagingParam(PAGE_FROM, PAGE_SIZE),
                new GeoFilter(filter.storedShapeIds(), GEO_SEARCH_TYPE),
                StringUtils.EMPTY,
                SORT
        );
    }

    private static String str(int value) {
        return String.valueOf(value);
    }
}
