package br.org.fadex.helpdesk.model.notification;

import java.time.LocalDateTime;

public record NotificationConnectionDto(String connectionId, LocalDateTime serverTime) {
}
