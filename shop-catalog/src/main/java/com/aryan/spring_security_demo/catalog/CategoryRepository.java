package com.aryan.spring_security_demo.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category,Long> {

    Category findByName(String name);
    boolean existsByName(String name);

    /** Whether a category other than {@code id} already has this name (for renames). */
    boolean existsByNameAndIdNot(String name, Long id);


}
