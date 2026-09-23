package board.push.api;

import board.push.service.PushBroadcastService;
import board.push.service.PushBroadcastService.BroadcastResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/push/broadcasts")
public class PushBroadcastController {
    private final PushBroadcastService broadcastService;

    @PostMapping
    public BroadcastResult broadcast(@Valid @RequestBody BroadcastRequest request) {
        return broadcastService.broadcast(request.title(), request.body());
    }

    public record BroadcastRequest(
            @NotBlank @Size(max = 100) String title,
            @NotBlank @Size(max = 500) String body
    ) {
    }
}
