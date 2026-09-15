package com.tweakdapp.backend.relationships.internal;

import com.tweakdapp.backend.relationships.BlockService;
import com.tweakdapp.backend.relationships.dto.BlockedAccountDto;
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
@RequestMapping("/api/v1/blocks")
class BlockController {

    private final BlockService blockService;

    BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @GetMapping
    public List<BlockedAccountDto> listBlocked(@AuthenticationPrincipal Jwt jwt) {
        return blockService.listBlocked(jwt.getSubject());
    }

    @PostMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) {
        blockService.block(jwt.getSubject(), username);
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) {
        blockService.unblock(jwt.getSubject(), username);
    }
}
