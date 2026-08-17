package com.generationb.gifting.api;

import com.generationb.foundation.ApiResponse;
import com.generationb.gifting.GiftingDtos.AddressFormView;
import com.generationb.gifting.GiftingDtos.SubmitAddressCommand;
import com.generationb.gifting.internal.GiftingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * The two creator- and brand-facing pages that have no login (requirements #41 and #43).
 *
 * <p>Under {@code /api/public/**}, which the security config permits anonymously. The token in
 * the path is the only credential, so it is long, random and single-use.
 */
@RestController
@RequestMapping("/api/public/gifting")
@RequiredArgsConstructor
public class PublicGiftingController {

    private final GiftingService giftingService;

    @GetMapping("/address/{token}")
    public ApiResponse<AddressFormView> viewAddressForm(@PathVariable String token) {
        return ApiResponse.of(giftingService.viewAddressForm(token));
    }

    @PostMapping("/address/{token}")
    public ApiResponse<Void> submitAddress(@PathVariable String token,
                                           @Valid @RequestBody SubmitAddressCommand command) {
        giftingService.submitAddress(token, command);
        return ApiResponse.success();
    }

    @PostMapping("/brand-order/{token}/confirm")
    public ApiResponse<Void> confirmBrandOrder(@PathVariable String token) {
        giftingService.confirmBrandOrder(token);
        return ApiResponse.success();
    }
}
