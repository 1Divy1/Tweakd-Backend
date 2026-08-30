/**
 * Profile-related exceptions exposed as a named interface so other modules
 * (e.g. {@code follow}) can throw or catch them without crossing into
 * {@code profile.internal}.
 */
@NamedInterface("exceptions")
package com.tweakdapp.backend.profile.exception;

import org.springframework.modulith.NamedInterface;
