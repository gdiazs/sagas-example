package com.guillermods.orders.config;

import com.guillermods.orders.entity.Product;
import com.guillermods.orders.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CatalogSeeder {

	@Bean
	CommandLineRunner seedCatalog(ProductRepository productRepository) {
		return args -> {
			if (productRepository.count() == 0) {
				productRepository.saveAll(List.of(
						new Product("Laptop", 1200.0, 10),
						new Product("Mouse", 25.0, 50),
						new Product("Keyboard", 80.0, 30),
						new Product("Monitor", 300.0, 15)));
			}
		};
	}
}
