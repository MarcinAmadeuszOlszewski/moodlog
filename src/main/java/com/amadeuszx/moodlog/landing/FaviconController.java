package com.amadeuszx.moodlog.landing;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class FaviconController {

	@GetMapping(value = "/favicon.ico", produces = "image/svg+xml")
	@ResponseBody
	public Resource favicon() {
		return new ClassPathResource("static/favicon.svg");
	}
}
