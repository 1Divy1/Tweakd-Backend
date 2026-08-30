/**
 * Data Transfer Objects for the Business module.
 *
 * <ul>
 *   <li>{@link com.tweakdapp.backend.business.dto.BusinessMapPinDto} — compact map marker
 *       projection; what the virtual map renders</li>
 *   <li>{@link com.tweakdapp.backend.business.dto.BusinessDto} — full business profile page</li>
 *   <li>{@link com.tweakdapp.backend.business.dto.BusinessHoursDto} — one weekday's hours</li>
 *   <li>{@link com.tweakdapp.backend.business.dto.BusinessTypeOptionDto} — business category
 *       reference data</li>
 * </ul>
 *
 * <p>Exposed as a named interface so other modules can consume these DTOs without crossing into
 * {@code business.internal}.
 */
@NamedInterface("dto")
package com.tweakdapp.backend.business.dto;

import org.springframework.modulith.NamedInterface;
