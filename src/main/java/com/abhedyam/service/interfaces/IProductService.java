package com.abhedyam.service.interfaces;


import java.util.UUID;

public interface IProductService {

    void evictOwnerCaches(UUID ownerId);
}
