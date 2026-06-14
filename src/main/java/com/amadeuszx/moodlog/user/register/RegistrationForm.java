package com.amadeuszx.moodlog.user.register;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegistrationForm {

	@NotBlank(message = "Podaj adres e-mail.")
	@Email(message = "Podaj poprawny adres e-mail.")
	private String email;

	@NotBlank(message = "Podaj hasło.")
	private String password;

	private String timezone;
}
