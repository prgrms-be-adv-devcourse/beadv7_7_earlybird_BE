package com.growmighty.lectures.firstday.project.infrastructure.search;

import com.growmighty.lectures.firstday.project.domain.Project;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;

@Document(indexName = "projects")
@Setting(settingPath = "elasticsearch/project-settings.json")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDocument {

    @Id
    private Long id;                       // ★ projectId를 _id로 → 색인이 자연스럽게 "멱등 upsert"가 된다

    @Field(type = FieldType.Long)
    private Long creatorId;

	// ★ 동의어는 검색 시점에만!
	@MultiField(mainField = @Field(type = FieldType.Text, analyzer = "korean_index", searchAnalyzer = "korean_search"), otherFields = @InnerField(suffix = "auto", type = FieldType.Text,
			analyzer = "autocomplete_index",         // ★ edge_ngram은 색인 시점에만!
			searchAnalyzer = "korean_index"))
    private String title;

    @Field(type = FieldType.Text, analyzer = "korean_index", searchAnalyzer = "korean_search")
    private String description;

    @Field(type = FieldType.Double)
    private BigDecimal goalAmount;

    @Field(type = FieldType.Keyword)      // ★ 필터용 → keyword
    private String status;

    public static ProjectDocument from(Project p) {
        return new ProjectDocument(p.getId(), p.getCreatorId(), p.getTitle(),
            p.getDescription(), p.getGoalAmount(), p.getStatus().name());
    }
}
