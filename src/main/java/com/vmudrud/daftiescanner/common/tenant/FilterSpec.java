package com.vmudrud.daftiescanner.common.tenant;

import com.vmudrud.daftiescanner.common.listing.BerRating;

import java.util.List;

public record FilterSpec(
        String section,
        Range rentalPrice,
        Range numBeds,
        List<String> storedShapeIds,
        List<BerRating> berRatings
) {
    public record Range(int from, int to) {}
}
