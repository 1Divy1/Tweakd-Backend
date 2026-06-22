package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.TaggedPersonEntity;
import com.carsocialmedia.backend.posts.internal.entities.TaggedPersonId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaggedPersonRepository extends JpaRepository<TaggedPersonEntity, TaggedPersonId> {

    List<TaggedPersonEntity> findAllByIdPostId(UUID postId);

    void deleteAllByIdPostId(UUID postId);
}
