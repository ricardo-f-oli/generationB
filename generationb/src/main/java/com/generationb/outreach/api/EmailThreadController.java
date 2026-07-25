package com.generationb.outreach.api;

import com.generationb.foundation.ApiResponse;
import com.generationb.outreach.EmailThreadResponse;
import com.generationb.outreach.internal.EmailThread;
import com.generationb.outreach.internal.EmailThreadRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/outreach/recipients")
public class EmailThreadController {

    private final EmailThreadRepository emailThreadRepository;

    public EmailThreadController(EmailThreadRepository emailThreadRepository) {
        this.emailThreadRepository = emailThreadRepository;
    }

    @GetMapping("/{id}/thread")
    public ResponseEntity<ApiResponse<List<EmailThreadResponse>>> getThreadForRecipient(@PathVariable UUID id) {
        List<EmailThread> threads = emailThreadRepository.findByOutreachRecipientIdAndDeletedAtIsNullOrderByReceivedAtAsc(id);
        List<EmailThreadResponse> responses = threads.stream().map(t -> new EmailThreadResponse(
            t.getId(),
            t.getDirection(),
            t.getFromAddress(),
            t.getSubject(),
            t.getBodyText(),
            t.getReceivedAt()
        )).toList();
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
