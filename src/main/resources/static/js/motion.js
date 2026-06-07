/* ── Inner Cosmos Motion System ── */
window.ICMotion = {
  // IntersectionObserver for reveal animations
  revealObserver: null,
  cursorDot: null,
  cursorRing: null,
  cursorTrails: [],
  mousePos: { x: 0, y: 0 },
  lastMousePos: { x: 0, y: 0 },
  isDragging: false,
  initialized: false,
  cursorReady: false,
  cursorSeen: false,
  motionLoopStarted: false,
  scrollIndicatorReady: false,

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

  // Initialize enhanced cursor system
  initCursor() {
    if (this.cursorReady) return;
    // Skip on mobile/touch devices
    if ('ontouchstart' in window || window.matchMedia('(max-width: 768px)').matches) {
      document.documentElement.classList.add('mobile');
      return;
    }

    document.querySelectorAll('.flow-cursor-dot, .flow-cursor-ring').forEach(el => el.remove());

    // Create cursor elements
    this.cursorDot = document.createElement('div');
    this.cursorDot.className = 'flow-cursor-dot';

    this.cursorRing = document.createElement('div');
    this.cursorRing.className = 'flow-cursor-ring';

    document.body.appendChild(this.cursorDot);
    document.body.appendChild(this.cursorRing);
    this.cursorReady = true;

    // Track mouse movement
    document.addEventListener('mousemove', (e) => {
      this.mousePos.x = e.clientX;
      this.mousePos.y = e.clientY;
      if (!this.cursorSeen) {
        this.lastMousePos.x = e.clientX;
        this.lastMousePos.y = e.clientY;
        this.cursorSeen = true;
      }

      // Immediate update for dot
      this.cursorDot.style.left = `${e.clientX}px`;
      this.cursorDot.style.top = `${e.clientY}px`;

      // Smooth follow for ring
      this.updateCursorRing();
    });

    // Track cursor state based on hovered elements
    document.addEventListener('mouseover', (e) => {
      const target = e.target;
      this.updateCursorState(target);
    });

    // Handle dragging state
    document.addEventListener('mousedown', () => {
      this.isDragging = true;
      this.cursorRing.classList.add('dragging');
      this.cursorDot.classList.add('dragging');
    });

    document.addEventListener('mouseup', () => {
      this.isDragging = false;
      this.cursorRing.classList.remove('dragging');
      this.cursorDot.classList.remove('dragging');
    });

    // Start smooth ring animation loop
    this.animateCursorRing();
  },

  updateCursorRing() {
    if (!this.cursorRing) return;

    // Smooth lerp for ring
    const lerp = (start, end, factor) => start + (end - start) * factor;
    const smoothFactor = 0.15;

    const targetX = this.mousePos.x;
    const targetY = this.mousePos.y;

    this.lastMousePos.x = lerp(this.lastMousePos.x, targetX, smoothFactor);
    this.lastMousePos.y = lerp(this.lastMousePos.y, targetY, smoothFactor);

    this.cursorRing.style.left = `${this.lastMousePos.x}px`;
    this.cursorRing.style.top = `${this.lastMousePos.y}px`;
  },

  animateCursorRing() {
    if (this.cursorRing) {
      this.updateCursorRing();
      requestAnimationFrame(() => this.animateCursorRing());
    }
  },

  updateCursorState(target) {
    if (!this.cursorRing || !this.cursorDot) return;

    // Reset all states
    this.cursorRing.classList.remove('hovering', 'interactive', 'encounter');
    this.cursorDot.classList.remove('hovering', 'interactive', 'encounter');

    // Determine cursor state based on element
    if (this.isDragging) return;

    if (target.closest('.star, .encounter-element')) {
      this.cursorRing.classList.add('encounter');
      this.cursorDot.classList.add('encounter');
    } else if (target.closest('input, textarea, select, [contenteditable]')) {
      this.cursorRing.classList.add('interactive');
      this.cursorDot.classList.add('interactive');
    } else if (target.closest('a, button, .button, .card, .interactive')) {
      this.cursorRing.classList.add('hovering');
      this.cursorDot.classList.add('hovering');
    }
  },

  createCursorTrail() {
    if ('ontouchstart' in window) return;

    const trail = document.createElement('div');
    trail.className = 'flow-cursor-trail';
    trail.style.left = this.mousePos.x + 'px';
    trail.style.top = this.mousePos.y + 'px';
    document.body.appendChild(trail);

    setTimeout(() => trail.remove(), 800);
  },

  // Initialize reveal observer for scroll animations
  initRevealObserver() {
    const options = {
      root: null,
      rootMargin: '0px 0px -100px 0px',
      threshold: 0.1
    };

    this.revealObserver = new IntersectionObserver((entries) => {
      entries.forEach((entry, index) => {
        if (entry.isIntersecting) {
          // Stagger animation based on index
          setTimeout(() => {
            entry.target.classList.add('revealed');
          }, index * 80);

          this.revealObserver.unobserve(entry.target);
        }
      });
    }, options);

    // Observe all reveal elements
    document.querySelectorAll('.reveal').forEach(el => {
      this.revealObserver.observe(el);
    });

    // Initialize parallax scrolling
    this.initParallaxScroll();
  },

  // Parallax scroll effects
  initParallaxScroll() {
    let ticking = false;

    const updateParallax = () => {
      const scrollY = window.scrollY;
      const scrollPercent = scrollY / (document.body.scrollHeight - window.innerHeight);

      // Parallax for flowing background
      const bg = document.querySelector('.flowing-background');
      if (bg) {
        bg.style.transform = `translateY(${scrollY * 0.3}px)`;
      }

      // Parallax for hero sections
      const heroes = document.querySelectorAll('.hero');
      heroes.forEach(hero => {
        const rect = hero.getBoundingClientRect();
        const speed = 0.4;
        const yPos = -(rect.top * speed);
        hero.style.transform = `translateY(${yPos}px)`;
        hero.style.opacity = Math.max(0, 1 - (scrollY * 0.002));
      });

      // Parallax for cards with depth
      document.querySelectorAll('.parallax-card').forEach(card => {
        const rect = card.getBoundingClientRect();
        const centerY = rect.top + rect.height / 2;
        const viewportCenter = window.innerHeight / 2;
        const distanceFromCenter = (centerY - viewportCenter) / viewportCenter;

        card.style.transform = `
          perspective(1000px)
          rotateX(${distanceFromCenter * 3}deg)
          translateY(${distanceFromCenter * 10}px)
        `;
      });

      // Parallax for star field
      const starfield = document.querySelector('.starfield');
      if (starfield) {
        const stars = starfield.querySelectorAll('.star');
        stars.forEach((star, i) => {
          const speed = 0.1 + (i % 3) * 0.05;
          const yPos = -(scrollY * speed);
          star.style.marginTop = `${yPos}px`;
        });
      }

      ticking = false;
    };

    window.addEventListener('scroll', () => {
      if (!ticking) {
        window.requestAnimationFrame(() => {
          updateParallax();
          ticking = false;
        });
        ticking = true;
      }
    }, { passive: true });

    // Initial update
    updateParallax();
  },

  // Horizontal scroll parallax
  initHorizontalParallax(container) {
    if (!container) return;

    container.addEventListener('scroll', () => {
      const scrollLeft = container.scrollLeft;
      const maxScroll = container.scrollWidth - container.clientWidth;
      const scrollPercent = scrollLeft / maxScroll;

      const layers = container.querySelectorAll('.parallax-layer');
      layers.forEach((layer, i) => {
        const speed = 0.5 + (i * 0.2);
        const xPos = -(scrollPercent * speed * 100);
        layer.style.transform = `translateX(${xPos}%)`;
      });
    }, { passive: true });
  },

  // Add flowing background layers
  initFlowingBackground() {
    if (document.querySelector('.flowing-background')) return;
    const bg = document.createElement('div');
    bg.className = 'flowing-background';
    document.body.appendChild(bg);

    const particles = document.createElement('div');
    particles.className = 'flowing-particles';
    bg.appendChild(particles);

    const texture = document.createElement('div');
    texture.className = 'flowing-texture';
    bg.appendChild(texture);

    const inkwash = document.createElement('div');
    inkwash.className = 'flowing-inkwash';
    bg.appendChild(inkwash);
  },

  // Initialize motion system
  init() {
    if (this.initialized) return;
    /* ════════ Respect prefers-reduced-motion (P3-2) ════════ */
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      window.__icMotionDisabled = true;
      this.initialized = true;  // mark loaded so we don't re-check
      return;
    }
    /* Live toggle: if user changes preference mid-session, reactivate */
    if (window.matchMedia) {
      const mq = window.matchMedia('(prefers-reduced-motion: reduce)');
      const onChange = (e) => {
        if (e.matches) {
          window.__icMotionDisabled = true;
          this.teardownMotion();
        } else if (window.__icMotionDisabled) {
          window.__icMotionDisabled = false;
          this.initialized = false;
          this.init();
        }
      };
      if (mq.addEventListener) mq.addEventListener('change', onChange);
      else if (mq.addListener) mq.addListener(onChange);
    }
    this.initialized = true;
    // Add motion CSS
    this.injectMotionCSS();

    // Apply time-based motion
    this.applyTimeBasedMotion();

    // Start motion loop
    this.startMotionLoop();

    // Initialize enhanced cursor
    this.initCursor();

    // Initialize reveal observer
    this.initRevealObserver();

    // Initialize flowing background
    this.initFlowingBackground();

    // Initialize scroll progress bar
    this.initScrollIndicator();

    // Expose stagger function globally for manual use
    window.IC = window.IC || {};
    window.IC.motion = {
      fadeIn: (el) => {
        el.style.opacity = '0';
        el.style.transform = 'translateY(20px)';
        requestAnimationFrame(() => {
          el.style.transition = 'opacity 0.6s ease, transform 0.6s ease';
          el.style.opacity = '1';
          el.style.transform = 'translateY(0)';
        });
      },
      stagger: (container) => {
        const children = container.children;
        Array.from(children).forEach((child, i) => {
          child.style.opacity = '0';
          child.style.transform = 'translateY(10px)';
          setTimeout(() => {
            child.style.transition = 'opacity 0.4s ease, transform 0.4s ease';
            child.style.opacity = '1';
            child.style.transform = 'translateY(0)';
          }, i * 60);
        });
      },
      starAppear: (star) => {
        star.style.transform = 'scale(0)';
        star.style.opacity = '0';
        requestAnimationFrame(() => {
          star.style.transition = 'transform 0.8s var(--ease-bloom), opacity 0.8s ease';
          star.style.transform = 'scale(1)';
          star.style.opacity = '1';
        });
      }
    };
  },

  /* Teardown: stop all RAF loops and remove injected DOM. Used by prefers-reduced-motion. */
  teardownMotion() {
    if (this.cursorDot) { this.cursorDot.remove(); this.cursorDot = null; }
    if (this.cursorRing) { this.cursorRing.remove(); this.cursorRing = null; }
    if (this.cursorTrails) { this.cursorTrails.forEach(t => t.remove && t.remove()); this.cursorTrails = []; }
    document.querySelectorAll('.flowing-inkwash, .scroll-progress, .flow-cursor-dot, .flow-cursor-ring').forEach(el => el.remove());
    document.body.classList.remove('cursor-active', 'flow-page-enter', 'flow-page-ready', 'flow-page-leave');
  },

  // Initialize scroll progress indicator
  initScrollIndicator() {
    if (this.scrollIndicatorReady) return;
    this.scrollIndicatorReady = true;
    document.querySelectorAll('.scroll-progress').forEach(el => el.remove());
    // Create progress bar
    const progressBar = document.createElement('div');
    progressBar.className = 'scroll-progress';
    document.body.appendChild(progressBar);

    // Update progress on scroll
    let ticking = false;
    const updateProgress = () => {
      const scrollY = window.scrollY;
      const maxScroll = document.body.scrollHeight - window.innerHeight;
      const progress = (scrollY / maxScroll) * 100;
      progressBar.style.width = `${Math.min(100, progress)}%`;
      ticking = false;
    };

    window.addEventListener('scroll', () => {
      if (!ticking) {
        window.requestAnimationFrame(() => {
          updateProgress();
          ticking = false;
        });
        ticking = true;
      }
    }, { passive: true });

    // Initialize scroll-triggered animations
    this.initScrollAnimations();
  },

  // Initialize scroll-triggered animations
  initScrollAnimations() {
    const observerOptions = {
      root: null,
      rootMargin: '0px 0px -100px 0px',
      threshold: 0.15
    };

    const scrollObserver = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
        }
      });
    }, observerOptions);

    // Observe all scroll-triggered elements
    document.querySelectorAll('.scroll-fade-in, .scroll-scale, .scroll-slide-left, .scroll-slide-right').forEach(el => {
      scrollObserver.observe(el);
    });
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
    if (this.motionLoopStarted) return;
    this.motionLoopStarted = true;
    // Update motion parameters every minute
    setInterval(() => {
      this.applyTimeBasedMotion();
    }, 60000);
  },

  // Create ripple effect
  createRipple(x, y, container = document.body) {
    const ripple = document.createElement('div');
    ripple.className = 'touch-ripple';
    ripple.style.left = x + 'px';
    ripple.style.top = y + 'px';
    ripple.style.width = '20px';
    ripple.style.height = '20px';
    ripple.style.marginLeft = '-10px';
    ripple.style.marginTop = '-10px';

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
