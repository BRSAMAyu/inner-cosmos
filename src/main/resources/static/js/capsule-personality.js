/* ── Inner Cosmos Capsule Personality System ── */
/* Each seed capsule gets a unique visual identity */

window.CapsulePersonality = {
  // Seed capsule visual identities
  seedIdentities: {
    "斯多葛信使": {
      icon: "🏛️",
      color: "#8B7355",
      secondaryColor: "#D4C4A8",
      avatar: "stoic",
      mood: "steady",
      background: "linear-gradient(135deg, #8B7355 0%, #D4C4A8 100%)",
      borderColor: "#6B5344",
      glowColor: "rgba(139, 115, 85, 0.3)"
    },
    "苏格拉底之问": {
      icon: "❓",
      color: "#4A6741",
      secondaryColor: "#7FB069",
      avatar: "socratic",
      mood: "curious",
      background: "linear-gradient(135deg, #4A6741 0%, #7FB069 100%)",
      borderColor: "#3A5731",
      glowColor: "rgba(74, 103, 65, 0.3)"
    },
    "庄周之梦": {
      icon: "🦋",
      color: "#6B8BA6",
      secondaryColor: "#B0D4E1",
      avatar: "zhuangzi",
      mood: "dreamy",
      background: "linear-gradient(135deg, #6B8BA6 0%, #B0D4E1 100%)",
      borderColor: "#4B7B96",
      glowColor: "rgba(107, 139, 166, 0.3)"
    },
    "存在主义旅人": {
      icon: "🚶",
      color: "#9E7A7A",
      secondaryColor: "#D4B8B8",
      avatar: "existential",
      mood: "contemplative",
      background: "linear-gradient(135deg, #9E7A7A 0%, #D4B8B8 100%)",
      borderColor: "#7E5A5A",
      glowColor: "rgba(158, 122, 122, 0.3)"
    },
    "热烈的画家": {
      icon: "🎨",
      color: "#C77D63",
      secondaryColor: "#E8B4A8",
      avatar: "painter",
      mood: "passionate",
      background: "linear-gradient(135deg, #C77D63 0%, #E8B4A8 100%)",
      borderColor: "#A75D43",
      glowColor: "rgba(199, 125, 99, 0.3)"
    },
    "安静的图书管理员": {
      icon: "📚",
      color: "#7D9C8E",
      secondaryColor: "#B8D4C4",
      avatar: "librarian",
      mood: "gentle",
      background: "linear-gradient(135deg, #7D9C8E 0%, #B8D4C4 100%)",
      borderColor: "#5D7C6E",
      glowColor: "rgba(125, 156, 142, 0.3)"
    },
    "深夜电台": {
      icon: "📻",
      color: "#6B5B7A",
      secondaryColor: "#A89BB8",
      avatar: "radio",
      mood: "intimate",
      background: "linear-gradient(135deg, #6B5B7A 0%, #A89BB8 100%)",
      borderColor: "#4B3B5A",
      glowColor: "rgba(107, 91, 122, 0.3)"
    },
    "海边修表匠": {
      icon: "⌚",
      color: "#8B9D83",
      secondaryColor: "#C8D4C4",
      avatar: "watchmaker",
      mood: "patient",
      background: "linear-gradient(135deg, #8B9D83 0%, #C8D4C4 100%)",
      borderColor: "#6B7D63",
      glowColor: "rgba(139, 157, 131, 0.3)"
    }
  },

  // ── Deterministic per-capsule identity (Morandi day / warm-night) ──
  // PURE & deterministic: same seed -> identical palette+mood+avatar, always.
  // This is the JS twin of com.innercosmos.util.CapsuleIdentityUtils and MUST
  // implement the IDENTICAL algorithm (hash, palette table, mood table, indices).
  // No Math.random, no Date — seeded djb2 over UTF-8 bytes.
  _PALETTES: [
    ["#8FA994", "#C8D4C4", "#6B8974", 143, 169, 148], // 0 sage green
    ["#A89BB8", "#D4CCDE", "#897BA0", 168, 155, 184], // 1 mauve
    ["#8B9DA8", "#C4D0D8", "#6B7D88", 139, 157, 168], // 2 slate blue
    ["#B8A89B", "#D8CFC4", "#98887B", 184, 168, 155], // 3 warm taupe
    ["#9CB0A2", "#CCD8CE", "#7C9082", 156, 176, 162], // 4 eucalyptus
    ["#B89B9B", "#DECBCB", "#987B7B", 184, 155, 155], // 5 dusty rose
    ["#9BA8B8", "#CBD4DE", "#7B8898", 155, 168, 184], // 6 periwinkle
    ["#A8A089", "#D0CABC", "#88806B", 168, 160, 137], // 7 olive sand
    ["#8BA8A0", "#C0D4CC", "#6B887F", 139, 168, 160], // 8 seafoam
    ["#B09BA8", "#D4C8D0", "#907B88", 176, 155, 168], // 9 heather
    ["#A89B95", "#D0C8C2", "#887B75", 168, 155, 149], //10 mushroom
    ["#9FA8B0", "#CBD2D8", "#7F8890", 159, 168, 176]  //11 silver blue
  ],
  _MOODS: [
    "steady", "curious", "dreamy", "contemplative", "gentle",
    "intimate", "patient", "hopeful", "quiet", "warm"
  ],

  // djb2-style deterministic hash over the UTF-8 bytes of the seed string.
  // Mirrors CapsuleIdentityUtils.hashSeed exactly.
  hashSeed(seed) {
    if (seed == null) seed = "";
    seed = String(seed);
    let hash = 5381;
    for (let i = 0; i < seed.length; i++) {
      let c = seed.charCodeAt(i) & 0xffff;
      if (c < 0x80) {
        hash = (((hash << 5) + hash) + c) | 0;
      } else if (c < 0x800) {
        hash = (((hash << 5) + hash) + (0xC0 | (c >> 6))) | 0;
        hash = (((hash << 5) + hash) + (0x80 | (c & 0x3F))) | 0;
      } else {
        hash = (((hash << 5) + hash) + (0xE0 | (c >> 12))) | 0;
        hash = (((hash << 5) + hash) + (0x80 | ((c >> 6) & 0x3F))) | 0;
        hash = (((hash << 5) + hash) + (0x80 | (c & 0x3F))) | 0;
      }
    }
    // Normalize to unsigned 32-bit, matching the Java & 0xFFFFFFFFL.
    return hash >>> 0;
  },

  // Spread bits so palette/mood/avatar indices are decorrelated.
  _index(hash, modulus, salt) {
    // Use Math.imul for 32-bit multiply semantics identical to Java long*int low bits.
    let mixed = (Math.imul(hash >>> 0, 2654435761) + Math.imul(salt | 0, -1640531527)) >>> 0;
    return mixed % modulus;
  },

  paletteIndex(seed) {
    return this._index(this.hashSeed(seed), this._PALETTES.length, 1);
  },

  moodIndex(seed) {
    return this._index(this.hashSeed(seed), this._MOODS.length, 2);
  },

  starPoints(seed) {
    return 4 + this._index(this.hashSeed(seed), 5, 3);
  },

  // Prefer numeric id (stable across renames); fall back to pseudonym string.
  // id may be a number, numeric string, or null/undefined.
  resolveSeed(id, pseudonym) {
    if (id !== null && id !== undefined && id !== "") {
      const s = String(id).trim();
      if (s !== "" && /^[+-]?\d+$/.test(s)) {
        return "id:" + s;
      }
    }
    return pseudonym == null ? "" : pseudonym;
  },

  // Derive a full identity object from a seed. Pure & deterministic.
  deriveIdentity(seed) {
    const p = this._PALETTES[this.paletteIndex(seed)];
    const mood = this._MOODS[this.moodIndex(seed)];
    return {
      icon: "✦",
      color: p[0],
      secondaryColor: p[1],
      avatar: "derived",
      mood: mood,
      background: "linear-gradient(135deg, " + p[0] + " 0%, " + p[1] + " 100%)",
      borderColor: p[2],
      glowColor: "rgba(" + p[3] + ", " + p[4] + ", " + p[5] + ", 0.3)",
      derivedSeed: seed
    };
  },

  // Get personality data for a capsule.
  // Backward-compatible: pseudonym-only call still works. When id is provided
  // (number or numeric string), user capsules get a deterministic identity;
  // seed pseudonyms always keep their hardcoded identity.
  getPersonality(pseudonym, id) {
    if (pseudonym && Object.prototype.hasOwnProperty.call(this.seedIdentities, pseudonym)) {
      return this.seedIdentities[pseudonym];
    }
    return this.deriveIdentity(this.resolveSeed(id, pseudonym));
  },

  // Default personality for user capsules (kept for backward compatibility;
  // new callers should prefer getPersonality(pseudonym, id)).
  getDefaultPersonality() {
    return this.deriveIdentity("default");
  },

  // Apply personality styling to a capsule card.
  // Backward-compatible: id is optional; passing it gives user capsules a
  // deterministic, stable identity.
  applyPersonality(pseudonym, cardElement, id) {
    const personality = this.getPersonality(pseudonym, id);

    // Set CSS variables for this capsule
    cardElement.style.setProperty('--capsule-color', personality.color);
    cardElement.style.setProperty('--capsule-secondary', personality.secondaryColor);
    cardElement.style.setProperty('--capsule-border', personality.borderColor);
    cardElement.style.setProperty('--capsule-glow', personality.glowColor);

    // Add personality class
    cardElement.classList.add('capsule-card', `capsule-${personality.mood}`);

    return personality;
  },

  // Deterministic "star-sea" SVG avatar glyph. Pure: same seed -> same SVG.
  // Parameters (star point count, radii, orbit, rotation) derive from the hash.
  // Static — respects prefers-reduced-motion (no <animate> / flashing).
  generateStarSeaSvg(seed, pixelSize) {
    const hash = this.hashSeed(seed);
    const points = this.starPoints(seed);
    const cx = 50, cy = 50;
    const innerR = 14 + (hash % 6);          // 14..19
    const outerR = 30 + ((hash >>> 3) % 8);  // 30..37
    const orbitR = 40 + ((hash >>> 6) % 6);  // 40..45
    const orbitCount = 1 + ((hash >>> 9) % 3); // 1..3 small companion dots
    const baseRot = ((hash >>> 12) % 360);
    const id = this.deriveIdentity(seed);

    // Build the star polygon path.
    let starPath = "";
    const total = points * 2;
    for (let i = 0; i < total; i++) {
      const r = (i % 2 === 0) ? outerR : innerR;
      const angle = (baseRot + (i * 360 / total)) * Math.PI / 180;
      const x = cx + r * Math.cos(angle);
      const y = cy + r * Math.sin(angle);
      starPath += (i === 0 ? "M" : "L") + x.toFixed(2) + " " + y.toFixed(2);
    }
    starPath += "Z";

    // Companion dots orbiting the star.
    let dots = "";
    for (let i = 0; i < orbitCount; i++) {
      const a = ((baseRot * 2 + i * (360 / orbitCount)) % 360) * Math.PI / 180;
      const dx = cx + orbitR * Math.cos(a);
      const dy = cy + orbitR * Math.sin(a);
      const dr = 1.6 + ((hash >>> (15 + i)) % 2);
      dots += `<circle cx="${dx.toFixed(2)}" cy="${dy.toFixed(2)}" r="${dr.toFixed(2)}" fill="${id.color}" opacity="0.85"/>`;
    }

    return `<svg class="capsule-stars-svg" viewBox="0 0 100 100" width="${pixelSize}" height="${pixelSize}" ` +
           `role="img" aria-label="共鸣体星海标识" focusable="false" ` +
           `style="display:block">` +
           `<circle cx="${cx}" cy="${cy}" r="46" fill="${id.background}"/>` +
           `<path d="${starPath}" fill="${id.color}" opacity="0.92"/>` +
           `<path d="${starPath}" fill="none" stroke="${id.secondaryColor}" stroke-width="1.2" opacity="0.7"/>` +
           dots +
           `</svg>`;
  },

  // Generate avatar HTML for a capsule. id optional (backward-compatible).
  generateAvatar(pseudonym, size = 'medium', id) {
    const personality = this.getPersonality(pseudonym, id);
    const sizes = {
      small: 24,
      medium: 48,
      large: 72
    };
    const pixelSize = sizes[size] || sizes.medium;

    // Seed capsules keep their emoji avatars; user capsules get a deterministic
    // star-sea SVG glyph unique to their stable id.
    if (personality.avatar === "derived") {
      const seed = personality.derivedSeed;
      return `<div class="capsule-avatar capsule-avatar-${size} capsule-avatar-derived" ` +
             `style="width:${pixelSize}px;height:${pixelSize}px;background:${personality.background};border-color:${personality.borderColor};">` +
             this.generateStarSeaSvg(seed, pixelSize) +
             `<div class="avatar-glow" style="background:${personality.glowColor};"></div>` +
             `</div>`;
    }

    return `
      <div class="capsule-avatar capsule-avatar-${size} capsule-avatar-${personality.avatar}"
           style="width: ${pixelSize}px; height: ${pixelSize}px; background: ${personality.background}; border-color: ${personality.borderColor};">
        <span class="avatar-icon">${personality.icon}</span>
        <div class="avatar-glow" style="background: ${personality.glowColor};"></div>
      </div>
    `;
  },

  // Generate theme color gradient text. id optional (backward-compatible).
  gradientText(pseudonym, text, id) {
    const personality = this.getPersonality(pseudonym, id);
    return `<span class="capsule-gradient-text" style="background: ${personality.background}; -webkit-background-clip: text; -webkit-text-fill-color: transparent;">${text}</span>`;
  },

  // Create personality badge. id optional (backward-compatible).
  createBadge(pseudonym, id) {
    const personality = this.getPersonality(pseudonym, id);
    return `<span class="capsule-badge" style="background: ${personality.background}; color: white; border-color: ${personality.borderColor};">
              ${personality.icon} ${this.getMoodLabel(personality.mood)}
            </span>`;
  },

  // Get mood label in Chinese
  getMoodLabel(mood) {
    const labels = {
      steady: "沉稳",
      curious: "好奇",
      dreamy: "梦幻",
      contemplative: "沉思",
      passionate: "热情",
      gentle: "温柔",
      intimate: "亲密",
      patient: "耐心",
      hopeful: "希望",
      quiet: "安静",
      warm: "温暖",
      default: "温和"
    };
    return labels[mood] || labels.default;
  },

  // Initialize personality system CSS
  init() {
    if (document.getElementById('capsule-personality-css')) return;

    const style = document.createElement('style');
    style.id = 'capsule-personality-css';
    style.textContent = `
      /* Capsule Card Styles */
      .capsule-card {
        position: relative;
        transition: all 0.3s ease;
        border: 1px solid var(--capsule-border, rgba(143, 169, 148, 0.3));
      }

      .capsule-card:hover {
        box-shadow: 0 8px 24px var(--capsule-glow, rgba(143, 169, 148, 0.2));
        transform: translateY(-2px);
      }

      /* Capsule Breathing Animation */
      @keyframes capsule-breathe {
        0%, 100% { transform: scale(1); opacity: 0.8; }
        50% { transform: scale(1.05); opacity: 1; }
      }

      .capsule-steady { animation-duration: 7s; }
      .capsule-curious { animation-duration: 4s; }
      .capsule-dreamy { animation-duration: 8s; }
      .capsule-contemplative { animation-duration: 6s; }
      .capsule-passionate { animation-duration: 3s; }
      .capsule-gentle { animation-duration: 5s; }
      .capsule-intimate { animation-duration: 9s; }
      .capsule-patient { animation-duration: 10s; }

      /* Avatar Styles */
      .capsule-avatar {
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: 50%;
        border: 2px solid;
        overflow: hidden;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      }

      .capsule-avatar-small { font-size: 12px; }
      .capsule-avatar-medium { font-size: 20px; }
      .capsule-avatar-large { font-size: 28px; }

      .capsule-stars-svg {
        position: relative;
        z-index: 2;
        max-width: 100%;
        max-height: 100%;
      }

      .avatar-icon {
        position: relative;
        z-index: 2;
      }

      .avatar-glow {
        position: absolute;
        top: -50%;
        left: -50%;
        width: 200%;
        height: 200%;
        border-radius: 50%;
        animation: capsule-breathe 5s ease-in-out infinite;
        z-index: 1;
      }

      /* Badge Styles */
      .capsule-badge {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 4px 10px;
        border-radius: 999px;
        font-size: 0.82rem;
        border: 1px solid;
        font-weight: 500;
      }

      /* Gradient Text */
      .capsule-gradient-text {
        font-weight: 600;
      }

      /* Dark star theme overrides */
      body.dark-star .capsule-card {
        background: rgba(26, 26, 46, 0.6);
      }
    `;

    document.head.appendChild(style);
  }
};

// Auto-initialize
if (typeof document !== 'undefined') {
  document.addEventListener('DOMContentLoaded', () => {
    CapsulePersonality.init();
  });
}
