package com.aryan.spring_security_demo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class SpringSecurityDemoApplicationTests {

	@Test
	void contextLoads() {
	}

}
