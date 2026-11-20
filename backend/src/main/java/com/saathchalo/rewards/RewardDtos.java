package com.saathchalo.rewards;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Rewards: XP, levels, badges, impact, referrals and the eco leaderboard. */
public final class RewardDtos {

	private RewardDtos() {
	}

	public record Badge(String key, String name, String how, boolean earned, int progress, int goal) {
	}

	/** What the member's shared rides add up to. {@code co2Kg}: CO₂ not emitted because seats were shared. */
	public record Impact(int trips, int driven, int km, int people, int rupees, double co2Kg) {
	}

	public record Challenge(String title, int done, int goal, boolean complete) {
	}

	/**
	 * {@code canClaim}: the member may still enter a friend's invite code (in their first two weeks);
	 * {@code creditRupees}: what each side gets when an invited friend completes their first ride.
	 */
	public record Referral(String code, int invited, int rewarded, int creditRupees, boolean canClaim, boolean invitedByFriend,
			String status) {
	}

	public record Board(boolean on, String nickname) {
	}

	public record RewardsView(int xp, int level, String levelName, int levelStartXp, Integer nextLevelXp, String nextLevelName,
			Impact impact, int streak, int bestStreak, Challenge challenge, List<Badge> badges, Referral referral, Board board) {
	}

	public record ClaimRequest(@NotBlank(message = "Enter the invite code") @Size(max = 16, message = "That code is too long") String code) {
	}

	public record BoardRequest(@NotNull(message = "On or off?") Boolean on,
			@Size(max = 24, message = "Keep the nickname under 24 characters") String nickname) {
	}

	public record BoardRow(int rank, String nickname, double co2Kg, int trips, boolean you) {
	}

	public record Leaderboard(String month, List<BoardRow> top, BoardRow me) {
	}
}
