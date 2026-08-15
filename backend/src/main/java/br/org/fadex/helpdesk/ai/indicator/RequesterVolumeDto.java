package br.org.fadex.helpdesk.ai.indicator;

import br.org.fadex.helpdesk.model.user.UserMinDto;

public record RequesterVolumeDto(UserMinDto user, long tickets) {
}
