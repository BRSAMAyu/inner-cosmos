/* ── Inner Cosmos BGM Audio System ── */
window.ICAudio = {
  // Audio context and players
  audioCtx: null,
  bgmPlayer: null,
  weatherPlayer: null,
  interactionPlayer: null,

  // Current state
  isMuted: false,
  currentBGM: null,
  currentWeather: null,
  masterVolume: 0.12,
  ambientFallbackEnabled: false,

  // BGM tracks for different times. Real local public-domain classical
  // recordings, transcoded to 128k MP3 (universal browser support, ~10x smaller
  // than the source FLAC). No oscillator fallback by default — it can sound like a hum.
  bgmTracks: {
    dawn: { name: 'Chopin Nocturne Op.9 No.2', url: '/audio/music/chopin-nocturne-op9-no2.mp3' },
    morning: { name: 'Mozart K.282 Adagio', url: '/audio/music/mozart-k282-adagio.mp3' },
    noon: { name: 'Mozart K.282 Menuetto', url: '/audio/music/mozart-k282-menuetto.mp3' },
    afternoon: { name: 'Mozart K.282 Allegro', url: '/audio/music/mozart-k282-allegro.mp3' },
    dusk: { name: 'Chopin Nocturne Op.55 No.1', url: '/audio/music/chopin-nocturne-op55-no1.mp3' },
    night: { name: 'Chopin Nocturne Op.62 No.2', url: '/audio/music/chopin-nocturne-op62-no2.mp3' },
    deepNight: { name: 'Chopin Nocturne Op.55 No.1', url: '/audio/music/chopin-nocturne-op55-no1.mp3' }
  },

  // Weather sound effects
  weatherSounds: {
    CLEAR: null,
    CLOUD: null,
    RAIN: '/audio/rain.mp3',
    STORM: '/audio/storm.mp3',
    FOG: '/audio/wind.mp3',
    SNOW: '/audio/snow.mp3'
  },

  // Interaction sounds
  interactionSounds: {
    hover: { freq: 800, duration: 0.05 },
    click: { freq: 1200, duration: 0.08 },
    success: { freq: 880, duration: 0.15 },
    error: { freq: 220, duration: 0.2 },
    message: { freq: 660, duration: 0.1 }
  },

  // Initialize
  init() {
    this.loadSettings();
    if (window.IC?.refreshMusicButton) window.IC.refreshMusicButton();

    // Listen for user interaction to unlock audio
    document.addEventListener('click', () => this.unlockAudio(), { once: true });
    document.addEventListener('keydown', () => this.unlockAudio(), { once: true });

    // Listen for time changes
    window.addEventListener('weatherChanged', (e) => this.onWeatherChange(e.detail.type));
    if (window.ICTimeSystem) {
      setInterval(() => this.onTimeChange(), 60000); // Check every minute
    }
  },

  // Load settings from localStorage
  loadSettings() {
    const savedMuted = localStorage.getItem('ic_audio_muted');
    const savedVolume = localStorage.getItem('ic_audio_volume');
    const savedAmbientFallback = localStorage.getItem('ic_audio_ambient_fallback');

    if (savedMuted !== null) {
      this.isMuted = savedMuted === 'true';
    } else {
      this.isMuted = false;
      localStorage.setItem('ic_audio_muted', 'false');
    }
    if (savedVolume !== null) {
      this.masterVolume = parseFloat(savedVolume);
    }
    this.ambientFallbackEnabled = savedAmbientFallback === 'true';
  },

  // Save settings
  saveSettings() {
    localStorage.setItem('ic_audio_muted', this.isMuted.toString());
    localStorage.setItem('ic_audio_volume', this.masterVolume.toString());
  },

  // Initialize audio context
  initAudioContext() {
    if (this.audioCtx) return this.audioCtx;
    try {
      this.audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    } catch (e) {
      console.warn('Web Audio API not supported:', e);
    }
    return this.audioCtx;
  },

  // Unlock audio (required for user interaction)
  unlockAudio() {
    if (this.isMuted) return;
    this.initAudioContext();
    if (this.audioCtx && this.audioCtx.state === 'suspended') {
      this.audioCtx.resume();
    }
    if (!this.bgmPlayer) this.playCurrentBGM();
  },

  // Toggle mute
  toggleMute() {
    this.isMuted = !this.isMuted;
    this.saveSettings();

    if (this.isMuted) {
      this.stopBGM();
      this.stopWeatherSound();
    } else {
      this.unlockAudio();
    }

    return this.isMuted;
  },

  // Set volume
  setVolume(volume) {
    this.masterVolume = Math.max(0, Math.min(1, volume));
    this.saveSettings();

    if (this.bgmPlayer) {
      this.bgmPlayer.gainNode.gain.setValueAtTime(
        this.isMuted ? 0 : this.masterVolume * 0.5,
        this.audioCtx.currentTime
      );
    }
  },

  // Get current time class
  getCurrentTimeClass() {
    if (window.ICTimeSystem) {
      return window.ICTimeSystem.getTimeClass();
    }
    // Fallback to hour-based
    const hour = new Date().getHours();
    if (hour >= 5 && hour < 9) return 'dawn';
    if (hour >= 9 && hour < 12) return 'morning';
    if (hour >= 12 && hour < 15) return 'noon';
    if (hour >= 15 && hour < 18) return 'afternoon';
    if (hour >= 18 && hour < 21) return 'dusk';
    if (hour >= 21 || hour < 5) return hour >= 23 || hour < 5 ? 'deepNight' : 'night';
    return 'morning';
  },

  // Play BGM for current time
  playCurrentBGM() {
    if (this.isMuted) return;

    const timeClass = this.getCurrentTimeClass();
    const timeKey = timeClass.replace('time-', '').replace('deepNight', 'deepNight');

    // Map time class to BGM track key
    const bgmMap = {
      'dawn': 'dawn',
      'morning': 'morning',
      'noon': 'noon',
      'afternoon': 'afternoon',
      'dusk': 'dusk',
      'night': 'night',
      'deep-night': 'deepNight',
      'deepNight': 'deepNight'
    };

    const trackKey = bgmMap[timeKey] || 'morning';
    this.playBGM(trackKey);
  },

  // Play BGM by track key
  playBGM(trackKey) {
    if (this.isMuted) return;
    this.initAudioContext();
    if (!this.audioCtx) return;

    this.stopBGM();
    this.currentBGM = trackKey;

    const track = this.bgmTracks[trackKey];
    if (!track) return;

    // Try to load audio file. Synthetic ambient fallback stays opt-in because
    // continuous oscillators can sound like a low hum on laptop speakers.
    this.loadAudioFile(track.url).then(audioBuffer => {
      if (audioBuffer) {
        this.playAudioBuffer(audioBuffer, true);
      } else if (this.ambientFallbackEnabled) {
        this.generateAmbientTones(track);
      }
    }).catch(() => {
      if (this.ambientFallbackEnabled) this.generateAmbientTones(track);
    });
  },

  // Load audio file
  async loadAudioFile(url) {
    try {
      const response = await fetch(url);
      if (!response.ok) return null;
      const arrayBuffer = await response.arrayBuffer();
      return await this.audioCtx.decodeAudioData(arrayBuffer);
    } catch (e) {
      return null;
    }
  },

  // Play audio buffer with looping
  playAudioBuffer(audioBuffer, loop = false) {
    if (!this.audioCtx) return;

    const source = this.audioCtx.createBufferSource();
    source.buffer = audioBuffer;
    source.loop = loop;

    const gainNode = this.audioCtx.createGain();
    gainNode.gain.setValueAtTime(this.masterVolume * 0.5, this.audioCtx.currentTime);

    source.connect(gainNode);
    gainNode.connect(this.audioCtx.destination);

    source.start();
    this.bgmPlayer = { source, gainNode, audioBuffer };
  },

  // Generate ambient tones (fallback)
  generateAmbientTones(track) {
    if (!this.ambientFallbackEnabled || !this.audioCtx || !track.tones) return;

    const { tones, tempo } = track;
    const now = this.audioCtx.currentTime;

    // Create multiple oscillators for rich ambient sound
    const oscillators = tones.map((freq, i) => {
      const osc = this.audioCtx.createOscillator();
      const gain = this.audioCtx.createGain();

      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, now);

      // Subtle volume modulation
      gain.gain.setValueAtTime(this.masterVolume * 0.15 / tones.length, now);
      gain.gain.linearRampToValueAtTime(
        this.masterVolume * 0.2 / tones.length,
        now + 2
      );

      osc.connect(gain);
      gain.connect(this.audioCtx.destination);

      osc.start();
      return { osc, gain };
    });

    // Store for later cleanup
    this.bgmPlayer = { oscillators, tempo };
  },

  // Stop BGM
  stopBGM() {
    if (this.bgmPlayer) {
      if (this.bgmPlayer.source) {
        this.bgmPlayer.source.stop();
        this.bgmPlayer.source.disconnect();
      }
      if (this.bgmPlayer.oscillators) {
        this.bgmPlayer.oscillators.forEach(({ osc, gain }) => {
          osc.stop();
          osc.disconnect();
          gain.disconnect();
        });
      }
      this.bgmPlayer = null;
    }
    this.currentBGM = null;
  },

  // On time change
  onTimeChange() {
    const newTimeClass = this.getCurrentTimeClass();
    const timeKey = newTimeClass.replace('time-', '').replace('deepNight', 'deepNight');
    const bgmMap = {
      'dawn': 'dawn', 'morning': 'morning', 'noon': 'noon',
      'afternoon': 'afternoon', 'dusk': 'dusk', 'night': 'night', 'deepNight': 'deepNight'
    };
    const trackKey = bgmMap[timeKey] || 'morning';

    if (this.currentBGM !== trackKey && !this.isMuted && this.bgmPlayer) {
      this.playBGM(trackKey);
    }
  },

  // On weather change
  onWeatherChange(weatherType) {
    this.currentWeather = weatherType;
    if (!this.isMuted) {
      this.playWeatherSound(weatherType);
    }
  },

  // Play weather sound
  playWeatherSound(weatherType) {
    this.stopWeatherSound();

    if (this.isMuted) return;
    this.initAudioContext();
    if (!this.audioCtx) return;

    const soundUrl = this.weatherSounds[weatherType];
    if (!soundUrl) return;

    this.loadAudioFile(soundUrl).then(audioBuffer => {
      if (audioBuffer) {
        this.playAudioBuffer(audioBuffer, true);
        this.weatherPlayer = this.bgmPlayer;
        this.bgmPlayer = null; // Don't track weather sounds as BGM
      }
    });
  },

  // Stop weather sound
  stopWeatherSound() {
    if (this.weatherPlayer) {
      if (this.weatherPlayer.source) {
        this.weatherPlayer.source.stop();
        this.weatherPlayer.source.disconnect();
      }
      this.weatherPlayer = null;
    }
  },

  // Play interaction sound
  playInteractionSound(soundName) {
    if (localStorage.getItem('ic_audio_interaction') !== 'true') return;
    if (this.isMuted) return;
    this.initAudioContext();
    if (!this.audioCtx) return;

    const sound = this.interactionSounds[soundName];
    if (!sound) return;

    const { freq, duration } = sound;
    const now = this.audioCtx.currentTime;

    const osc = this.audioCtx.createOscillator();
    const gain = this.audioCtx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(freq, now);

    gain.gain.setValueAtTime(this.masterVolume * 0.12, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + duration);

    osc.connect(gain);
    gain.connect(this.audioCtx.destination);

    osc.start(now);
    osc.stop(now + duration);
  },

  playInteraction(soundName) {
    this.playInteractionSound(soundName);
  },

  // Crossfade between tracks
  crossfadeTo(newTrackKey, duration = 3) {
    if (!this.audioCtx || this.isMuted) return;

    const oldPlayer = this.bgmPlayer;
    const newTrack = this.bgmTracks[newTrackKey];
    if (!newTrack) return;

    // Start new track
    this.loadAudioFile(newTrack.url).then(audioBuffer => {
      if (audioBuffer) {
        const source = this.audioCtx.createBufferSource();
        source.buffer = audioBuffer;
        source.loop = true;

        const gainNode = this.audioCtx.createGain();
        gainNode.gain.setValueAtTime(0, this.audioCtx.currentTime);
        gainNode.gain.linearRampToValueAtTime(
          this.masterVolume * 0.5,
          this.audioCtx.currentTime + duration
        );

        source.connect(gainNode);
        gainNode.connect(this.audioCtx.destination);
        source.start();

        this.bgmPlayer = { source, gainNode };

        // Fade out old track
        if (oldPlayer && oldPlayer.gainNode) {
          oldPlayer.gainNode.gain.linearRampToValueAtTime(
            0,
            this.audioCtx.currentTime + duration
          );
          setTimeout(() => {
            if (oldPlayer.source) {
              oldPlayer.source.stop();
              oldPlayer.source.disconnect();
            }
          }, duration * 1000 + 100);
        }
      }
    });
  }
};

// Auto-initialize when DOM is ready
if (typeof document !== 'undefined') {
  document.addEventListener('DOMContentLoaded', () => {
    window.ICAudio.init();
  });
}
