package dev.fairplay.arena.player.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerPercentiles(
        int headshotRate,
        int accuracy,
        int reactionSpeed
) {}
