package br.org.fadex.helpdesk.ai.indicator;

public record SlaSliceDto(long evaluated, long withinTarget, Double percentage) {
}
