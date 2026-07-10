package com.sherlock.confidence.adapter.in.rest;

import com.sherlock.confidence.application.ConfidenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only verdict query: the current per-participant belief for a meeting.
 * Handy for demos and the dashboard's headline panel until the WS Gateway (M3)
 * pushes live updates.
 */
@RestController
@RequestMapping("/api/v1/meetings/{meetingId}")
public class VerdictController {

    private final ConfidenceService service;

    public VerdictController(ConfidenceService service) {
        this.service = service;
    }

    @GetMapping("/verdict")
    public List<VerdictDto> verdict(@PathVariable String meetingId) {
        return service.verdicts(meetingId).stream()
                .map(v -> new VerdictDto(v.participantId(), v.state().name(), v.score()))
                .toList();
    }

    public record VerdictDto(String participantId, String state, double score) {
    }
}
