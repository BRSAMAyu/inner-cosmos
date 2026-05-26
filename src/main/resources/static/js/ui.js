/* ── Inner Cosmos UI Utilities ── */
window.InnerCosmosUI = {
  toast: typeof IC !== "undefined" ? IC.toast : function() {},
  empty: typeof IC !== "undefined" ? IC.empty : function(t) { return t; },
  pulse: typeof IC !== "undefined" ? IC.pulse : function() {},
  showModal: typeof IC !== "undefined" ? IC.showModal : function() {},
  closeModal: typeof IC !== "undefined" ? IC.closeModal : function() {},
  stagger: typeof IC !== "undefined" ? IC.stagger : function() {}
};
