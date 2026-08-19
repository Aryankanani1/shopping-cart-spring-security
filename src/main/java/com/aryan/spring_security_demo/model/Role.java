package com.aryan.spring_security_demo.model;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.Collection;
import java.util.HashSet;

@Entity
@Table(name="roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "roles_seq")
    @SequenceGenerator(name = "roles_seq", sequenceName = "roles_seq", allocationSize = 50)
    private Long id;

    @Version
    private Long version;

    private String name;

    @BatchSize(size = 20)
    @ManyToMany(mappedBy = "roles",fetch = FetchType.LAZY)
    private Collection<User> users = new HashSet<>();

    public Role(String name) {
        this.name = name;
    }

    // name is the unique business key for a role (e.g. ROLE_ADMIN).
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role other)) return false;
        return name != null && name.equals(other.getName());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}


