package com.slotmate.common.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** API 명세 3절 목록 응답 — items + page (번호는 0부터) */
public record PageResponse<T>(List<T> items, PageInfo page) {

    public record PageInfo(int number, int size, long totalElements, int totalPages) {
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                new PageInfo(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }
}
