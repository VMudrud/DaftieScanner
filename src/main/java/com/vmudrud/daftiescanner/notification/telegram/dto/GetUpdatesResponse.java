package com.vmudrud.daftiescanner.notification.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GetUpdatesResponse(boolean ok, List<TelegramUpdate> result) {}
