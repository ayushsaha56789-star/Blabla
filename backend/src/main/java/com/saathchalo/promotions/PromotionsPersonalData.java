package com.saathchalo.promotions;

import com.saathchalo.common.PersonalData;
import com.saathchalo.files.PromoImageStorage;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** The member's promotions and their totals. On erasure they're deleted, with their pictures. */
@Component
class PromotionsPersonalData implements PersonalData {

	private final JdbcTemplate jdbc;

	private final PromoImageStorage images;

	private final PromotionService promotions;

	PromotionsPersonalData(JdbcTemplate jdbc, PromoImageStorage images, PromotionService promotions) {
		this.jdbc = jdbc;
		this.images = images;
		this.promotions = promotions;
	}

	@Override
	public String section() {
		return "promotions";
	}

	@Override
	public Object export(UUID memberId) {
		return Map.of("promotions", PersonalData.rows(jdbc.queryForList("""
				SELECT business, title, body, link_url AS link, category, all_india AS "allIndia", starts_on AS "startsOn",
				       ends_on AS "endsOn", status, views, clicks, created_at AS created
				FROM promotions WHERE owner_id = ? ORDER BY created_at""", memberId)));
	}

	@Override
	public void erase(UUID memberId) {
		jdbc.queryForList("SELECT image_path FROM promotions WHERE owner_id = ? AND image_path IS NOT NULL", String.class, memberId)
			.forEach(images::deleteQuietly);
		jdbc.update("DELETE FROM promotions WHERE owner_id = ?", memberId);
		jdbc.update("DELETE FROM promotion_views WHERE user_id = ?", memberId);
	}

	/** Once a day: the daily view counts older than a week go. */
	@Scheduled(fixedDelay = 86_400_000, initialDelay = 1_200_000)
	void tidy() {
		promotions.tidy();
	}
}
