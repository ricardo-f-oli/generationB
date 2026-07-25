package com.generationb.foundation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int status,
    String error,
    String message,
    String timestamp,
    String path,
    List<FieldErrorDetail> details
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldErrorDetail(String field, String message) {}
}
