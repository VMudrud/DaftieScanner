package com.vmudrud.daftiescanner.common.store;

import com.vmudrud.daftiescanner.common.store.dto.Cursor;

import java.util.Optional;

public interface CursorStore {
    Optional<Cursor> load(String tenantId);
    void save(String tenantId, long lastPostedAt, long lastListingId);
}
