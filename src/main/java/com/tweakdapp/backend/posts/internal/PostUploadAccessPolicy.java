package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.posts.exception.NotPostOwnerException;
import com.tweakdapp.backend.posts.exception.PostNotFoundException;
import com.tweakdapp.backend.posts.internal.entities.PostEntity;
import com.tweakdapp.backend.posts.internal.repositories.PostRepository;
import com.tweakdapp.backend.storage.UploadAccessPolicy;
import com.tweakdapp.backend.storage.UploadTarget;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Only a post's author may upload its images. */
@Component
class PostUploadAccessPolicy implements UploadAccessPolicy {

    private final PostRepository postRepository;

    PostUploadAccessPolicy(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    public UploadTarget target() {
        return UploadTarget.POST;
    }

    @Override
    public void requireUploadAccess(UUID userId, UUID postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (!post.getUserId().equals(userId)) {
            throw new NotPostOwnerException();
        }
    }
}
