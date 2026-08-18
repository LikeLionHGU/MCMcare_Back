package com.ladder.mcmcare.repository;

import com.ladder.mcmcare.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {
}
