import "@testing-library/jest-dom/vitest";

// jsdom does not implement scrollIntoView; components that auto-scroll (e.g. AuroraConversation)
// would otherwise throw "scrollIntoView is not a function" in every test that renders them.
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {};
}
