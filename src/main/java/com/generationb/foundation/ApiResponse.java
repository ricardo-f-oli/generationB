package com.generationb.foundation;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, Meta meta) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> of(T data, Meta meta) {
        return new ApiResponse<>(data, meta);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(null, null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(int page, int size, long totalElements, Integer totalPages) {
        public static Meta of(int page, int size, long totalElements, int totalPages) {
            return new Meta(page, size, totalElements, totalPages);
        }
    }
}
