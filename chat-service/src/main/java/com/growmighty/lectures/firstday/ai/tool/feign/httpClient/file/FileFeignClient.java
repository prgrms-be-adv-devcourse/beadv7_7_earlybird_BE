package com.growmighty.lectures.firstday.ai.tool.feign.httpClient.file;

import com.growmighty.lectures.firstday.ai.tool.feign.httpClient.file.dto.FileApiData;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "file-service", contextId = "fileLookup")
public interface FileFeignClient {

    @GetMapping("/api/v1/files")
    ApiResponse<List<FileApiData>> findByOwner(
        @RequestParam String ownerType,
        @RequestParam Long ownerId
    );
}
