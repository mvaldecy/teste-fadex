package br.org.fadex.helpdesk.sse.model;

import java.time.LocalDateTime;

public record NotificationConnectionDto(String connectionId, LocalDateTime serverTime) {
}
