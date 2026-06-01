package com.amadeuszx.moodlog;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = JournalEntryContentValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface JournalEntryContent {

	String message() default "Nieprawidłowy wpis.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
