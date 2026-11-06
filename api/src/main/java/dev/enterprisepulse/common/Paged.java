package dev.enterprisepulse.common;

import java.util.List;

public record Paged<T>(List<T> data, PageMeta meta) {
    public record PageMeta(int page, int pageSize, long total, int totalPages) {}

    public static <T> Paged<T> of(List<T> data, long total, int page, int pageSize) {
        int pages = pageSize == 0 ? 0 : (int) Math.ceil(total / (double) pageSize);
        return new Paged<>(data, new PageMeta(page, pageSize, total, pages));
    }
}
