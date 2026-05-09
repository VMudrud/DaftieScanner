package com.vmudrud.daftiescanner.common.listing;

import java.util.Map;
import java.util.Optional;

public enum BerRating {
    A1, A2, A3,
    B1, B2, B3,
    C1, C2, C3,
    D1, D2,
    E1, E2,
    F, G,
    EXEMPT;

    private static final Map<String, BerRating> BY_CODE;

    static {
        var map = new java.util.HashMap<String, BerRating>();
        for (BerRating r : values()) {
            map.put(r.name(), r);
        }
        map.put("SI_666", EXEMPT);
        BY_CODE = Map.copyOf(map);
    }

    public static Optional<BerRating> fromCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return Optional.ofNullable(BY_CODE.get(code.toUpperCase()));
    }
}
