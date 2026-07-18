package com.carsocialmedia.backend.profile.internal.entity;

import com.carsocialmedia.backend.profile.dto.LanguageOptionDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A selectable UI language for the app (e.g. {@code en} → English, {@code ro} → Romanian).
 *
 * Reference data table ({@code app_language_options}). Read-only lookup managed by Supabase;
 * referenced by {@code profiles.app_language} (which stores the {@code id} code).
 */
@Entity
@Table(name = "app_language_options")
@Getter
@Setter
public class LanguageOptionEntity {

    @Id
    private String id;

    @Column(name = "language", nullable = false)
    private String language;

    public LanguageOptionDto toDto() {
        return new LanguageOptionDto(id, language);
    }
}
