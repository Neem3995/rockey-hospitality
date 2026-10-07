package com.rockey.hospitality.dto.common;

/** Internal pagination arguments; public query parameters remain unchanged. */
/**
 * Immutable paging inputs shared by search services.
 * Page is zero-based; each service validates limits and its own sort allowlist.
 */
public record PageCriteria(
        /**
         * Zero-based page number requested or returned.
         */
        int page,
        /**
         * Requested or returned page size, validated by the search service.
         */
        int size,
        /**
         * Optional field,direction input checked against the service's explicit allowlist.
         */
        String sort) { }
