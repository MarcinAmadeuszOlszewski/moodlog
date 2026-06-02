package com.amadeuszx.moodlog.user.register;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class RegistrationForm {

	@NotBlank(message = "Podaj adres e-mail.")
	@Email(message = "Podaj poprawny adres e-mail.")
	private String email;

	@NotBlank(message = "Podaj hasło.")
	private String password;

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
