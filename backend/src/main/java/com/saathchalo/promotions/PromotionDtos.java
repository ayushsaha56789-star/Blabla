package com.saathchalo.promotions;

import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Promotions: what members create, what others see (always "Sponsored"), and what moderators check. */
public final class PromotionDtos {

	private PromotionDtos() {
	}

	/** Only these kinds of business can be promoted (no betting, alcohol, tobacco, adult content or weapons). */
	public enum Category {
		FOOD, SHOP, SERVICES, EDUCATION, EVENTS, TRAVEL, FITNESS, CREATOR, TECH, OTHER
	}

	/** Where a promotion is shown. */
	public enum Slot {
		DISCOVER, SEARCH, GAMES
	}

	public record PromotionRequest(
			@NotBlank(message = "What's your business or project called?") @Size(max = 60, message = "Keep it under 60 characters") String business,
			@NotBlank(message = "Add a headline") @Size(max = 80, message = "Keep the headline under 80 characters") String title,
			@NotBlank(message = "Say a little more") @Size(max = 300, message = "Keep it under 300 characters") String body,
			@Size(max = 300, message = "That link is too long") String linkUrl,
			@NotNull(message = "Choose a category") Category category,
			Boolean allIndia,
			@Size(max = 10, message = "Up to 10 towns") List<Long> placeIds,
			@NotNull(message = "When should it start?") LocalDate startsOn,
			@NotNull(message = "When should it end?") LocalDate endsOn) {
	}

	public record City(long placeId, String name) {
	}

	/** A promotion as members see it. */
	public record PromoCard(UUID id, String business, String title, String body, String linkUrl, Category category, String imageUrl) {
	}

	/** The owner's view: the card, where and when it runs, its status and its totals (never who saw it). */
	public record MyPromotion(PromoCard card, boolean allIndia, List<City> cities, LocalDate startsOn, LocalDate endsOn, String status,
			boolean live, String reviewNote, long views, long clicks, long pricePaise, Instant updatedAt) {
	}

	public record Options(List<Category> categories, boolean free, LocalDate freeUntil, int maxDays) {
	}

	public record AdminPromotionRow(MyPromotion promotion, MemberCard owner, String flags) {
	}

	public record StaffNote(@NotBlank(message = "Say why") @Size(min = 3, max = 300, message = "Between 3 and 300 characters") String note) {
	}
}
