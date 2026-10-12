package com.rockey.hospitality.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.rockey.hospitality.exception.ApiException.BadRequestException;

/** STUDY NOTE: We expose our own stable JSON envelope, not Spring's internal Page serialization.
 * The count and rows use the same authorized query. Clients cannot supply a sort or owner scope.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> rows) {
        return new PageResponse<>(rows.getContent(), rows.getNumber(), rows.getSize(), rows.getTotalElements(), rows.getTotalPages());
    }
    public static PageRequest request(int page, int size, Sort sort) {
        if (page < 0 || page > 1_000_000 || size < 1 || size > 100) {
            throw new BadRequestException("Page must be 0-1000000 and size must be 1-100.");
        }
        return PageRequest.of(page, size, sort);
    }
}
