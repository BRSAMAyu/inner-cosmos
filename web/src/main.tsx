import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AuroraApp } from "./AuroraApp";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AuroraApp />
  </StrictMode>
);
