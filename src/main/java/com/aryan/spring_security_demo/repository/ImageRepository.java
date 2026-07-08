package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageRepository extends JpaRepository<Image,Long> {

    List<Image> findProductById(Long id);

}
