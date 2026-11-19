import { ChatPreference } from '../../core/api.models';
import { IconName } from '../../shared/icon';

export const CHAT_OPTIONS: ReadonlyArray<{ value: ChatPreference; emoji: string; label: string; long: string }> = [
  { value: 'QUIET', emoji: '🤫', label: 'Quiet', long: 'Prefers a quiet ride' },
  { value: 'SOMETIMES', emoji: '🙂', label: 'Sometimes', long: 'Chats when in the mood' },
  { value: 'CHATTY', emoji: '🗣️', label: 'Chatty', long: 'Loves a good conversation' },
];

export function chatOption(value: ChatPreference) {
  return CHAT_OPTIONS.find((o) => o.value === value) ?? CHAT_OPTIONS[1];
}

/** Human-readable travel preferences for a profile. */
export function preferenceBadges(p: { prefMusic: boolean; prefSmoking: boolean; prefPets: boolean }) {
  const list: Array<{ icon: IconName; text: string }> = [
    { icon: 'music', text: p.prefMusic ? 'Music is welcome' : 'Prefers no music' },
    { icon: 'smoke', text: p.prefSmoking ? 'Smoking is OK' : 'No smoking, please' },
    { icon: 'paw', text: p.prefPets ? 'Pets are welcome' : 'No pets, please' },
  ];
  return list;
}
