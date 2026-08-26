package com.growmighty.lectures.firstday.ai.tool.feign.port.file;

public interface FileLookupPort {
    String findThumbnailUrl(Long projectId, Long thumbnailId);
}
