package space.gavinklfong.insurance.quotation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
public class QuotationSrvApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuotationSrvApplication.class, args);
	}

}
