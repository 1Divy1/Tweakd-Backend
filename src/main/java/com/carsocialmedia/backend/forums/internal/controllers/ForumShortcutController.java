package com.carsocialmedia.backend.forums.internal.controllers;

import com.carsocialmedia.backend.forums.ForumsService;
import com.carsocialmedia.backend.forums.dto.ShortcutDto;
import com.carsocialmedia.backend.forums.dto.request.CreateShortcutRequest;
import com.carsocialmedia.backend.forums.dto.request.ReorderShortcutsRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateShortcutRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A user's forum shortcuts (saved filters). Every operation is owner-scoped to the JWT subject in
 * the service layer.
 */
@RestController
@RequestMapping("/api/v1/forums/shortcuts")
public class ForumShortcutController {

    private final ForumsService forumsService;

    public ForumShortcutController(ForumsService forumsService) {
        this.forumsService = forumsService;
    }

    /** The caller's shortcuts, in their pinned order. */
    @GetMapping
    public List<ShortcutDto> listShortcuts(@AuthenticationPrincipal Jwt jwt) {
        return forumsService.listShortcuts(jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShortcutDto createShortcut(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody CreateShortcutRequest request) {
        return forumsService.createShortcut(jwt.getSubject(), request);
    }

    /**
     * Reorders the caller's shortcuts to match the given id order. Declared before the
     * {@code /{shortcutId}} mapping so {@code reorder} is not captured as a path variable.
     */
    @PatchMapping("/reorder")
    public List<ShortcutDto> reorderShortcuts(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody ReorderShortcutsRequest request) {
        return forumsService.reorderShortcuts(jwt.getSubject(), request);
    }

    @PatchMapping("/{shortcutId}")
    public ShortcutDto updateShortcut(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID shortcutId,
                                      @Valid @RequestBody UpdateShortcutRequest request) {
        return forumsService.updateShortcut(jwt.getSubject(), shortcutId, request);
    }

    @DeleteMapping("/{shortcutId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteShortcut(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID shortcutId) {
        forumsService.deleteShortcut(jwt.getSubject(), shortcutId);
    }
}
