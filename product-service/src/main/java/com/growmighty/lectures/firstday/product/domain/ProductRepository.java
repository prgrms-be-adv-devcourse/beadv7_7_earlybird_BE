package com.growmighty.lectures.firstday.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();      // ★ 추가 — 재색인용
}
