/**
 * SaathChalo's policies in plain English. These are drafts written for the owner: a lawyer should review them
 * before launch. While {@link LEGAL_DRAFT} is true, the pages say so.
 */
export const LEGAL_DRAFT = true;
export const LEGAL_UPDATED = '2026-11-19';

export type LegalDocKey = 'terms' | 'privacy' | 'community' | 'grievance';

export interface LegalSection {
  heading: string;
  paragraphs?: string[];
  points?: string[];
}

export interface LegalDoc {
  key: LegalDocKey;
  title: string;
  summary: string;
  sections: LegalSection[];
}

export const LEGAL_DOCS: Record<LegalDocKey, LegalDoc> = {
  terms: {
    key: 'terms',
    title: 'Terms of use',
    summary: 'The rules for using SaathChalo, in plain words.',
    sections: [
      {
        heading: 'What SaathChalo is',
        paragraphs: [
          'SaathChalo helps people who are travelling between cities share the journey and its costs. Drivers are members offering empty seats on trips they are making anyway. We are not a taxi or transport company, and we do not employ drivers.',
        ],
      },
      {
        heading: 'Who can use it',
        points: [
          'You must be at least 18 years old.',
          'Use your real name and a mobile number that belongs to you. One account per person.',
          'Keep your login codes to yourself. You are responsible for what happens in your account.',
        ],
      },
      {
        heading: 'Sharing costs, not making money',
        points: [
          'Prices are for sharing the cost of the trip (fuel, tolls, wear), not for making a profit. That is why we limit the price per kilometre.',
          'Drivers must hold a valid driving licence, and the car must have valid registration, insurance and a pollution certificate, and be safe to drive.',
          'Offering rides as a business, or using SaathChalo for commercial transport, is not allowed.',
        ],
      },
      {
        heading: 'Booking, paying and cancelling',
        points: [
          'A seat is yours once the driver confirms it (or straight away with instant booking).',
          'For now you pay the driver directly, in cash or by UPI, the amount shown when you booked. Nobody may ask for more.',
          'If your plans change, cancel as early as you can so others can take the seat or find another ride.',
        ],
      },
      {
        heading: 'Behaviour and safety',
        paragraphs: [
          'Everyone must follow our Community guidelines and the law, including traffic rules. In an emergency, call 112. The SOS button alerts our team and your emergency contacts, but SaathChalo is not an emergency service.',
        ],
      },
      {
        heading: 'What you post',
        paragraphs: [
          'You own what you write and upload (your profile, messages and reviews). You allow us to show it in the app so the service works. We may remove content, or limit or suspend accounts, that break these terms or the guidelines.',
        ],
      },
      {
        heading: 'Our responsibility',
        paragraphs: [
          'We work hard to keep SaathChalo safe and working, but we do not control the trips members make together. As far as the law allows, members are responsible for their own conduct on a trip. Nothing in these terms takes away rights you have under Indian law.',
        ],
      },
      {
        heading: 'Leaving, and changes to these terms',
        paragraphs: [
          'You can delete your account at any time in the Privacy centre. If we change these terms in an important way, we will tell you in the app before the change applies. Indian law applies to these terms. For complaints, contact our grievance officer.',
        ],
      },
    ],
  },
  privacy: {
    key: 'privacy',
    title: 'Privacy policy',
    summary: 'What we collect, why, who sees it, how long we keep it, and your rights.',
    sections: [
      {
        heading: 'What we collect',
        points: [
          'Your mobile number, to log you in with one-time codes.',
          'What you add to your profile: name, photo, gender, date of birth, city, bio, email and travel preferences.',
          'Your cars, including the number plate (stored encrypted).',
          'Your rides, bookings, chats, reviews and help requests.',
          'Emergency contacts you add (stored encrypted).',
          'Your location, only when you share a trip or press SOS.',
          'Basic technical data such as IP address and browser, to keep accounts secure and stop abuse.',
        ],
      },
      {
        heading: 'Why we use it',
        points: [
          'To run SaathChalo: find rides, book seats, and let co-travellers talk to each other.',
          'For safety: SOS alerts, trip sharing, reports and checking identities.',
          'To prevent fraud and abuse, and to meet our legal duties.',
        ],
      },
      {
        heading: 'Who can see it',
        points: [
          'Other members see your first name and last initial, photo, bio, city, age (not your date of birth), preferences and reviews.',
          'Your phone number is shared only between a driver and passengers with a confirmed seat on the same ride.',
          'Our staff see what they need to help you and keep the community safe. Every sensitive action they take is recorded.',
          'We never sell your data. Service providers (for example for text messages and hosting) process it only on our behalf. We share data with authorities only when the law requires it.',
        ],
      },
      {
        heading: 'How long we keep it',
        points: [
          'Chats: deleted 6 months after the ride.',
          'Live location from trip sharing: removed soon after the trip. Locations from SOS alerts: removed after 90 days.',
          'Your account: until you delete it. After you ask, you have 30 days to change your mind; then we erase your details.',
          'After an account is erased, Indian law (IT Rules 2021) asks us to keep the sign-up details for 180 days. We keep them encrypted and locked away, then delete them.',
        ],
      },
      {
        heading: 'Your rights',
        paragraphs: [
          'Under the Digital Personal Data Protection Act, 2023, you can: get a copy of your data (Privacy centre → Download my data); correct it (edit your profile); erase it (Privacy centre → Delete my account); nominate someone to act for you if you cannot; and complain to our grievance officer. You can also complain to the Data Protection Board of India.',
        ],
      },
      {
        heading: 'How we protect it',
        paragraphs: [
          'Login codes are stored only in a scrambled (hashed) form and expire after 5 minutes. Sensitive details such as number plates, emergency contacts and staff security keys are encrypted. Uploaded photos are re-processed to remove hidden location data. Connections are encrypted (HTTPS).',
        ],
      },
      {
        heading: 'Children',
        paragraphs: ['SaathChalo is only for people aged 18 and over.'],
      },
    ],
  },
  community: {
    key: 'community',
    title: 'Community guidelines',
    summary: 'How we look after each other on SaathChalo.',
    sections: [
      {
        heading: 'Be reliable',
        points: [
          'Be on time at the pickup point. If you are running late, message your co-travellers.',
          'Cancel as early as you can if your plans change.',
          'Drivers: keep your ride details (route, time, car) accurate.',
        ],
      },
      {
        heading: 'Be respectful',
        points: [
          'No harassment, threats, hate speech or discrimination, in person or in chat.',
          'Respect each other\'s preferences on music, smoking, pets and conversation.',
          'Keep conversations about the trip. Don\'t ask for personal details someone hasn\'t offered.',
        ],
      },
      {
        heading: 'Be safe',
        points: [
          'Drivers: follow traffic rules, never drive after drinking, and make sure everyone wears a seatbelt. Don\'t carry more people than the car is made for.',
          'Passengers: check that the car and the driver match what the app shows before you get in.',
          'In an emergency call 112, and use the SOS button on your ride page.',
        ],
      },
      {
        heading: 'Be honest',
        points: [
          'Use your real name and a real photo of yourself.',
          'Pay the amount shown when you booked. Drivers may not ask for more.',
          'Write honest reviews about the trip. No insults or personal attacks.',
        ],
      },
      {
        heading: 'Ladies-only rides',
        paragraphs: ['Only women drivers can offer ladies-only rides, and only women can see and book them.'],
      },
      {
        heading: 'If something goes wrong',
        paragraphs: [
          'Use "Report" on a profile, ride, message or review, or "Block" to stop someone contacting you. Our team reads every report within 24 hours. Depending on what happened, we may warn, restrict or suspend an account.',
        ],
      },
    ],
  },
  grievance: {
    key: 'grievance',
    title: 'Grievance officer',
    summary: 'How to make a formal complaint, and how quickly we respond.',
    sections: [
      {
        heading: 'How to complain',
        paragraphs: [
          'Write to our grievance officer using the details on this page, or open the Help centre, choose "A formal complaint (grievance)" and tell us what happened. Include the ride, member or message it is about if you can.',
        ],
      },
      {
        heading: 'What happens next',
        points: [
          'We acknowledge your complaint within 24 hours.',
          'We resolve it within 15 days and tell you what we did.',
          'Complaints about your personal data are handled the same way. If you are not happy with our answer, you can also complain to the Data Protection Board of India.',
        ],
      },
    ],
  },
};
