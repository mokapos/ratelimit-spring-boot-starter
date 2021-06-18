package com.github.sajjaadalipour.ratelimit.exception;

/**
 * should be thrown when the {@link #field} not presented in
 * the Http servlet request body while generating the key.
 *
 * @author Sajjad Alipour
 */
public class FieldNotPresentedException extends RuntimeException {

    /**
     * The header key.
     */
    private final String field;

    public FieldNotPresentedException(String field, String message) {
        super(message);
        this.field = field;
    }
}
