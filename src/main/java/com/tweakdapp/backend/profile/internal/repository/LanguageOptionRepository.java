package com.tweakdapp.backend.profile.internal.repository;

import com.tweakdapp.backend.profile.internal.entity.LanguageOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Read-only reference data for {@code app_language_options}. {@code existsById} (inherited) is
 * used to validate a language code before writing it to {@code profiles.app_language}.
 */
public interface LanguageOptionRepository extends JpaRepository<LanguageOptionEntity, String> {
    List<LanguageOptionEntity> findAllByOrderByLanguageAsc();
}
