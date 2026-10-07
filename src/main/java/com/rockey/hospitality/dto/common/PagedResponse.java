package com.rockey.hospitality.dto.common;

import java.util.List;

/**
 * Generic response wrapper for one selected page and its totals.
 * It uses zero-based page numbering and safe DTO content rather than exposing repository Page internals.
 */
public class PagedResponse<T> {

    /**
     * DTO items on this page, not every matching row in the database.
     */
    private final List<T> content;
    /**
     * Zero-based page number requested or returned.
     */
    private final int page;
    /**
     * Requested or returned page size, validated by the search service.
     */
    private final int size;
    /**
     * Total number of matching rows across all pages.
     */
    private final long totalElements;
    /**
     * Number of pages for the matching row count and selected page size.
     */
    private final int totalPages;
    /**
     * Whether this page is the final page of the selected result set.
     */
    private final boolean last;

    /**
     * Packages the listed response fields supplied by the service without serializing a persistence entity.
     */
    public PagedResponse(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean last
    ) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.last = last;
    }

    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isLast() {
        return last;
    }
}
