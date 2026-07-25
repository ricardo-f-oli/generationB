package com.generationb.briefs.api;

import com.generationb.briefs.ContractClauseResponse;
import com.generationb.briefs.internal.ContractClauseService;
import com.generationb.foundation.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clauses")
public class ContractClauseController {

    private final ContractClauseService contractClauseService;

    public ContractClauseController(ContractClauseService contractClauseService) {
        this.contractClauseService = contractClauseService;
    }

    @GetMapping
    public ApiResponse<List<ContractClauseResponse>> listClauses() {
        return ApiResponse.of(contractClauseService.listClauses());
    }

    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderClauses(@RequestBody List<UUID> orderedIds) {
        contractClauseService.reorderClauses(orderedIds);
    }
}
