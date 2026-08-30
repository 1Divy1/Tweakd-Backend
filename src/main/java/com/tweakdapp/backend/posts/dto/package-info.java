/**
 * Posts DTOs exposed as a named interface so other modules (e.g. {@code feed}) can consume them
 * without crossing into {@code posts.internal}.
 */
@NamedInterface("dto")
package com.tweakdapp.backend.posts.dto;

import org.springframework.modulith.NamedInterface;
