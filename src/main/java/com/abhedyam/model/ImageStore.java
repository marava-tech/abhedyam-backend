package com.abhedyam.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "image_store")
@Getter
@Setter
/** Admin image store was removed; the table and entity stay only so account deletion never deletes shared catalog pictures old products may point to. */
public class ImageStore extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(name = "image_store_tags", joinColumns = @JoinColumn(name = "image_store_id"))
    @Column(name = "tag")
    private List<String> tags;

    @Column(nullable = false)
    private String imageUrl;

    @Column
    private String description;
}
