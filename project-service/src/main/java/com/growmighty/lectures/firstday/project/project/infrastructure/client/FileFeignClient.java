package com.growmighty.lectures.firstday.project.project.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "file-service")
public interface FileFeignClient {

    @DeleteMapping("/internal/v1/files")
    void deleteByOwner(@RequestParam("ownerType") String ownerType, @RequestParam("ownerId") Long ownerId);
}
