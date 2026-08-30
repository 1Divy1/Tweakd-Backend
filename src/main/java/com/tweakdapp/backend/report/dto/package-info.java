/**
 * Report DTOs exposed as a named interface so the {@code posts} and {@code profile} modules — which
 * own the report endpoints — can consume the request/reason records without crossing into
 * {@code report.internal}.
 */
@NamedInterface("dto")
package com.tweakdapp.backend.report.dto;

import org.springframework.modulith.NamedInterface;
