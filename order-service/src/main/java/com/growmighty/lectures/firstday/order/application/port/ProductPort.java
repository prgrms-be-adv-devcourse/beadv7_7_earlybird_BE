package com.growmighty.lectures.firstday.order.application.port;

import com.growmighty.lectures.firstday.order.application.port.dto.ProductSnapshot;

public interface ProductPort {

    ProductSnapshot getProduct(Long productId);

    void decreaseStock(Long productId, int quantity);

    void restoreStock(Long productId, int quantity);
}
