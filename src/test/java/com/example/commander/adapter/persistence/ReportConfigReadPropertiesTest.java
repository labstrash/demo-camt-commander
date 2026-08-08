package com.example.commander.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReportConfigReadProperties}'s {@code @Positive} constraints.
 *
 * <p>Uses a plain {@link Validator} rather than a Spring context, since binding and
 * constraint enforcement here don't depend on anything Spring-specific.
 */
class ReportConfigReadPropertiesTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void defaultsAreValid() {
        ReportConfigReadProperties properties = new ReportConfigReadProperties();

        assertThat(validator.validate(properties)).isEmpty();
        assertThat(properties.getPageSize()).isEqualTo(500);
        assertThat(properties.getStagedQueryTimeoutSeconds()).isEqualTo(10);
        assertThat(properties.getTvpQueryTimeoutSeconds()).isEqualTo(15);
    }

    @Test
    void positiveValuesAreValid() {
        ReportConfigReadProperties properties = new ReportConfigReadProperties();
        properties.setPageSize(100);
        properties.setStagedQueryTimeoutSeconds(5);
        properties.setTvpQueryTimeoutSeconds(20);

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void zeroPageSizeIsRejected() {
        ReportConfigReadProperties properties = new ReportConfigReadProperties();
        properties.setPageSize(0);

        Set<ConstraintViolation<ReportConfigReadProperties>> violations = validator.validate(properties);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("pageSize");
    }

    @Test
    void negativePageSizeIsRejected() {
        ReportConfigReadProperties properties = new ReportConfigReadProperties();
        properties.setPageSize(-1);

        Set<ConstraintViolation<ReportConfigReadProperties>> violations = validator.validate(properties);

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("pageSize");
    }

    @Test
    void zeroStagedQueryTimeoutSecondsIsRejected() {
        ReportConfigReadProperties properties = new ReportConfigReadProperties();
        properties.setStagedQueryTimeoutSeconds(0);

        Set<ConstraintViolation<ReportConfigReadProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("stagedQueryTimeoutSeconds");
    }

    @Test
    void negativeStagedQueryTimeoutSecondsIsRejected() {
        ReportConfigReadProperties properties = new ReportConfigReadProperties();
        properties.setStagedQueryTimeoutSeconds(-5);

        Set<ConstraintViolation<ReportConfigReadProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("stagedQueryTimeoutSeconds");
    }

    @Test
    void zeroTvpQueryTimeoutSecondsIsRejected() {
        ReportConfigReadProperties properties = new ReportConfigReadProperties();
        properties.setTvpQueryTimeoutSeconds(0);

        Set<ConstraintViolation<ReportConfigReadProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("tvpQueryTimeoutSeconds");
    }

    @Test
    void negativeTvpQueryTimeoutSecondsIsRejected() {
        ReportConfigReadProperties properties = new ReportConfigReadProperties();
        properties.setTvpQueryTimeoutSeconds(-15);

        Set<ConstraintViolation<ReportConfigReadProperties>> violations = validator.validate(properties);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("tvpQueryTimeoutSeconds");
    }
}
