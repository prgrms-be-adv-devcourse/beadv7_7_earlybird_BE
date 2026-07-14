package com.growmighty.lectures.firstday.product;

import com.growmighty.lectures.firstday.product.application.ProductService;
import com.growmighty.lectures.firstday.product.application.dto.RegisterProductCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class ProductDataInitializer implements CommandLineRunner {
    private final ProductService productService;

    @Override
    public void run(String... args) {
        register("삼성 노트북 갤럭시북4 프로", 2_150_000, 50, "가벼운 삼성 노트북입니다.");
        register("LG 그램 노트북 17인치", 1_890_000, 30, "초경량 대화면 노트북.");
        register("애플 맥북 에어 M3", 1_590_000, 40, "애플 실리콘 랩탑.");
        register("애플 맥북 프로 14 M3", 2_690_000, 20, "전문가용 laptop.");
        register("무선 기계식 키보드 청축", 120_000, 100, "타건감 좋은 무선 keyboard.");
        register("유선 기계식 키보드 적축", 90_000, 80, "사무실용 조용한 키보드.");
        register("로지텍 무선 마우스 MX Master 3S", 129_000, 200, "인체공학 wireless mouse.");
        register("게이밍 유선 마우스", 45_000, 150, "가성비 게이밍 마우스.");
        register("아이폰 15 프로 스마트폰", 1_550_000, 60, "티타늄 휴대폰.");
        register("갤럭시 S24 울트라 스마트폰", 1_690_000, 70, "AI 핸드폰.");
        register("무선 이어폰 버즈3 프로", 249_000, 300, "노이즈캔슬링 이어폰.");
        register("노트북 파우치 15인치", 25_000, 500, "노트북 보호 파우치.");
        // 취향껏 몇 개 더 추가해도 좋습니다 (예: 모니터, 충전기, 케이스...)
    }

    private void register(String name, long price, int stock, String desc) {
        productService.register(
            new RegisterProductCommand(1L, name, BigDecimal.valueOf(price), stock, desc));
    }
}
