package com.tweakdapp.backend.relationships.internal;

import com.tweakdapp.backend.relationships.RelationshipService;
import com.tweakdapp.backend.relationships.dto.FollowProfileSearchResult;
import com.tweakdapp.backend.relationships.dto.FollowStatusDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/follow")
class RelationshipController {

    private final RelationshipService relationshipService;

    RelationshipController(RelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    // ---- Follow / Unfollow / Status -----------------------------------

    @PostMapping("/{username}")
    public FollowStatusDto follow(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable String username) {
        return relationshipService.follow(jwt.getSubject(), username);
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable String username) {
        relationshipService.unfollow(jwt.getSubject(), username);
    }

    @GetMapping("/{username}/status")
    public FollowStatusDto getStatus(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable String username) {
        return relationshipService.getFollowStatus(jwt.getSubject(), username);
    }

    @DeleteMapping("/followers/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFollower(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable String username) {
        relationshipService.removeFollower(jwt.getSubject(), username);
    }

    // ---- Followers / Following lists ----------------------------------

    @GetMapping("/{username}/followers")
    public List<FollowProfileSearchResult> getFollowers(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable String username) {
        return relationshipService.getFollowers(jwt.getSubject(), username);
    }

    @GetMapping("/{username}/following")
    public List<FollowProfileSearchResult> getFollowing(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable String username) {
        return relationshipService.getFollowing(jwt.getSubject(), username);
    }
}
