package com.vmudrud.daftiescanner.search.client;

import com.vmudrud.daftiescanner.search.client.dto.SearchRequest;
import com.vmudrud.daftiescanner.search.client.dto.SearchRequest.GeoFilter;
import com.vmudrud.daftiescanner.search.client.dto.SearchRequest.NameValues;
import com.vmudrud.daftiescanner.search.client.dto.SearchRequest.PagingParam;
import com.vmudrud.daftiescanner.search.client.dto.SearchRequest.RangeParam;
import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.listing.SearchResult;
import com.vmudrud.daftiescanner.common.tenant.FilterSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class DaftClient {

    private static final String GEO_SEARCH_TYPE = "STORED_SHAPES";
    private static final String SORT = "publishDateDesc";
    private static final String AD_STATE = "published";
    private static final String FILTER_AD_STATE = "adState";
    private static final String RANGE_RENTAL_PRICE = "rentalPrice";
    private static final String RANGE_NUM_BEDS = "numBeds";
    private static final String PAGE_FROM = "0";
    private static final String PAGE_SIZE = "20";
    private static final String MEDIA_SIZE_PARAM = "mediaSizes=size720x480&mediaSizes=size72x52";

    // Pre-built absolute URI — Spring's RestClient.uri(URI) uses it as-is, bypassing
    // UriComponentsBuilder which would strip the leading empty query param ("?&...").
    // Daft's gateway appears to require this exact format.
    private static final URI LISTINGS_URI = URI.create(
            "https://gateway.daft.ie/api/v2/ads/listings?&" + MEDIA_SIZE_PARAM);

    private final RestClient restClient;

    @Retryable(retryFor = ResourceAccessException.class, maxAttempts = 2, backoff = @Backoff(500))
    public SearchResult search(FilterSpec filter) {
        SearchResult result = restClient.post()
                .uri(LISTINGS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toRequest(filter))
                .retrieve()
                .body(SearchResult.class);
        logListings(result);
        return result;
    }

    // Toggle via: logging.level.com.vmudrud.daftiescanner.search.client.DaftClient: DEBUG
    private static void logListings(SearchResult result) {
        if (!log.isDebugEnabled() || result == null || result.listings() == null) {
            return;
        }
        result.listings().forEach(wrapper -> {
            ListingResult l = wrapper.listing();
            log.debug("listing id={} title=\"{}\" price=\"{}\" path={}",
                    l.id(), l.title(), l.price(), l.seoFriendlyPath());
        });
    }

    private SearchRequest toRequest(FilterSpec filter) {
        return new SearchRequest(
                filter.section(),
                List.of(new NameValues(FILTER_AD_STATE, List.of(AD_STATE))),
                List.of(),
                toRanges(filter),
                new PagingParam(PAGE_FROM, PAGE_SIZE),
                toGeoFilter(filter.storedShapeIds()),
                StringUtils.EMPTY,
                SORT
        );
    }

    private static List<RangeParam> toRanges(FilterSpec filter) {
        var ranges = new ArrayList<RangeParam>(2);
        ranges.add(rangeParam(filter.rentalPrice(), RANGE_RENTAL_PRICE));
        if (filter.numBeds().isFullyBound()) {
            ranges.add(rangeParam(filter.numBeds(), RANGE_NUM_BEDS));
        }
        return ranges;
    }

    private static RangeParam rangeParam(FilterSpec.Range range, String name) {
        return new RangeParam(String.valueOf(range.from()), String.valueOf(range.to()), name);
    }

    private static GeoFilter toGeoFilter(List<String> storedShapeIds) {
        var cleaned = storedShapeIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .toList();
        return cleaned.isEmpty()
                ? new GeoFilter(List.of(), null)
                : new GeoFilter(cleaned, GEO_SEARCH_TYPE);
    }
}
