package com.vitorbetmann.hospitapi.notification;

import org.springframework.boot.SpringApplication;

public class TestHospitapiNotificationApplication {

	public static void main(String[] args) {
		SpringApplication.from(HospitapiNotificationApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
