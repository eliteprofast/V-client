'use strict';

/**
 * Invite rewards: reaching a number of invited players unlocks these premium cosmetics (ids from the
 * built-in catalog). Rewards stack, so 10 invites also includes the 5 and 8 rewards.
 */
const INVITE_REWARDS = [
  { invites: 5, cosmetics: ['ice_cape'] },
  { invites: 8, cosmetics: ['lava_cape'] },
  { invites: 10, cosmetics: ['neon_cape'] },
  { invites: 12, cosmetics: ['lava_flow'] },
  { invites: 15, cosmetics: ['phoenix_wings'] },
  { invites: 17, cosmetics: ['rainbow_wings', 'spin_emote'] },
  { invites: 20, cosmetics: ['neon_pulse', 'star_topper', 'totem_trail'] },
];

/** Every cosmetic id earned by a player who has invited `invites` people. */
function rewardsFor(invites) {
  return INVITE_REWARDS.filter((tier) => invites >= tier.invites).flatMap((tier) => tier.cosmetics);
}

module.exports = { INVITE_REWARDS, rewardsFor };
