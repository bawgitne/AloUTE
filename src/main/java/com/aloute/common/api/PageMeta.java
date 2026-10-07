package com.aloute.common.api;

import org.springframework.data.domain.Page;

public record PageMeta(int page, int size, long totalElements, int totalPages,
                       boolean hasNext, boolean hasPrevious) {
    public static PageMeta from(Page<?> source) {
        return new PageMeta(source.getNumber(), source.getSize(), source.getTotalElements(),
                source.getTotalPages(), source.hasNext(), source.hasPrevious());
    }
    public static PageMeta fromPage(Page<?> source) {
        return from(source);
    }
}
