package vn.hutech.fooddelivery.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body cho POST /api/merchant/reviews/{id}/reply */
public record CreateReplyRequest(
        @NotBlank @Size(max = 1000) String content
) {}
