package com.guillermods.orders.repository;

import com.guillermods.orders.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

	List<Product> findAllByOrderByIdAsc();

	/**
	 * Atomically reserves {@code qty} units of stock. No rows are affected when
	 * the product does not exist or the stock is insufficient.
	 */
	@Modifying
	@Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
	int reserve(@Param("id") Long id, @Param("qty") Integer qty);

	/** Compensating action: returns reserved units back to stock. */
	@Modifying
	@Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id")
	int restore(@Param("id") Long id, @Param("qty") Integer qty);
}
