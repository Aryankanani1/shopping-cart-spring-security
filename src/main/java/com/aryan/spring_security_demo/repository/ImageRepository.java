package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface ImageRepository extends JpaRepository<Image,Long> {

    List<Image> findProductById(Long id);

}
