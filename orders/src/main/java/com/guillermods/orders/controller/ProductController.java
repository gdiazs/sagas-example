package com.guillermods.orders.controller;

import com.guillermods.orders.entity.Product;
import com.guillermods.orders.repository.ProductRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProductController {

	private final ProductRepository productRepository;

	public ProductController(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	@GetMapping("/products")
	public List<Product> list() {
		return productRepository.findAllByOrderByIdAsc();
	}
}
