package vn.hutech.fooddelivery.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body cho POST /api/orders/{id}/review
 * restaurantRating bắt buộc (1-5); driverRating tuỳ chọn (1-5 hoặc null).
 */
public record CreateReviewRequest(
        @NotNull @Min(1) @Max(5) Integer restaurantRating,
        @Min(1) @Max(5) Integer driverRating,
        @Size(max = 2000) String comment
) {}
