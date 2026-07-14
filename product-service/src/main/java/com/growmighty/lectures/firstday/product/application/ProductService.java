package com.growmighty.lectures.firstday.product.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.product.application.dto.ProductInfo;
import com.growmighty.lectures.firstday.product.application.dto.RegisterProductCommand;
import com.growmighty.lectures.firstday.product.domain.event.ProductChangedEvent;
import com.growmighty.lectures.firstday.product.domain.Product;
import com.growmighty.lectures.firstday.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;   // ★ 추가

    @Transactional
    public ProductInfo register(RegisterProductCommand command) {
        Product product = Product.register(
            command.sellerId(), command.name(), command.price(), command.stockQuantity(), command.description());
        ProductInfo info = ProductInfo.from(productRepository.save(product));
        eventPublisher.publishEvent(new ProductChangedEvent(info.id()));   // ★
        return info;
    }

    @Transactional
    public ProductInfo changePrice(Long productId, BigDecimal newPrice) {
        Product product = getProductEntity(productId);
        product.changePrice(newPrice);
        eventPublisher.publishEvent(new ProductChangedEvent(productId));   // ★
        return ProductInfo.from(product);
    }

    @Transactional
    public void decreaseStock(Long productId, int quantity) {
        getProductEntity(productId).decreaseStock(quantity);
        eventPublisher.publishEvent(new ProductChangedEvent(productId));   // ★
    }

    @Transactional
    public void restoreStock(Long productId, int quantity) {
        getProductEntity(productId).restoreStock(quantity);
        eventPublisher.publishEvent(new ProductChangedEvent(productId));   // ★
    }

    @Transactional(readOnly = true)
    public ProductInfo getProductInfo(Long productId) {
        return ProductInfo.from(getProductEntity(productId));
    }

    private Product getProductEntity(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 상품입니다. productId=" + productId));
    }
}
