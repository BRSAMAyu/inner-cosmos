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

  // Get personality data for a capsule
  getPersonality(pseudonym) {
    return this.seedIdentities[pseudonym] || this.getDefaultPersonality();
  },

  // Default personality for user capsules
  getDefaultPersonality() {
    return {
      icon: "✨",
      color: "#8FA994",
      secondaryColor: "#C8D4C4",
      avatar: "default",
      mood: "gentle",
      background: "linear-gradient(135deg, #8FA994 0%, #C8D4C4 100%)",
      borderColor: "#6B8974",
      glowColor: "rgba(143, 169, 148, 0.3)"
    };
  },

  // Apply personality styling to a capsule card
  applyPersonality(pseudonym, cardElement) {
    const personality = this.getPersonality(pseudonym);

    // Set CSS variables for this capsule
    cardElement.style.setProperty('--capsule-color', personality.color);
    cardElement.style.setProperty('--capsule-secondary', personality.secondaryColor);
    cardElement.style.setProperty('--capsule-border', personality.borderColor);
    cardElement.style.setProperty('--capsule-glow', personality.glowColor);

    // Add personality class
    cardElement.classList.add('capsule-card', `capsule-${personality.mood}`);

    return personality;
  },

  // Generate avatar HTML for a capsule
  generateAvatar(pseudonym, size = 'medium') {
    const personality = this.getPersonality(pseudonym);
    const sizes = {
      small: 24,
      medium: 48,
      large: 72
    };
    const pixelSize = sizes[size] || sizes.medium;

    return `
      <div class="capsule-avatar capsule-avatar-${size} capsule-avatar-${personality.avatar}"
           style="width: ${pixelSize}px; height: ${pixelSize}px; background: ${personality.background}; border-color: ${personality.borderColor};">
        <span class="avatar-icon">${personality.icon}</span>
        <div class="avatar-glow" style="background: ${personality.glowColor};"></div>
      </div>
    `;
  },

  // Generate theme color gradient text
  gradientText(pseudonym, text) {
    const personality = this.getPersonality(pseudonym);
    return `<span class="capsule-gradient-text" style="background: ${personality.background}; -webkit-background-clip: text; -webkit-text-fill-color: transparent;">${text}</span>`;
  },

  // Create personality badge
  createBadge(pseudonym) {
    const personality = this.getPersonality(pseudonym);
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
