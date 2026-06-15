/* ── Inner Cosmos Time System ── */
window.ICTimeSystem = {
  // Time classes based on local time and sunset
  timeClasses: ['time-dawn', 'time-morning', 'time-noon', 'time-afternoon', 'time-dusk', 'time-night', 'time-deep-night'],

  // Current time state
  currentTimeState: {
    timeClass: null,
    isDay: null,
    transitionProgress: 0,
    solarNoon: null,
    sunset: null,
    sunrise: null
  },

  // User location (default: Beijing)
  userLocation: {
    lat: 39.9042,  // Beijing
    lon: 116.4074
  },

  // API endpoints for sunset data
  sunsetAPI: {
    enabled: true,
    // Sunrise-Sunset API (free, no API key required)
    url: 'https://api.sunrise-sunset.org/json',
    // Cached sunset data with 1-day expiry
    cache: null,
    cacheTime: null,
    cacheDuration: 24 * 60 * 60 * 1000 // 24 hours
  },

  // Color palettes for smooth transitions
  colorPalettes: {
    dawn: { warm: '#FAF2E7', secondary: '#F7F2EC', glow: 'rgba(240, 216, 176, 0.18)' },
    morning: { warm: '#F7F2EC', secondary: '#E8EDE4', glow: 'rgba(181, 194, 176, 0.18)' },
    noon: { warm: '#F8F3EC', secondary: '#E9EEE6', glow: 'rgba(184, 194, 168, 0.16)' },
    afternoon: { warm: '#F7F2EC', secondary: '#EFE6DA', glow: 'rgba(216, 181, 181, 0.16)' },
    dusk: { warm: '#F5EEE7', secondary: '#E8DDE4', glow: 'rgba(144, 128, 160, 0.16)' },
    night: { warm: '#F4EFE8', secondary: '#E7E2DA', glow: 'rgba(156, 175, 154, 0.14)' },
    deepNight: { warm: '#F2ECE4', secondary: '#E4DFD8', glow: 'rgba(156, 175, 154, 0.14)' },
    'deep-night': { warm: '#F2ECE4', secondary: '#E4DFD8', glow: 'rgba(156, 175, 154, 0.14)' }
  },

  // Initialize
  init() {
    if (this.__icInitialized) return;
    this.__icInitialized = true;
    this.loadUserLocation();
    this.fetchSunsetTimes().then(() => {
      this.startClock();
      this.startTimeTransitionChecker();
      this.updateTime();
    });
  },

  // Load user location from localStorage or request
  loadUserLocation() {
    const savedLat = localStorage.getItem('userLat');
    const savedLon = localStorage.getItem('userLon');

    if (savedLat && savedLon) {
      this.userLocation.lat = parseFloat(savedLat);
      this.userLocation.lon = parseFloat(savedLon);
      this.resolveLocationLabel();
    } else {
      // Request geolocation
      if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(
          (pos) => {
            this.userLocation.lat = pos.coords.latitude;
            this.userLocation.lon = pos.coords.longitude;
            localStorage.setItem('userLat', this.userLocation.lat.toString());
            localStorage.setItem('userLon', this.userLocation.lon.toString());
            this.resolveLocationLabel();
            // Re-fetch sunset times with new location
            this.fetchSunsetTimes();
          },
          (err) => {
            console.log('Geolocation not available, using default location:', err.message);
          }
        );
      }
    }
  },

  async resolveLocationLabel() {
    try {
      const url = `https://nominatim.openstreetmap.org/reverse?lat=${this.userLocation.lat}&lon=${this.userLocation.lon}&format=json&accept-language=zh-CN`;
      const response = await fetch(url, { headers: { 'Accept': 'application/json' } });
      if (!response.ok) return;
      const data = await response.json();
      const a = data?.address || {};
      const parts = [a.city || a.town || a.village || a.county, a.state, a.country].filter(Boolean);
      const label = Array.from(new Set(parts)).join(" · ");
      if (label) {
        localStorage.setItem('ic_location_label', label);
        window.dispatchEvent(new CustomEvent('locationChanged', { detail: { label, lat: this.userLocation.lat, lon: this.userLocation.lon } }));
      }
    } catch (error) {
      console.warn('Failed to resolve location label:', error);
    }
  },

  // Fetch sunset times from API
  async fetchSunsetTimes() {
    // Check cache first
    const now = Date.now();
    if (this.sunsetAPI.cache && this.sunsetAPI.cacheTime &&
        (now - this.sunsetAPI.cacheTime) < this.sunsetAPI.cacheDuration) {
      console.log('Using cached sunset times');
      this.currentTimeState.sunrise = new Date(this.sunsetAPI.cache.sunrise);
      this.currentTimeState.sunset = new Date(this.sunsetAPI.cache.sunset);
      this.currentTimeState.solarNoon = new Date(this.sunsetAPI.cache.solar_noon);
      return;
    }

    if (!this.sunsetAPI.enabled) {
      this.useApproximateSunTimes();
      return;
    }

    try {
      const date = new Date();
      const url = `${this.sunsetAPI.url}?lat=${this.userLocation.lat}&lng=${this.userLocation.lon}&date=${date.toISOString().split('T')[0]}&formatted=0`;

      const response = await fetch(url);
      const data = await response.json();

      if (data.results) {
        const sunrise = new Date(data.results.sunrise);
        const sunset = new Date(data.results.sunset);
        const solarNoon = new Date(data.results.solar_noon);

        // Adjust for local timezone
        const offset = date.getTimezoneOffset() * 60 * 1000;
        this.currentTimeState.sunrise = new Date(sunrise.getTime() - offset);
        this.currentTimeState.sunset = new Date(sunset.getTime() - offset);
        this.currentTimeState.solarNoon = new Date(solarNoon.getTime() - offset);

        // Cache the results
        this.sunsetAPI.cache = data.results;
        this.sunsetAPI.cacheTime = now;

        console.log('Sunset times fetched:', {
          sunrise: this.currentTimeState.sunrise,
          sunset: this.currentTimeState.sunset,
          solarNoon: this.currentTimeState.solarNoon
        });
      }
    } catch (error) {
      console.warn('Failed to fetch sunset times, using approximation:', error);
      this.useApproximateSunTimes();
    }
  },

  // Fallback to approximate sun times
  useApproximateSunTimes() {
    const date = new Date();
    const sunTimes = this.calculateApproximateSunTimes(date, this.userLocation.lat, this.userLocation.lon);
    this.currentTimeState.sunrise = sunTimes.sunrise;
    this.currentTimeState.sunset = sunTimes.sunset;
    this.currentTimeState.solarNoon = sunTimes.solarNoon;
  },

  // Calculate sun times using NOAA algorithm
  getSunTimes(date = new Date()) {
    const lat = this.userLocation.lat;
    const lon = this.userLocation.lon;

    // Simplified sunset calculation (for production, use a proper library)
    // This is an approximation - replaces complex NOAA algorithm
    const baseSunset = this.calculateApproximateSunset(date, lat, lon);
    const baseSunrise = new Date(baseSunset);
    baseSunrise.setHours(baseSunset.getHours() - 12); // Approximate sunrise 12h before sunset

    return {
      sunrise: baseSunrise,
      sunset: baseSunset,
      isDay: this.isDaytime(date, baseSunrise, baseSunset)
    };
  },

  // Approximate sunset calculation (simplified for demo)
  calculateApproximateSunset(date, lat, lon) {
    // Very rough approximation based on latitude and month
    const month = date.getMonth();
    const hour = 18; // Default 6pm

    // Adjust by latitude (higher latitude = later sunset in summer)
    const latOffset = (lat - 40) * 0.1;
    const monthOffset = Math.sin((month / 12) * Math.PI * 2) * 1.5; // Seasonal variation

    const sunsetHour = hour + latOffset + monthOffset;
    const sunset = new Date(date);
    sunset.setHours(Math.max(16, Math.min(20, sunsetHour)), 0, 0);

    return sunset;
  },

  // Calculate approximate sun times with sunrise
  calculateApproximateSunTimes(date, lat, lon) {
    const month = date.getMonth();
    const dayOfYear = Math.floor((date - new Date(date.getFullYear(), 0, 0)) / 86400000);

    // Simple approximation
    const baseSunsetHour = 18;
    const latOffset = (lat - 40) * 0.1;
    const seasonalOffset = Math.sin((dayOfYear / 365) * 2 * Math.PI - Math.PI / 2) * 2;

    const sunsetHour = Math.max(16, Math.min(20, baseSunsetHour + latOffset + seasonalOffset));
    const sunriseHour = Math.max(5, Math.min(8, 7 - latOffset - seasonalOffset));

    const sunset = new Date(date);
    sunset.setHours(sunsetHour, 0, 0);

    const sunrise = new Date(date);
    sunrise.setHours(sunriseHour, 0, 0);

    const solarNoon = new Date(date);
    solarNoon.setHours(12, 0, 0);

    return { sunrise, sunset, solar_noon: solarNoon };
  },

  // Check if it's daytime
  isDaytime(date, sunrise, sunset) {
    const hour = date.getHours();
    return hour >= sunrise.getHours() && hour < sunset.getHours();
  },

  // Determine time class based on current time and sun times
  getTimeClass(date = new Date()) {
    const sunTimes = this.getSunTimes(date);
    const hour = date.getHours();
    const minute = date.getMinutes();
    const hourDecimal = hour + minute / 60;

    // Use actual sunset times if available
    const sunriseTime = this.currentTimeState.sunrise || sunTimes.sunrise;
    const sunsetTime = this.currentTimeState.sunset || sunTimes.sunset;

    // Time windows (with 30min buffers around sunrise/sunset)
    // Dawn: sunrise-30min to sunrise+90min
    const dawnStart = sunriseTime.getTime() - 30 * 60 * 1000;
    const dawnEnd = sunriseTime.getTime() + 90 * 60 * 1000;

    // Dusk: sunset-60min to sunset+90min
    const duskStart = sunsetTime.getTime() - 60 * 60 * 1000;
    const duskEnd = sunsetTime.getTime() + 90 * 60 * 1000;

    const now = date.getTime();

    if (now >= dawnStart && now < dawnEnd) return 'time-dawn';
    if (hourDecimal >= 7 && hourDecimal < 11) return 'time-morning';
    if (hourDecimal >= 11 && hourDecimal < 15) return 'time-noon';
    if (hourDecimal >= 15 && now < duskStart) return 'time-afternoon';
    if (now >= duskStart && now < duskEnd) return 'time-dusk';
    if (hourDecimal >= 21 && hourDecimal < 23) return 'time-night';
    return 'time-deep-night';
  },

  // Start clock - updates every minute
  startClock() {
    this.updateTime();
    setInterval(() => this.updateTime(), 60000); // Every minute
  },

  // Start transition checker - every 10 seconds for smooth transitions
  startTimeTransitionChecker() {
    setInterval(() => this.updateTransition(), 10000);
    this.updateTransition();
  },

  // Update time-based UI
  updateTime() {
    // Check unified theme mode setting
    const themeMode = localStorage.getItem('ic_theme_mode') || 'auto';

    if (themeMode === 'dark') {
      // Force dark mode
      document.body.classList.add('dark-star');
      this.currentTimeState.timeClass = 'time-night';
      this.currentTimeState.isDay = false;
      document.body.classList.remove(...this.timeClasses);
      document.body.classList.add('time-night');
      return;
    }

    if (themeMode === 'light') {
      // Force light mode
      document.body.classList.remove('dark-star');
      const timeClass = this.getTimeClass();
      this.currentTimeState.timeClass = timeClass;
      this.currentTimeState.isDay = true;
      document.body.classList.remove(...this.timeClasses);
      document.body.classList.add(timeClass);
      this.applyTimeColors(timeClass);
      if (window.ICMotion) ICMotion.applyTimeBasedMotion();
      return;
    }

    // Auto mode: follow natural rhythm
    const fixed = localStorage.getItem('ic_fixed_theme');
    if (fixed && localStorage.getItem('ic_visual_auto') === 'false') {
      this.currentTimeState.timeClass = fixed;
      this.currentTimeState.isDay = fixed !== 'time-night' && fixed !== 'time-deep-night';
      document.body.classList.remove(...this.timeClasses);
      document.body.classList.add(fixed);
      this.applyTimeColors(fixed);
      return;
    }

    const timeClass = this.getTimeClass();
    this.currentTimeState.timeClass = timeClass;
    this.currentTimeState.isDay = timeClass !== 'time-night' && timeClass !== 'time-deep-night';

    // Apply time class to body (remove old time classes first)
    document.body.classList.remove(...this.timeClasses);
    document.body.classList.add(timeClass);

    // Also update legacy time classes for compatibility
    document.body.classList.remove('time-morning', 'time-afternoon', 'time-noon', 'time-evening', 'time-dawn', 'time-dusk');
    const hour = new Date().getHours();
    if (hour >= 5 && hour < 9) document.body.classList.add('time-morning');
    else if (hour >= 9 && hour < 12) document.body.classList.add('time-noon');
    else if (hour >= 12 && hour < 17) document.body.classList.add('time-afternoon');
    else if (hour >= 17 && hour < 21) document.body.classList.add('time-dusk');
    else if (hour >= 21 || hour < 5) document.body.classList.add('time-night');

    // Auto dark mode: switch to warm dark theme at dusk/night
    const autoTheme = JSON.parse(localStorage.getItem('ic_auto_theme') || 'true');
    if (autoTheme) {
      const isNightPeriod = timeClass === 'time-night' || timeClass === 'time-deep-night';
      const isDuskPeriod = timeClass === 'time-dusk';
      const isNightHour = new Date().getHours() >= 18 || new Date().getHours() < 6;
      document.body.classList.toggle('dark-star', isNightPeriod || isDuskPeriod || isNightHour);
    }

    // Update CSS variables for time-based colors
    this.applyTimeColors(timeClass);

    // Trigger motion update for time-based animations
    if (window.ICMotion) {
      ICMotion.applyTimeBasedMotion();
    }
  },

  // Update transition progress for smooth color blending
  updateTransition() {
    if (localStorage.getItem('ic_visual_auto') === 'false') return;
    if (!this.currentTimeState.sunset || !this.currentTimeState.sunrise) return;

    const now = new Date();
    const nowMs = now.getTime();
    const sunsetMs = this.currentTimeState.sunset.getTime();
    const sunriseMs = this.currentTimeState.sunrise.getTime();

    // Calculate transition progress
    // 0 = fully in one state, 1 = fully in next state
    let progress = 0;
    const transitionDuration = 90 * 60 * 1000; // 90 minutes

    // Check if we're in sunset transition
    if (Math.abs(nowMs - sunsetMs) < transitionDuration) {
      progress = (nowMs - (sunsetMs - transitionDuration / 2)) / transitionDuration;
      this.applyColorTransition('dusk', 'night', progress);
      return;
    }

    // Check if we're in sunrise transition
    if (Math.abs(nowMs - sunriseMs) < transitionDuration) {
      progress = (nowMs - (sunriseMs - transitionDuration / 2)) / transitionDuration;
      this.applyColorTransition('deep-night', 'dawn', progress);
      return;
    }

    this.currentTimeState.transitionProgress = 0;
  },

  // Apply smooth color transition between two palettes
  applyColorTransition(fromPalette, toPalette, progress) {
    progress = Math.max(0, Math.min(1, progress));
    this.currentTimeState.transitionProgress = progress;

    const from = this.colorPalettes[fromPalette];
    const to = this.colorPalettes[toPalette];
    if (!from || !to) {
      this.applyTimeColors(this.currentTimeState.timeClass || 'time-morning');
      return;
    }

    const root = document.documentElement;

    // Interpolate colors
    const warmColor = this.interpolateColor(from.warm, to.warm, progress);
    const secondaryColor = this.interpolateColor(from.secondary, to.secondary, progress);

    root.style.setProperty('--surface-warm', warmColor);
    root.style.setProperty('--surface-secondary', secondaryColor);
  },

  // Color interpolation utility
  interpolateColor(color1, color2, factor) {
    const c1 = this.hexToRgb(color1);
    const c2 = this.hexToRgb(color2);

    const r = Math.round(c1.r + (c2.r - c1.r) * factor);
    const g = Math.round(c1.g + (c2.g - c1.g) * factor);
    const b = Math.round(c1.b + (c2.b - c1.b) * factor);

    return `rgb(${r}, ${g}, ${b})`;
  },

  // Hex to RGB conversion
  hexToRgb(hex) {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return result ? {
      r: parseInt(result[1], 16),
      g: parseInt(result[2], 16),
      b: parseInt(result[3], 16)
    } : { r: 0, g: 0, b: 0 };
  },

  // Apply time-based colors
  applyTimeColors(timeClass) {
    const root = document.documentElement;
    const autoTheme = JSON.parse(localStorage.getItem('ic_auto_theme') || 'false');

    switch (timeClass) {
      case 'time-dawn':
        root.style.setProperty('--surface-warm', '#FAF2E7');
        root.style.setProperty('--surface-secondary', '#F7F2EC');
        break;
      case 'time-morning':
      case 'time-noon':
        root.style.setProperty('--surface-warm', '#F7F2EC');
        root.style.setProperty('--surface-secondary', '#E8EDE4');
        break;
      case 'time-afternoon':
        root.style.setProperty('--surface-warm', '#F7F2EC');
        root.style.setProperty('--surface-secondary', '#EFE6DA');
        break;
      case 'time-dusk':
        root.style.setProperty('--surface-warm', '#F5EEE7');
        root.style.setProperty('--surface-secondary', '#E8DDE4');
        break;
      case 'time-night':
      case 'time-deep-night':
        root.style.setProperty('--surface-warm', autoTheme ? 'var(--color-night-brown)' : '#F4EFE8');
        root.style.setProperty('--surface-secondary', autoTheme ? 'var(--color-night-warm-gray)' : '#E7E2DA');
        break;
    }
  },

  // Get current time state for other components
  getTimeState() {
    return this.currentTimeState;
  },

  // Check if it's currently daytime
  isDay() {
    return this.currentTimeState.isDay;
  }
};

