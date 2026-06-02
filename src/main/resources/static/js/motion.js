/* ── Inner Cosmos Motion System ── */
window.ICMotion = {
  // Time-based motion parameters
  getMotionParams() {
    const hour = new Date().getHours();
    const isNight = hour >= 18 || hour < 6;

    return {
      baseSpeed: isNight ? 0.6 : 1.0,
      flowIntensity: isNight ? 0.4 : 0.7,
      breatheDuration: isNight ? 7.2 : 5.6,
      starIntensity: isNight ? 1.0 : 0.0,
      cloudIntensity: isNight ? 0.2 : 0.8
    };
  },

  // Apply flowing animation to elements
  applyFlowAnimation(element, duration = 3000) {
    if (!element) return;

    const params = this.getMotionParams();
    element.style.setProperty('--motion-speed', params.baseSpeed.toString());
    element.style.setProperty('--motion-intensity', params.flowIntensity.toString());
    element.classList.add('flowing');
  },

  // Create breathing effect for stars
  createBreatheEffect(element) {
    if (!element) return;

    const params = this.getMotionParams();
    const duration = params.breatheDuration * 1000;

    element.style.animation = `breath ${duration}ms ease-in-out infinite`;
  },

  // Create floating particles effect
  createParticles(container, count = 20) {
    if (!container) return;

    const params = this.getMotionParams();
    container.innerHTML = '';

    for (let i = 0; i < count; i++) {
      const particle = document.createElement('div');
      particle.className = 'particle';
      particle.style.cssText = `
        position: absolute;
        width: ${Math.random() * 4 + 2}px;
        height: ${Math.random() * 4 + 2}px;
        background: rgba(255, 255, 255, ${params.baseSpeed * 0.3});
        border-radius: 50%;
        left: ${Math.random() * 100}%;
        top: ${Math.random() * 100}%;
        animation: float ${Math.random() * 5000 + 5000}ms ease-in-out infinite;
        animation-delay: ${Math.random() * 2000}ms;
      `;
      container.appendChild(particle);
    }
  },

  // Weather-based motion adaptation
  adaptToWeather(weatherType) {
    const body = document.body;

    // Remove existing weather classes
    body.classList.remove('weather-rain', 'weather-storm', 'weather-fog', 'weather-snow');

    switch (weatherType) {
      case 'RAIN':
        body.classList.add('weather-rain');
        break;
      case 'STORM':
        body.classList.add('weather-storm');
        break;
      case 'FOG':
        body.classList.add('weather-fog');
        break;
      case 'SNOW':
        body.classList.add('weather-snow');
        break;
    }
  },

  // Initialize motion system
  init() {
    // Add motion CSS
    this.injectMotionCSS();

    // Apply time-based motion
    this.applyTimeBasedMotion();

    // Start motion loop
    this.startMotionLoop();
  },

  injectMotionCSS() {
    if (document.getElementById('motion-css')) return;

    const style = document.createElement('style');
    style.id = 'motion-css';
    style.textContent = `
      @keyframes float {
        0%, 100% { transform: translate(0, 0); opacity: 0.6; }
        50% { transform: translate(${Math.random() * 40 - 20}px, ${Math.random() * 40 - 20}px); opacity: 1; }
      }

      @keyframes drift {
        0% { transform: translateX(-100%); }
        100% { transform: translateX(100vw); }
      }

      @keyframes pulse-soft {
        0%, 100% { opacity: 0.3; transform: scale(1); }
        50% { opacity: 0.7; transform: scale(1.05); }
      }

      .flowing {
        animation: flow ${this.getMotionParams().breatheDuration}s ease-in-out infinite;
      }

      @keyframes flow {
        0%, 100% { transform: translateY(0px); }
        50% { transform: translateY(-5px); }
      }

      .particle {
        pointer-events: none;
      }

      .weather-rain .particle {
        background: rgba(134, 152, 169, 0.6);
        animation: rain-drop linear infinite;
      }

      @keyframes rain-drop {
        0% { transform: translateY(-10px); opacity: 0; }
        10% { opacity: 1; }
        90% { opacity: 1; }
        100% { transform: translateY(100vh); opacity: 0; }
      }

      .weather-snow .particle {
        background: rgba(255, 255, 255, 0.8);
        animation: snow-fall linear infinite;
      }

      @keyframes snow-fall {
        0% { transform: translateY(-10px) rotate(0deg); opacity: 0; }
        10% { opacity: 1; }
        90% { opacity: 1; }
        100% { transform: translateY(100vh) rotate(360deg); opacity: 0; }
      }
    `;
    document.head.appendChild(style);
  },

  applyTimeBasedMotion() {
    const params = this.getMotionParams();
    document.body.style.setProperty('--motion-speed', params.baseSpeed.toString());
    document.body.style.setProperty('--motion-intensity', params.flowIntensity.toString());
  },

  startMotionLoop() {
    // Update motion parameters every minute
    setInterval(() => {
      this.applyTimeBasedMotion();
    }, 60000);
  },

  // Create ripple effect
  createRipple(x, y, container = document.body) {
    const ripple = document.createElement('div');
    ripple.className = 'ripple';
    ripple.style.cssText = `
      position: absolute;
      left: ${x}px;
      top: ${y}px;
      width: 0;
      height: 0;
      border-radius: 50%;
      background: radial-gradient(circle, rgba(143, 163, 148, 0.3) 0%, transparent 70%);
      transform: translate(-50%, -50%);
      animation: ripple-effect 600ms ease-out forwards;
      pointer-events: none;
    `;

    container.appendChild(ripple);
    setTimeout(() => ripple.remove(), 600);
  },

  // Smooth page transitions
  transitionTo(url, duration = 400) {
    document.body.style.opacity = '0';
    document.body.style.transition = `opacity ${duration}ms ease`;

    setTimeout(() => {
      window.location.href = url;
    }, duration);
  }
};

// Auto-initialize
if (typeof document !== 'undefined') {
  document.addEventListener('DOMContentLoaded', () => {
    ICMotion.init();
  });
}
