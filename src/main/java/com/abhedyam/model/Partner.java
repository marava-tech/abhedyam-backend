package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** An agent / accountant / distributor salesman who brings shops. Earns per shop that pays (paid by hand over UPI). */
@Entity
@Table(name = "partners")
@Getter
@Setter
public class Partner extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String upiId;

    /** Same format and namespace as owner referral codes (ABH + 5), so the app's code box takes both. */
    @Column(nullable = false, unique = true, length = 16)
    private String code;
}
