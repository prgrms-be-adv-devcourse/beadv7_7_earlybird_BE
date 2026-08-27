package com.growmighty.lectures.firstday.ai.policy.infrastructure.loader;

import com.growmighty.lectures.firstday.ai.policy.domain.PolicyCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyDocumentLoader {

    private static final String POLICY_RESOURCE_PATTERN = "classpath:policy/*.md";
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\n(.*?)\\n---\\n(.*)$", Pattern.DOTALL);
    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile("^## (.+)$", Pattern.MULTILINE);

    private final ResourcePatternResolver resourcePatternResolver;

    public List<PolicyChunk> loadAll() {
        List<PolicyChunk> chunks = new ArrayList<>();
        for (Resource resource : resolvePolicyResources()) {
            try {
                chunks.addAll(loadFile(resource));
            } catch (RuntimeException e) {
                log.warn("정책 문서 로드 실패, 건너뛴 문서: {}", resource.getFilename(), e);
            }
        }
        return chunks;
    }

    private Resource[] resolvePolicyResources() {
        try {
            return resourcePatternResolver.getResources(POLICY_RESOURCE_PATTERN);
        } catch (IOException e) {
            throw new IllegalStateException("정책 문서 리소스를 찾을 수 없습니다.:" + POLICY_RESOURCE_PATTERN, e);
        }
    }

    private List<PolicyChunk> loadFile(Resource resource) {
        String raw = readContent(resource);
        Matcher frontMatterMatcher = FRONTMATTER_PATTERN.matcher(raw);
        if (!frontMatterMatcher.matches()) {
            throw new IllegalStateException("frontmatter 형식이 올바르지 않습니다.:" + resource.getFilename());
        }

        String frontmatter = frontMatterMatcher.group(1);
        String body = frontMatterMatcher.group(2);
        PolicyCategory category = PolicyCategory.valueOf(parseField(frontmatter, "category", resource.getFilename()));
        String topic = parseField(frontmatter, "topic", resource.getFilename());
        String fileSlug = stripExtension(resource.getFilename());

        return splitIntoChunks(body, category, topic, fileSlug);
    }

    private List<PolicyChunk> splitIntoChunks(String body, PolicyCategory category, String topic, String fileSlug) {
        Matcher headingMatcher = SECTION_HEADING_PATTERN.matcher(body);
        List<Integer> headingStarts = new ArrayList<>();
        while (headingMatcher.find()) {
            headingStarts.add(headingMatcher.start());
        }
        if (headingStarts.isEmpty()) {
            throw new IllegalStateException("## 섹션이 없습니다.: " + fileSlug);
        }

        List<PolicyChunk> chunks = new ArrayList<>();
        for (int i = 0; i < headingStarts.size(); i++) {
            int start = headingStarts.get(i);
            int end = (i + 1 < headingStarts.size()) ? headingStarts.get(i + 1) : body.length();
            String section = body.substring(start, end).trim();
            chunks.add(new PolicyChunk(fileSlug + "-" + i, fileSlug, category, topic, section));
        }
        return chunks;
    }

    private String readContent(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("정책 문서를 읽을 수 없습니다.: " + resource.getFilename(), e);
        }
    }

    private String parseField(String frontmatter, String key, String filename) {
        for (String line : frontmatter.split("\n")) {
            String[] parts = line.split(":", 2);
            if (parts.length == 2 && parts[0].trim().equals(key)) {
                return parts[1].trim();
            }
        }
        throw new IllegalStateException("frontmatter에 %s 필드가 없습니다.: %s".formatted(key, filename));
    }

    private String stripExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex == -1 ? filename : filename.substring(0, dotIndex);
    }

}
