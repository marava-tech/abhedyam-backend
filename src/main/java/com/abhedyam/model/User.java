package com.abhedyam.model;

import com.abhedyam.model.enums.UserType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@Inheritance(strategy = InheritanceType.JOINED)
public class User extends BaseEntity {
    
    @Column(nullable = false)
    private String name;

    /** Hidden cross-script search key for {@link #name} (specs/cross-script-search.md). */
    @Column(name = "name_key")
    private String nameKey;

    @PrePersist
    @PreUpdate
    void keyName() {
        nameKey = com.abhedyam.util.SearchKey.of(name);
    }
    
    @Column(nullable = true)
    private String phone;
    
    @Column(unique = true, nullable = true)
    private String phoneNormalized;
    
    @Column(unique = true)
    private String email;
    
    @Column(unique = true, nullable = true)
    private String firebaseUid;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType type;
    
    @Column
    private String imageUrl;

}

