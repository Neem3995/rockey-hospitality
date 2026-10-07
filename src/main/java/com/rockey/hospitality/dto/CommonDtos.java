package com.rockey.hospitality.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * STUDY NOTE: DTO means Data Transfer Object: a plain Java type that carries request, response or internal
 * input data.
 * This container groups shared pagination inputs, paged responses and safe HTTP errors.
 * Controllers and services use these types to separate the API contract from JPA entities and avoid
 * exposing database objects directly.
 * Static nested types need no container instance; validation/JSON annotations describe data, not access
 * permissions.
 */
public final class CommonDtos {

    // A record is a compact Java type with fixed named values; these criteria carry internal filters/paging,
    // not new public API operations.
    // @JsonInclude controls which empty/null properties are omitted from JSON; it does not grant access.

    // Namespace only; callers construct the nested types instead.
    private CommonDtos() { }

/**
     * Immutable paging inputs shared by search services.
     * Page is zero-based; each service validates limits and its own sort allowlist.
     */
    public static record PageCriteria(
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

    /**
     * Generic response wrapper for one selected page and its totals.
     * It uses zero-based page numbering and safe DTO content rather than exposing repository Page internals.
     */
    public static class PagedResponse<T> {

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

    /**
     * Safe shared JSON error shape for controller and security failures.
     * Field-validation messages are included when useful, without stack traces or credential data.
     */
    // Omits null/empty properties such as absent fieldErrors from serialized errors.
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ApiError {

        /**
         * Server-local time at which this error response was constructed.
         */
        private final LocalDateTime timestamp;
        /**
         * Numeric HTTP error status, not a domain lifecycle enum.
         */
        private final int status;
        /**
         * HTTP status reason label corresponding to the error's numeric status.
         */
        private final String error;
        /**
         * Safe client-facing explanatory text without private authentication or database details.
         */
        private final String message;
        /**
         * Request URI associated with this safe error response.
         */
        private final String path;
        /**
         * Optional first validation message per rejected request field.
         */
        private final Map<String, String> fieldErrors;

        /**
         * Packages safe HTTP error metadata and optional field messages for JSON serialization.
         */
        public ApiError(
                LocalDateTime timestamp,
                int status,
                String error,
                String message,
                String path,
                Map<String, String> fieldErrors
        ) {
            this.timestamp = timestamp;
            this.status = status;
            this.error = error;
            this.message = message;
            this.path = path;
            this.fieldErrors = fieldErrors;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public int getStatus() {
            return status;
        }

        public String getError() {
            return error;
        }

        public String getMessage() {
            return message;
        }

        public String getPath() {
            return path;
        }

        public Map<String, String> getFieldErrors() {
            return fieldErrors;
        }
    }
}
