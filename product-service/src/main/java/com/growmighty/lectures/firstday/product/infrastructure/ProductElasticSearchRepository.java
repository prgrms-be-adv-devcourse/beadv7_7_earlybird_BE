package com.growmighty.lectures.firstday.product.infrastructure;

import com.growmighty.lectures.firstday.product.infrastructure.search.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductElasticSearchRepository extends ElasticsearchRepository<ProductDocument, Long> {
}