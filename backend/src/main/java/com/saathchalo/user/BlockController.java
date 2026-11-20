package com.saathchalo.user;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.user.MemberProfiles.MemberCard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BlockController {

	private final BlockService blocks;

	public BlockController(BlockService blocks) {
		this.blocks = blocks;
	}

	@GetMapping("/api/users/me/blocks")
	public List<MemberCard> myBlocks() {
		return blocks.myBlocks(CurrentUser.require());
	}

	@PostMapping("/api/users/{id}/block")
	public ResponseEntity<Void> block(@PathVariable UUID id) {
		blocks.block(CurrentUser.require(), id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/users/{id}/block")
	public ResponseEntity<Void> unblock(@PathVariable UUID id) {
		blocks.unblock(CurrentUser.require(), id);
		return ResponseEntity.noContent().build();
	}
}
