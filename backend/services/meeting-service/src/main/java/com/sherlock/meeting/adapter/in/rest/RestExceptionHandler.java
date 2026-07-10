package com.sherlock.meeting.adapter.in.rest;

import com.sherlock.meeting.application.MeetingNotFoundException;
import com.sherlock.meeting.domain.model.IllegalMeetingStateException;
import com.sherlock.meeting.domain.model.ParticipantNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Maps domain/application exceptions to RFC-7807 {@code application/problem+json}
 * responses. Keeping this in the REST adapter means the domain throws meaningful
 * exceptions without any knowledge of HTTP.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler({MeetingNotFoundException.class, ParticipantNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage());
    }

    @ExceptionHandler(IllegalMeetingStateException.class)
    public ProblemDetail handleConflict(IllegalMeetingStateException ex) {
        return problem(HttpStatus.CONFLICT, "illegal-state", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", ex.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://sherlock/errors/" + type));
        pd.setTitle(status.getReasonPhrase());
        return pd;
    }
}
