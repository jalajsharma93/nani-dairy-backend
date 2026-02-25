package net.nani.dairy.feed;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.nani.dairy.feed.dto.CreateFeedLogRequest;
import net.nani.dairy.feed.dto.FeedLogResponse;
import net.nani.dairy.feed.dto.UpdateFeedLogRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/feed-logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeedLogController {

    private final FeedLogService feedLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER')")
    public List<FeedLogResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String animalId
    ) {
        return feedLogService.list(date, animalId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','WORKER','FEED_MANAGER')")
    public FeedLogResponse create(@Valid @RequestBody CreateFeedLogRequest req) {
        return feedLogService.create(req);
    }

    @PutMapping("/{feedLogId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','FEED_MANAGER')")
    public FeedLogResponse update(@PathVariable String feedLogId, @Valid @RequestBody UpdateFeedLogRequest req) {
        return feedLogService.update(feedLogId, req);
    }
}
