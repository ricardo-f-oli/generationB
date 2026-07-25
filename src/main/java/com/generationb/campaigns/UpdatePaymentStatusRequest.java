package com.generationb.campaigns;

import jakarta.validation.constraints.NotNull;

public record UpdatePaymentStatusRequest(
    @NotNull(message = "Payment status is required")
    PaymentStatus status
) {}
