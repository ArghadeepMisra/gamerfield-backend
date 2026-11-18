package dev.fairplay.arena.player;

import dev.fairplay.arena.player.dto.PlayerDetailResponse;
import dev.fairplay.arena.player.dto.PlayerPercentiles;
import dev.fairplay.arena.player.dto.PlayerStats;
import dev.fairplay.arena.player.dto.PlayerSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PlayerMapper {

    PlayerSummaryResponse toSummary(Player player);

    @Mapping(target = "email", source = "email")
    @Mapping(target = "stats", source = "stats")
    @Mapping(target = "percentiles", source = "percentiles")
    PlayerDetailResponse toDetail(
            Player player,
            String email,
            PlayerStats stats,
            PlayerPercentiles percentiles);
}
