package com.aryan.spring_security_demo.model;

import com.aryan.spring_security_demo.validation.NoProfanity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "category_seq")
    @SequenceGenerator(name = "category_seq", sequenceName = "category_seq", allocationSize = 50)
    private Long id;

    @Version
    private Long version;

    @NotBlank(message = "Category name is required")
    @NoProfanity(message = "Category name contains disallowed words")
    @Column(name = "name")
    private String name;

    @JsonIgnore
    @BatchSize(size = 20)
    @OneToMany(mappedBy = "category",fetch = FetchType.LAZY)
    private List<Product> productList;

    public Category(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
