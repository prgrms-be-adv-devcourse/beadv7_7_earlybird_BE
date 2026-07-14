package com.growmighty.lectures.firstday.product.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.product.application.ProductService;
import com.growmighty.lectures.firstday.product.presentation.dto.ChangeProductPriceRequest;
import com.growmighty.lectures.firstday.product.presentation.dto.ChangeStockRequest;
import com.growmighty.lectures.firstday.product.presentation.dto.ProductResponse;
import com.growmighty.lectures.firstday.product.presentation.dto.RegisterProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RefreshScope
@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {
    private final ProductService productService;

    @Value("${product.banner.message:이벤트 준비 중}")
    private String bannerMessage;

    @PostMapping
    public ApiResponse<ProductResponse> register(@RequestBody RegisterProductRequest request) {
        return ApiResponse.ok(ProductResponse.from(productService.register(request.toCommand())));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductResponse> getProduct(@PathVariable Long productId) {
        return ApiResponse.ok(ProductResponse.from(productService.getProductInfo(productId)));
    }

    @PatchMapping("/{productId}/price")
    public ApiResponse<ProductResponse> changePrice(@PathVariable Long productId, @RequestBody ChangeProductPriceRequest request) {
        return ApiResponse.ok(ProductResponse.from(productService.changePrice(productId, request.price())));
    }

    @PostMapping("/{productId}/decrease-stock")
    public ApiResponse<Void> decreaseStock(@PathVariable Long productId, @RequestBody ChangeStockRequest request) {
        productService.decreaseStock(productId, request.quantity());
        return ApiResponse.ok();
    }

    @PostMapping("/{productId}/restore-stock")
    public ApiResponse<Void> restoreStock(@PathVariable Long productId, @RequestBody ChangeStockRequest request) {
        productService.restoreStock(productId, request.quantity());
        return ApiResponse.ok();
    }

    @GetMapping("/banner")
    public Map<String, String> banner() {
        return Map.of("message", bannerMessage);
    }

}
