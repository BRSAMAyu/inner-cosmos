import { afterEach, describe, expect, it } from "vitest";
import { interactiveAncestor, spawnRipple } from "./ripple";

describe("interactiveAncestor", () => {
  afterEach(() => (document.body.innerHTML = ""));

  it("matches a button and a click on its inner content", () => {
    document.body.innerHTML = `<button>发送<span id="inner">·</span></button>`;
    const inner = document.getElementById("inner")!;
    expect(interactiveAncestor(inner)?.tagName).toBe("BUTTON");
  });

  it("matches links, role=button, role=tab", () => {
    document.body.innerHTML = `<a href="#x" id="a">x</a><div role="button" id="b">b</div><div role="tab" id="t">t</div>`;
    expect(interactiveAncestor(document.getElementById("a"))).not.toBeNull();
    expect(interactiveAncestor(document.getElementById("b"))).not.toBeNull();
    expect(interactiveAncestor(document.getElementById("t"))).not.toBeNull();
  });

  it("ignores plain text / disabled buttons", () => {
    document.body.innerHTML = `<p id="p">just text</p><button id="d" disabled>x</button>`;
    expect(interactiveAncestor(document.getElementById("p"))).toBeNull();
    expect(interactiveAncestor(document.getElementById("d"))).toBeNull();
  });

  it("returns null for non-element targets", () => {
    expect(interactiveAncestor(null)).toBeNull();
  });
});

describe("spawnRipple", () => {
  afterEach(() => (document.body.innerHTML = ""));

  it("appends a positioned .ripple element", () => {
    spawnRipple(120, 240);
    const r = document.querySelector(".ripple") as HTMLElement;
    expect(r).not.toBeNull();
    expect(r.style.left).toBe("120px");
    expect(r.style.top).toBe("240px");
    expect(r.getAttribute("aria-hidden")).toBe("true");
  });

  it("removes the ripple on animationend", () => {
    spawnRipple(10, 10);
    const r = document.querySelector(".ripple")!;
    r.dispatchEvent(new Event("animationend"));
    expect(document.querySelector(".ripple")).toBeNull();
  });
});
