/**
 * SSE client for proactive push notifications.
 * Start the connection by calling ICProactive.start(userId).
 */
(function() {
  window.ICProactive = {
    es: null,
    start: function(userId) {
      if (this.es) return;
      var self = this;
      this.es = new EventSource('/api/proactive/stream');
      this.es.addEventListener("proactive", function(e) {
        try {
          var data = JSON.parse(e.data);
          // Show toast notification
          if (window.IC && window.IC.toast) {
            IC.toast(data.content, "aurora", 8000);
          }
          // Dispatch custom event for chat bubble integration
          if (window.dispatchEvent) {
            window.dispatchEvent(new CustomEvent("aurora-proactive", { detail: data }));
          }
        } catch (err) {
          console.warn("Failed to parse proactive event:", err);
        }
      });
      this.es.onerror = function() {
        console.warn("SSE connection error, will retry...");
        self.es.close();
        self.es = null;
        // Auto-reconnect after 5 seconds
        setTimeout(function() { self.start(userId); }, 5000);
      };
    },
    stop: function() {
      if (this.es) {
        this.es.close();
        this.es = null;
      }
    }
  };
})();