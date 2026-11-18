package dev.fairplay.arena.player.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerStats(
        long kills,
        long deaths,
        double kdRatio,
        double headshotRate,
        double accuracy,
        Integer avgReactionMs
) {}
