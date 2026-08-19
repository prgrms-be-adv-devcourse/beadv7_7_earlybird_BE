package com.growmighty.lectures.firstday.ai.policy.presentation;

import com.growmighty.lectures.firstday.ai.policy.application.PolicyReindexService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/policy")
@RequiredArgsConstructor
public class PolicyReindexController {

    private final PolicyReindexService reindexService;

    @PostMapping("/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reindex() {
        reindexService.reindexAll();
    }
}
