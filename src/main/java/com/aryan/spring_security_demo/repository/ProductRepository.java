package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByCategoryName(String category);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByBrand(String brand);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByCategoryNameAndBrand(String category, String brand);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByName(String name);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByBrandAndName(String brand, String name);

    Long countByBrandAndName(String brand, String name);

    boolean existsByNameAndBrand(String name, String brand);
}
