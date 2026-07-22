import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { SafetyResourceList } from "./SafetyResourceList";

afterEach(cleanup);

describe("SafetyResourceList", () => {
  it("renders each resource line verbatim with a tel: link for any dialable number found in it", () => {
    render(<SafetyResourceList resources={[
      "如果你正处于紧急危险中，请立即拨打 110（报警），或联系身边可信赖的人。",
      "全国心理援助热线（希望 24）· 24 小时：400-161-9995。",
      "Inner Cosmos 不提供心理诊断，也不替代医生、咨询师或热线。"
    ]} dialLabel="拨打 " />);
    expect(screen.getByText(/如果你正处于紧急危险中/)).toBeInTheDocument();
    expect(screen.getByText(/Inner Cosmos 不提供心理诊断/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "拨打 110" })).toHaveAttribute("href", "tel:110");
    expect(screen.getByRole("link", { name: "拨打 400-161-9995" })).toHaveAttribute("href", "tel:4001619995");
    // The disclaimer line has no phone number, so it must not render a dangling tel: link.
    expect(screen.queryByRole("link", { name: /Inner Cosmos/ })).not.toBeInTheDocument();
  });

  it("renders nothing when there are no resources", () => {
    const { container } = render(<SafetyResourceList resources={[]} dialLabel="拨打 " />);
    expect(container).toBeEmptyDOMElement();
  });
});
