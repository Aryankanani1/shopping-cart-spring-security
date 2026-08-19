package com.aryan.spring_security_demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.NaturalId;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "users_seq")
    @SequenceGenerator(name = "users_seq", sequenceName = "users_seq", allocationSize = 50)
    private Long id;

    @Version
    private Long version;

    private String firstName;
    private String lastName;
    @NaturalId
    private String email;
    private String password;

    @OneToOne(mappedBy = "user" ,cascade = CascadeType.ALL,orphanRemoval = true, fetch = FetchType.LAZY)
    private Cart cart;

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "user",cascade = CascadeType.ALL,orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Order> orders;

    @BatchSize(size = 20)
    @ManyToMany(fetch = FetchType.LAZY,cascade = {CascadeType.MERGE,
            CascadeType.DETACH
    }
    )
    @JoinTable(name = "user_roles",
                    joinColumns = @JoinColumn
                    (name = "user_id",
                    referencedColumnName = "id"
                    ),
            inverseJoinColumns = @JoinColumn
                    (name = "role_id",
                    referencedColumnName = "id")
    )
    private Collection<Role> roles = new HashSet<>();

    // email is the @NaturalId (a stable, unique business key), so it is the
    // safest basis for equality across the entity lifecycle and detached state.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return email != null && email.equals(other.getEmail());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
