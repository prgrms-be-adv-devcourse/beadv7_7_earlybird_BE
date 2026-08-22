package com.growmighty.lectures.firstday.board.feign.httpClient.file;

import com.growmighty.lectures.firstday.board.feign.httpClient.file.dto.FileBatchApiData;
import com.growmighty.lectures.firstday.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "file-service")
public interface FileFeignClient {

    @GetMapping("/internal/v1/files/batch")
    ApiResponse<List<FileBatchApiData>> getFilesByOwners(
        @RequestParam("ownerType") String ownerType, @RequestParam("ownerIds") List<Long> ownerIds
    );
}
