/* ── Inner Cosmos Weather Perception System ── */
window.ICWeather = {
  // Weather types
  weatherTypes: ['CLEAR', 'CLOUD', 'RAIN', 'STORM', 'FOG', 'SNOW'],

  // Current weather state
  currentWeather: {
    type: 'CLEAR',
    temperature: 22,
    humidity: 60,
    windSpeed: 10,
    description: '晴朗'
  },

  // Weather API configuration
  weatherAPI: {
    enabled: true,
    // Open-Meteo API (free, no API key required)
    url: 'https://api.open-meteo.com/v1/forecast',
    // Cached weather data with 30min expiry
    cache: null,
    cacheTime: null,
    cacheDuration: 30 * 60 * 1000 // 30 minutes
  },

  // Mock weather for demo/testing
  mockWeather: {
    type: 'CLEAR',
    temperature: 22,
    humidity: 60,
    windSpeed: 10
  },

  // Initialize
  init() {
    this.loadWeatherPreference();
    this.fetchWeather();
    // Update weather every 30 minutes
    setInterval(() => this.fetchWeather(), 30 * 60 * 1000);
  },

  // Load user's weather preference
  loadWeatherPreference() {
    const preferredWeather = localStorage.getItem('ic_preferred_weather');
    if (preferredWeather) {
      this.mockWeather.type = preferredWeather;
    }
  },

  // Fetch weather from API
  async fetchWeather() {
    // Check if user prefers manual weather selection
    if (localStorage.getItem('ic_weather_mode') === 'manual') {
      this.applyWeather(this.mockWeather.type);
      return;
    }

    // Check cache first
    const now = Date.now();
    if (this.weatherAPI.cache && this.weatherAPI.cacheTime &&
        (now - this.weatherAPI.cacheTime) < this.weatherAPI.cacheDuration) {
      console.log('Using cached weather data');
      this.currentWeather = this.weatherAPI.cache;
      this.applyWeather(this.currentWeather.type);
      return;
    }

    if (!this.weatherAPI.enabled) {
      this.useMockWeather();
      return;
    }

    try {
      // Get user location from time system
      const lat = window.ICTimeSystem?.userLocation?.lat || 39.9042;
      const lon = window.ICTimeSystem?.userLocation?.lon || 116.4074;

      const url = `${this.weatherAPI.url}?latitude=${lat}&longitude=${lon}&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto`;

      const response = await fetch(url);
      const data = await response.json();

      if (data.current) {
        const weatherCode = data.current.weather_code;
        const weatherType = this.mapWeatherCodeToType(weatherCode);

        this.currentWeather = {
          type: weatherType,
          temperature: Math.round(data.current.temperature_2m),
          humidity: data.current.relative_humidity_2m,
          windSpeed: Math.round(data.current.wind_speed_10m),
          description: this.getWeatherDescription(weatherType)
        };

        // Cache the results
        this.weatherAPI.cache = this.currentWeather;
        this.weatherAPI.cacheTime = now;

        console.log('Weather fetched:', this.currentWeather);
        this.applyWeather(weatherType);
      }
    } catch (error) {
      console.warn('Failed to fetch weather, using mock:', error);
      this.useMockWeather();
    }
  },

  // Map WMO weather codes to our weather types
  mapWeatherCodeToType(code) {
    // WMO Weather Interpretation Codes (WW)
    // Code 0: Clear sky
    if (code === 0) return 'CLEAR';

    // Code 1-3: Mainly clear, partly cloudy, overcast
    if (code >= 1 && code <= 3) return 'CLOUD';

    // Code 45, 48: Fog
    if (code === 45 || code === 48) return 'FOG';

    // Code 51-67: Drizzle, Rain
    if (code >= 51 && code <= 67) return 'RAIN';

    // Code 71-77: Snow fall
    if (code >= 71 && code <= 77) return 'SNOW';

    // Code 80-82: Rain showers
    if (code >= 80 && code <= 82) return 'RAIN';

    // Code 85-86: Snow showers
    if (code === 85 || code === 86) return 'SNOW';

    // Code 95-99: Thunderstorm
    if (code >= 95 && code <= 99) return 'STORM';

    // Default
    return 'CLEAR';
  },

  // Get weather description in Chinese
  getWeatherDescription(type) {
    const descriptions = {
      'CLEAR': '晴朗',
      'CLOUD': '多云',
      'RAIN': '下雨',
      'STORM': '暴风雨',
      'FOG': '有雾',
      'SNOW': '下雪'
    };
    return descriptions[type] || '晴朗';
  },

  // Use mock weather
  useMockWeather() {
    // Generate mock weather based on time
    const hour = new Date().getHours();
    let mockType = 'CLEAR';

    if (hour >= 6 && hour < 12) {
      // Morning: random chance of clouds
      mockType = Math.random() > 0.7 ? 'CLOUD' : 'CLEAR';
    } else if (hour >= 12 && hour < 18) {
      // Afternoon: more varied
      const rand = Math.random();
      if (rand < 0.5) mockType = 'CLEAR';
      else if (rand < 0.75) mockType = 'CLOUD';
      else if (rand < 0.9) mockType = 'RAIN';
      else mockType = 'STORM';
    } else {
      // Evening/night: calmer
      mockType = Math.random() > 0.8 ? 'CLOUD' : 'CLEAR';
    }

    this.mockWeather.type = mockType;
    this.currentWeather = {
      ...this.mockWeather,
      description: this.getWeatherDescription(mockType)
    };

    this.applyWeather(mockType);
  },

  // Apply weather effects
  applyWeather(type) {
    const body = document.body;

    // Remove all weather classes
    this.weatherTypes.forEach(t => {
      body.classList.remove(`weather-${t.toLowerCase()}`);
    });

    // Add new weather class
    body.classList.add(`weather-${type.toLowerCase()}`);

    // Update CSS variables for weather-specific effects
    this.applyWeatherEffects(type);

    // Update motion system if available
    if (window.ICMotion) {
      ICMotion.adaptToWeather(type);
    }

    // Dispatch weather change event
    window.dispatchEvent(new CustomEvent('weatherChanged', { detail: { type, weather: this.currentWeather } }));
  },

  // Apply weather-specific visual effects
  applyWeatherEffects(type) {
    const root = document.documentElement;

    switch (type) {
      case 'CLEAR':
        root.style.setProperty('--weather-opacity', '0');
        root.style.setProperty('--weather-blur', '0px');
        break;
      case 'CLOUD':
        root.style.setProperty('--weather-opacity', '0.15');
        root.style.setProperty('--weather-blur', '2px');
        break;
      case 'RAIN':
        root.style.setProperty('--weather-opacity', '0.3');
        root.style.setProperty('--weather-blur', '4px');
        break;
      case 'STORM':
        root.style.setProperty('--weather-opacity', '0.5');
        root.style.setProperty('--weather-blur', '6px');
        break;
      case 'FOG':
        root.style.setProperty('--weather-opacity', '0.6');
        root.style.setProperty('--weather-blur', '8px');
        break;
      case 'SNOW':
        root.style.setProperty('--weather-opacity', '0.25');
        root.style.setProperty('--weather-blur', '3px');
        break;
    }
  },

  // Set manual weather preference
  setManualWeather(type) {
    if (!this.weatherTypes.includes(type)) {
      console.warn('Invalid weather type:', type);
      return;
    }

    localStorage.setItem('ic_weather_mode', 'manual');
    localStorage.setItem('ic_preferred_weather', type);
    this.mockWeather.type = type;

    this.currentWeather = {
      ...this.mockWeather,
      description: this.getWeatherDescription(type)
    };

    this.applyWeather(type);

    if (window.IC && window.IC.toast) {
      IC.toast(`天气已切换为：${this.getWeatherDescription(type)}`);
    }
  },

  // Set automatic weather mode
  setAutoWeather() {
    localStorage.setItem('ic_weather_mode', 'auto');
    this.fetchWeather();

    if (window.IC && window.IC.toast) {
      IC.toast('天气已切换为自动模式');
    }
  },

  // Get current weather state
  getWeatherState() {
    return {
      ...this.currentWeather,
      isAuto: localStorage.getItem('ic_weather_mode') !== 'manual'
    };
  },

  // Get weather icon
  getWeatherIcon(type) {
    const icons = {
      'CLEAR': '☀️',
      'CLOUD': '☁️',
      'RAIN': '🌧️',
      'STORM': '⛈️',
      'FOG': '🌫️',
      'SNOW': '🌨️'
    };
    return icons[type] || '☀️';
  }
};

// Auto-initialize when DOM is ready
if (typeof document !== 'undefined') {
  document.addEventListener('DOMContentLoaded', () => {
    window.ICWeather.init();
  });
}
