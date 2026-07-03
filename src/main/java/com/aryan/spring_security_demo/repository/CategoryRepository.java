package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category,Long> {

    Category findByName(String name);
    boolean existsByName(String name);


}
