package com.vmudrud.daftiescanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableRetry
@EnableAsync
@SpringBootApplication
public class DaftiescannerApplication {


	public static void main(String[] args) {
		SpringApplication.run(DaftiescannerApplication.class, args);
	}

}
