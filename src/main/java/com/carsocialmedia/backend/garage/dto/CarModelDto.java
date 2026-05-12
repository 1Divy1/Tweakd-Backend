package com.carsocialmedia.backend.garage.dto;

import java.util.UUID;

public record CarModelDto(UUID id, UUID brandId, String model) {}