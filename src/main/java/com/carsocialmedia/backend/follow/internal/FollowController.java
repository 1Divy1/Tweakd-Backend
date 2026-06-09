package com.carsocialmedia.backend.follow.internal;

import com.carsocialmedia.backend.follow.FollowService;
import com.carsocialmedia.backend.follow.dto.FollowProfileSearchResult;
import com.carsocialmedia.backend.follow.dto.FollowRequestDto;
import com.carsocialmedia.backend.follow.dto.FollowStatusDto;
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
class FollowController {

    private final FollowService followService;

    FollowController(FollowService followService) {
        this.followService = followService;
    }

    // ---- Follow / Unfollow / Status -----------------------------------

    @PostMapping("/{username}")
    public FollowStatusDto follow(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable String username) {
        return followService.follow(jwt.getSubject(), username);
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable String username) {
        followService.unfollow(jwt.getSubject(), username);
    }

    @GetMapping("/{username}/status")
    public FollowStatusDto getStatus(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable String username) {
        return followService.getFollowStatus(jwt.getSubject(), username);
    }

    // ---- Pending requests addressed to me -----------------------------

    @GetMapping("/requests")
    public List<FollowRequestDto> getPendingRequests(@AuthenticationPrincipal Jwt jwt) {
        return followService.getPendingRequests(jwt.getSubject());
    }

    @PostMapping("/requests/{username}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acceptRequest(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable String username) {
        followService.acceptRequest(jwt.getSubject(), username);
    }

    @DeleteMapping("/requests/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectRequest(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable String username) {
        followService.rejectRequest(jwt.getSubject(), username);
    }

    // ---- Followers / Following lists ----------------------------------

    @GetMapping("/{username}/followers")
    public List<FollowProfileSearchResult> getFollowers(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable String username) {
        return followService.getFollowers(jwt.getSubject(), username);
    }

    @GetMapping("/{username}/following")
    public List<FollowProfileSearchResult> getFollowing(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable String username) {
        return followService.getFollowing(jwt.getSubject(), username);
    }
}
