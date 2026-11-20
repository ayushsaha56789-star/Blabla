package com.saathchalo.rewards;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.rewards.RewardDtos.BoardRequest;
import com.saathchalo.rewards.RewardDtos.ClaimRequest;
import com.saathchalo.rewards.RewardDtos.Leaderboard;
import com.saathchalo.rewards.RewardDtos.RewardsView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rewards")
@RequiresFeature("rewards")
public class RewardController {

	private final RewardService rewards;

	public RewardController(RewardService rewards) {
		this.rewards = rewards;
	}

	@GetMapping("/me")
	public RewardsView me() {
		return rewards.me(CurrentUser.require());
	}

	@PostMapping("/claim")
	@RequiresFeature("rewards.referrals")
	public RewardsView claim(@Valid @RequestBody ClaimRequest request) {
		return rewards.claim(CurrentUser.require(), request.code());
	}

	@PutMapping("/board")
	public RewardsView board(@Valid @RequestBody BoardRequest request) {
		return rewards.board(CurrentUser.require(), request.on(), request.nickname());
	}

	@GetMapping("/leaderboard")
	public Leaderboard leaderboard() {
		return rewards.leaderboard(CurrentUser.require());
	}
}
