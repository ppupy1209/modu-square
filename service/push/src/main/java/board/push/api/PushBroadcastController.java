package board.push.api;

import board.push.service.PushBroadcastService;
import board.push.service.PushBroadcastService.AcceptResult;
import board.push.service.PushBroadcastService.BroadcastStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/push/broadcasts")
public class PushBroadcastController {
    private final PushBroadcastService broadcastService;

    /** 접수만 하고 바로 응답한다. 새로 접수했으면 202, 같은 키로 이미 접수된 발송이면 200이다. */
    @PostMapping
    public ResponseEntity<AcceptResult> accept(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 64) String idempotencyKey,
            @Valid @RequestBody BroadcastRequest request
    ) {
        AcceptResult result = broadcastService.accept(idempotencyKey, request.title(), request.body());
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result);
    }

    @GetMapping("/{broadcastId}")
    public BroadcastStatus readStatus(@PathVariable Long broadcastId) {
        return broadcastService.readStatus(broadcastId);
    }

    public record BroadcastRequest(
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 500) String body
    ) {
    }
}
