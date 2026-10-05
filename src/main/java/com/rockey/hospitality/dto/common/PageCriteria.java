package com.rockey.hospitality.dto.common;

/** Internal pagination arguments; public query parameters remain unchanged. */
public record PageCriteria(int page, int size, String sort) { }
