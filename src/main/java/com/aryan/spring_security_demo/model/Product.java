package com.aryan.spring_security_demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

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
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 50)
    private Long id;

    @Version
    private Long version;

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

    // A Category is shared reference data with its own lifecycle. No cascade here:
    // deleting/saving a Product must never delete or re-persist its Category.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;


    @JsonIgnore
    @BatchSize(size = 20)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true,fetch = FetchType.LAZY)
    private List<Image> imageList;

    public Product(String name, BigDecimal price, String description, String brand, int inventory, Category category) {
        this.name = name;
        this.price = price;
        this.description = description;
        this.brand = brand;
        this.inventory = inventory;
        this.category = category;
    }

    // Entity equality is based on the identifier only, using the accessor so that
    // Hibernate proxies are initialised correctly. hashCode is kept stable across
    // the entity lifecycle (before/after the id is generated).
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