// ── Inner Cosmos Location (city label exposed to chat payloads) ──
// ICLocation wraps the city-level label resolved by ICLocation/ICTimeSystem and
// exposes a stable, chat-friendly getter. Pages and the API client call
// `ICLocation.getCityLabel()` instead of reading localStorage directly so the
// lookup policy lives in one place. Falls back to "未知" on a cold cache.
window.ICLocation = {
  /**
   * @returns {string} e.g. "北京 · 海淀区" or "未知" when the browser has
   *   not yet resolved a location.
   */
  getCityLabel() {
    try {
      const label = localStorage.getItem('ic_location_label');
      if (label && label.trim()) return label.trim();
    } catch (_) {
      // localStorage can be unavailable (Safari private mode etc.) — fall through.
    }
    return '未知';
  },

  /**
   * Convenience: just the city part, e.g. "北京" (or "未知"). Strips the
   * " · " street segment that {@link #getCityLabel} returns.
   */
  getCityOnly() {
    const label = this.getCityLabel();
    if (!label || label === '未知') return '未知';
    const cut = label.indexOf(' · ');
    return cut > 0 ? label.substring(0, cut) : label;
  },

  /**
   * Current lat/lon (rounded to 4 decimals ≈ 11m) or null when not yet known.
   */
  getLatLon() {
    const lat = window.ICTimeSystem?.userLocation?.lat;
    const lon = window.ICTimeSystem?.userLocation?.lon;
    if (typeof lat !== 'number' || typeof lon !== 'number') return null;
    return { lat: Math.round(lat * 10000) / 10000, lon: Math.round(lon * 10000) / 10000 };
  },

  /**
   * Force a re-resolve. Useful right after a manual location change.
   */
  refresh() {
    if (window.ICTimeSystem && typeof window.ICTimeSystem.resolveLocationLabel === 'function') {
      return window.ICTimeSystem.resolveLocationLabel();
    }
    return Promise.resolve();
  }
};

// Auto-initialize when DOM is ready
if (typeof document !== 'undefined') {
  document.addEventListener('DOMContentLoaded', () => {
    window.ICTimeSystem.init();
  });
}
