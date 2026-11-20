package com.saathchalo.community;

import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Squads, event rides and vibe match. */
public final class CommunityDtos {

	private CommunityDtos() {
	}

	public enum SquadKind {
		COLLEGE, OFFICE, FRIENDS
	}

	/** The interests members can pick for vibe match. */
	public static final List<String> INTERESTS = List.of("Bollywood", "Indie music", "Hip-hop", "Classical music", "Podcasts",
			"Cricket", "Football", "Startups", "Tech", "Books", "Films", "Anime", "Food", "Travel", "Photography", "Fitness", "Gaming",
			"Comedy", "Art", "Spirituality");

	public record SquadRequest(
			@NotBlank(message = "Name your squad") @Size(min = 3, max = 60, message = "Between 3 and 60 characters") String name,
			@NotNull(message = "College, office or friends?") SquadKind kind,
			@Size(max = 100, message = "That email domain is too long") String emailDomain) {
	}

	public record CodeRequest(@NotBlank(message = "Enter the squad code") @Size(max = 12, message = "That code is too long") String code) {
	}

	/** {@code code} is shown to members only, so they can invite others. */
	public record SquadCard(UUID id, String name, SquadKind kind, String emailDomain, int members, String code, boolean owner) {
	}

	/** A ride offered by a squad member or going to an event. */
	public record RideCard(UUID id, String fromName, String toName, Instant departureAt, boolean ladiesOnly, MemberCard driver) {
	}

	public record SquadView(SquadCard squad, List<MemberCard> members, List<RideCard> rides) {
	}

	public record EventRequest(
			@NotBlank(message = "What's the event called?") @Size(max = 100, message = "Keep it under 100 characters") String title,
			@NotBlank(message = "Where is it? (the venue)") @Size(max = 150, message = "Keep it under 150 characters") String venue,
			@NotNull(message = "Choose the town") Long placeId,
			@NotNull(message = "When does it start?") Instant startsAt,
			@Size(max = 1000, message = "Keep it under 1,000 characters") String description) {
	}

	public record EventCard(UUID id, String title, String venue, long placeId, String placeName, String state, Instant startsAt,
			String status) {
	}

	public record EventView(EventCard event, String description, List<RideCard> rides) {
	}

	public record InterestsRequest(@NotNull(message = "Pick your interests") @Size(max = 8, message = "Up to 8 interests") List<String> interests) {
	}

	public record VibeView(List<String> interests, List<String> options) {
	}
}
