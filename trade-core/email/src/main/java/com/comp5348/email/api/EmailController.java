package com.comp5348.email.api;

import com.comp5348.email.domain.EmailMessage;
import com.comp5348.email.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email")
public class EmailController {
    private final EmailService service;
    public EmailController(EmailService service) { this.service = service; }

    public record EmailReq(Long orderId, String type, String to, String subject, String body) {}
    public record EmailResp(Long id, String status) {
        public static EmailResp of(EmailMessage m) { return new EmailResp(m.getId(), m.getStatus()); }
    }

    @PostMapping("/send")
    public ResponseEntity<EmailResp> send(@RequestBody EmailReq req) {
        if (req == null || req.to() == null || req.to().isBlank()) return ResponseEntity.badRequest().build();
        EmailMessage m = service.send(req.orderId(), req.type(), req.to(), req.subject(), req.body());
        return ResponseEntity.ok(EmailResp.of(m));
    }

    @GetMapping("/health")
    public String health() { return "OK"; }
}
