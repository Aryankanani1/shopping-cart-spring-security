package com.aryan.spring_security_demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name =  "name")
    private String name;
    @Column(name = "price")
    private BigDecimal price;
    @Column(name= "description")
    private String description;
    @Column(name = "brand")
    private String brand;
    @Column(name="inventory")
    private int inventory;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "category_id")
    private Category category;


    @JsonIgnore
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Image> imageList;

    public Product(String name, BigDecimal price, String description, String brand, int inventory, Category category) {
        this.name = name;
        this.price = price;
        this.description = description;
        this.brand = brand;
        this.inventory = inventory;
        this.category = category;
    }
}
