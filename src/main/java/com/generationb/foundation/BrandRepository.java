package com.generationb.foundation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BrandRepository extends JpaRepository<Brand, UUID> {

    @Query("SELECT b FROM Brand b WHERE b.deletedAt IS NULL ORDER BY b.name")
    List<Brand> findAllActive();
}
