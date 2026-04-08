package com.smartqueue.dto.request;

import com.smartqueue.model.enums.TokenPriority;
import com.smartqueue.model.enums.TokenSource;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TokenRequest {

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotNull(message = "Service ID is required")
    private Long serviceId;

    private String customerName;
    private String customerPhone;
    private String customerEmail;

    @Builder.Default
    private TokenPriority priority = TokenPriority.NORMAL;

    @Builder.Default
    private TokenSource source = TokenSource.WALK_IN;

    private String notes;
}
