package com.vmudrud.daftiescanner.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ListingResult(
        long id,
        String title,
        long publishDate,
        String price,
        String numBedrooms,
        String numBathrooms,
        String propertyType,
        String seoFriendlyPath,
        String state,
        SellerInfo seller,
        MediaInfo media,
        BerInfo ber,
        PointInfo point,
        List<FacilityInfo> facilities,
        Integer primaryAreaId
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SellerInfo(String name, String branch, String sellerType, boolean showContactForm) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaInfo(List<ImageInfo> images) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageInfo(String size720x480, String size72x52) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BerInfo(String rating) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PointInfo(List<Double> coordinates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FacilityInfo(String key, String name) {}
}
