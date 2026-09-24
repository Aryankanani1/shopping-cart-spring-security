package com.aryan.spring_security_demo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Body of {@code POST /orders}: the shipping address captured at checkout. The
 * cart contents are read server-side from the user's cart, so the request only
 * carries the delivery details. {@code addressLine2} is optional; everything else
 * is required, mirrored on the frontend checkout form.
 */
@Data
public class PlaceOrderRequest {

    @NotBlank(message = "Recipient name is required")
    @Size(max = 255)
    private String recipientName;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 255)
    private String city;

    @NotBlank(message = "State/region is required")
    @Size(max = 255)
    private String state;

    @NotBlank(message = "Postal code is required")
    @Size(max = 255)
    private String postalCode;

    @NotBlank(message = "Country is required")
    @Size(max = 255)
    private String country;
}
