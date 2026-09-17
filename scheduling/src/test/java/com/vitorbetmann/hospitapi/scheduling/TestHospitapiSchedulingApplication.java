package com.vitorbetmann.hospitapi.scheduling;

import org.springframework.boot.SpringApplication;

public class TestHospitapiSchedulingApplication {

	public static void main(String[] args) {
		SpringApplication.from(HospitapiSchedulingApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
