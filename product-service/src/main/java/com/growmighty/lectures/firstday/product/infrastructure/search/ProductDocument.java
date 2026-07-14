package com.growmighty.lectures.firstday.product.infrastructure.search;

import com.growmighty.lectures.firstday.product.domain.Product;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;

@Document(indexName = "products")
@Setting(settingPath = "elasticsearch/product-settings.json")   // Step 2에서 만들 파일
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    @Id
    private Long id;                       // ★ productId를 _id로 → 색인이 자연스럽게 "멱등 upsert"가 된다 (오전 §6-4 원칙 ①)

    @Field(type = FieldType.Long)
    private Long sellerId;

	// ★ 동의어는 검색 시점에만! (오전 §5-1)
	@MultiField(mainField = @Field(type = FieldType.Text, analyzer = "korean_index", searchAnalyzer = "korean_search"), otherFields = @InnerField(suffix = "auto", type = FieldType.Text,
			analyzer = "autocomplete_index",         // ★ edge_ngram은 색인 시점에만! (오전 §5-2)
			searchAnalyzer = "korean_index"))
    private String name;

    @Field(type = FieldType.Text, analyzer = "korean_index", searchAnalyzer = "korean_search")
    private String description;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Keyword)      // ★ 필터용 → keyword. text로 하면 오전 §3-4의 참사
    private String status;

    @Field(type = FieldType.Long)         // ★ 랭킹 시그널
    private long salesCount;

    public static ProductDocument from(Product p) {
        return new ProductDocument(p.getId(), p.getSellerId(), p.getName(),
            p.getDescription(), p.getPrice(), p.getStatus().name(), p.getSalesCount());
    }
}
