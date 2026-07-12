package com.aryan.spring_security_demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cart")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private BigDecimal totalAmount = BigDecimal.ZERO;

    // cart has many items
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CartItem> cartItems = new HashSet<>();

    public void addItem(CartItem cartItem){
        this.cartItems.add(cartItem);
        cartItem.setCart(this);
        updateTotalAmount();
    }

    public void removeItem(CartItem cartItem){
        this.cartItems.remove(cartItem);
        cartItem.setCart(null);
        updateTotalAmount();
    }

    public void updateTotalAmount(){
        this.totalAmount = cartItems.stream().map(item -> {
            BigDecimal unitPrice = item.getUnitPrice();
            if(unitPrice == null){
                return BigDecimal.ZERO;
            }
            return unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
        }).reduce(BigDecimal.ZERO,BigDecimal::add);
    }


}
