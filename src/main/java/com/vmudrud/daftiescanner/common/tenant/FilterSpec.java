package com.vmudrud.daftiescanner.common.tenant;

import java.util.List;

public record FilterSpec(
        String section,
        Range rentalPrice,
        Range numBeds,
        List<String> storedShapeIds
) {
    public record Range(int from, int to) {}
}
