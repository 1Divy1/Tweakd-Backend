package com.tweakdapp.backend.garage.internal.repositories;

import com.tweakdapp.backend.garage.internal.entities.CarEntity;
import com.tweakdapp.backend.testsupport.AbstractPostgresIT;
import jakarta.persistence.EntityManager;
import org.hibernate.TransientPropertyValueException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code GarageService.deleteCar} reads the R2 keys of a car's gallery and mod media before
 * removing the car, then lets the database cascade clean up the child rows. Against real Hibernate
 * that only works if the keys are read as plain strings: loading the rows as entities leaves managed
 * children pointing at a removed car, and the flush fails — which reached the app as a 500.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext
class CarDeletionRepositoryIT extends AbstractPostgresIT {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Autowired private CarRepository carRepository;
    @Autowired private CarGalleryRepository carGalleryRepository;
    @Autowired private CarModificationGalleryRepository modificationGalleryRepository;
    @Autowired private CarModificationRepository modificationRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc;
    private UUID carId;
    private UUID modId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from public.cars");
        jdbc.update("delete from public.garages");
        jdbc.update("insert into auth.users (id) values (?) on conflict do nothing", OWNER);
        jdbc.update("insert into public.profiles (id, username) values (?, 'racer_d1') on conflict do nothing", OWNER);
        jdbc.update("insert into public.car_mod_categories (id, mod_name) values ('test_mod', 'Test Mod') on conflict do nothing");

        // Creating the profile may already have created its garage.
        UUID garageId = jdbc.query("select id from public.garages where owner_id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, OWNER);
        if (garageId == null) {
            garageId = UUID.randomUUID();
            jdbc.update("insert into public.garages (id, owner_id) values (?, ?)", garageId, OWNER);
        }
        carId = UUID.randomUUID();
        jdbc.update("""
                insert into public.cars (id, garage_id, brand_id, model_id, drivetrain_id, year,
                                         horsepower, torque, weight, engine_displacement, color_id,
                                         mileage_unit_id, status_id, fuel_id, cover_image_url)
                values (?, ?, '00000000-0000-0000-0000-0000000b0001', '00000000-0000-0000-0000-0000000d0001',
                        'test_dt', 2022, 635, 750, 1900, 4.4, 'test_color', 'test_km', 'test_status', 'test_fuel',
                        'cars/' || ?::text || '/cover.webp')
                """, carId, garageId, carId);
        jdbc.update("insert into public.car_gallery (car_id, url, position) values (?, ?, 0)",
                carId, "cars/" + carId + "/gallery/1.webp");
        modId = UUID.randomUUID();
        jdbc.update("""
                insert into public.car_modifications (id, car_id, category_id, title, installation_date)
                values (?, ?, 'test_mod', 'Coilovers', now())
                """, modId, carId);
        jdbc.update("insert into public.car_modification_gallery (mod_id, url, type, phase) values (?, ?, 'image', 'after')",
                modId, "cars/" + carId + "/mods/" + modId + "/1.webp");
    }

    @Test
    void readingKeysOnlyLetsTheCarDeleteAndCascade() {
        CarEntity car = carRepository.findById(carId).orElseThrow();

        assertThat(carGalleryRepository.findKeysByCarId(carId))
                .containsExactly("cars/" + carId + "/gallery/1.webp");
        assertThat(modificationGalleryRepository.findKeysByCarId(carId))
                .containsExactly("cars/" + carId + "/mods/" + modId + "/1.webp");
        assertThat(modificationRepository.findIdsByCarId(carId)).containsExactly(modId);

        carRepository.delete(car);
        entityManager.flush();

        assertThat(count("cars", "id", carId)).isZero();
        assertThat(count("car_gallery", "car_id", carId)).isZero();
        assertThat(count("car_modifications", "car_id", carId)).isZero();
        assertThat(count("car_modification_gallery", "mod_id", modId)).isZero();
    }

    /** The trap the key-only queries exist to avoid — pinned so nobody "simplifies" back into it. */
    @Test
    void loadingGalleryEntitiesBeforeTheDeleteFailsTheFlush() {
        CarEntity car = carRepository.findById(carId).orElseThrow();
        carGalleryRepository.findAllByCarIdOrderByPositionAsc(carId);

        carRepository.delete(car);

        assertThatThrownBy(entityManager::flush)
                .hasRootCauseInstanceOf(TransientPropertyValueException.class);
    }

    private int count(String table, String column, UUID id) {
        return jdbc.queryForObject(
                "select count(*) from public." + table + " where " + column + " = ?", Integer.class, id);
    }
}
