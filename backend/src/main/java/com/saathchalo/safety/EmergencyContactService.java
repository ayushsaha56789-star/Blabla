package com.saathchalo.safety;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.FieldEncryptor;
import com.saathchalo.common.PhoneNumbers;
import com.saathchalo.safety.SafetyDtos.ContactRequest;
import com.saathchalo.safety.SafetyDtos.ContactView;
import com.saathchalo.safety.internal.EmergencyContact;
import com.saathchalo.safety.internal.EmergencyContactRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Up to three people a member trusts, texted if they press SOS. Their numbers are stored encrypted. */
@Service
public class EmergencyContactService {

	static final int MAX_CONTACTS = 3;

	/** A contact's name and full number, for sending an alert. */
	record Recipient(String name, String phone) {
	}

	private final EmergencyContactRepository contacts;

	private final FieldEncryptor encryptor;

	private final Clock clock;

	public EmergencyContactService(EmergencyContactRepository contacts, FieldEncryptor encryptor, Clock clock) {
		this.contacts = contacts;
		this.encryptor = encryptor;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<ContactView> list(UUID me) {
		return contacts.findByUserIdOrderByCreatedAtAsc(me).stream().map(this::view).toList();
	}

	@Transactional
	public ContactView add(UUID me, ContactRequest req, String ownPhone) {
		if (contacts.countByUserId(me) >= MAX_CONTACTS) {
			throw ApiException.conflict("too_many_contacts", "You can add up to " + MAX_CONTACTS + " emergency contacts.");
		}
		String phone = PhoneNumbers.normalizeIndianMobile(req.phone());
		if (phone.equals(ownPhone)) {
			throw ApiException.badRequest("own_number", "Add someone else's number: they'll be texted if you need help.");
		}
		boolean duplicate = contacts.findByUserIdOrderByCreatedAtAsc(me).stream()
			.anyMatch(c -> encryptor.decrypt(c.getPhoneEnc()).equals(phone));
		if (duplicate) {
			throw ApiException.conflict("duplicate_contact", "That number is already one of your emergency contacts.");
		}
		EmergencyContact saved = contacts.save(new EmergencyContact(me, req.name().strip().replaceAll("\\s+", " "),
				encryptor.encrypt(phone), phone.substring(phone.length() - 4), clock.instant()));
		return view(saved);
	}

	@Transactional
	public void remove(UUID me, UUID contactId) {
		EmergencyContact c = contacts.findById(contactId).filter(x -> x.getUserId().equals(me))
			.orElseThrow(() -> ApiException.notFound("contact_not_found", "We couldn't find that contact."));
		contacts.delete(c);
	}

	@Transactional(readOnly = true)
	List<Recipient> recipients(UUID me) {
		return contacts.findByUserIdOrderByCreatedAtAsc(me).stream()
			.map(c -> new Recipient(c.getName(), encryptor.decrypt(c.getPhoneEnc())))
			.toList();
	}

	private ContactView view(EmergencyContact c) {
		return new ContactView(c.getId(), c.getName(), encryptor.decrypt(c.getPhoneEnc()), c.getCreatedAt());
	}
}
