package com.vmudrud.daftiescanner.notification.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GetUpdatesRequest(Long offset, int timeout, @JsonProperty("allowed_updates") List<String> allowedUpdates) {
}
