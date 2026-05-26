/* ── Inner Cosmos Common Utilities ── */
/* Re-exports from the main IC namespace for backward compatibility */
window.InnerCosmosCommon = {
  nav: typeof IC !== "undefined" ? IC.nav : function() { return ""; },
  mountShell: typeof IC !== "undefined" ? IC.mountShell : function() {},
  esc: typeof IC !== "undefined" ? IC.esc : function(t) { return t; },
  formatTime: typeof IC !== "undefined" ? IC.formatTime : function(t) { return t; },
  greetingByTime: typeof IC !== "undefined" ? IC.greetingByTime : function() { return ""; },
  weatherIcon: typeof IC !== "undefined" ? IC.weatherIcon : function() { return ""; },
  toast: typeof IC !== "undefined" ? IC.toast : function() {},
  empty: typeof IC !== "undefined" ? IC.empty : function(t) { return t; },
  pulse: typeof IC !== "undefined" ? IC.pulse : function() {},
  stagger: typeof IC !== "undefined" ? IC.stagger : function() {},
  showModal: typeof IC !== "undefined" ? IC.showModal : function() {},
  closeModal: typeof IC !== "undefined" ? IC.closeModal : function() {}
};
