package com.carsocialmedia.backend.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static class ProfileMissingException extends NotFoundException {
        ProfileMissingException() {
            super("Profile not found");
        }
    }

    @Test
    void apiExceptionIsMappedToItsStatusAndMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleApiException(new ProfileMissingException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Profile not found");
        assertThat(response.getBody().fieldErrors()).isNull();
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void validationFailureIsMappedTo400WithPerFieldMessages() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleValidation(validationException(
                new FieldError("request", "username", "must not be blank"),
                new FieldError("request", "bio", "too long")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().fieldErrors())
                .containsEntry("username", "must not be blank")
                .containsEntry("bio", "too long");
    }

    @Test
    void firstMessagePerFieldWinsWhenAFieldHasSeveralViolations() throws Exception {
        ResponseEntity<ErrorResponse> response = handler.handleValidation(validationException(
                new FieldError("request", "username", "must not be blank"),
                new FieldError("request", "username", "size must be between 3 and 30")));

        assertThat(response.getBody().fieldErrors())
                .containsExactlyEntriesOf(java.util.Map.of("username", "must not be blank"));
    }

    private static MethodArgumentNotValidException validationException(FieldError... errors) throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        for (FieldError error : errors) {
            bindingResult.addError(error);
        }
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("validationException", FieldError[].class), 0);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }
}
