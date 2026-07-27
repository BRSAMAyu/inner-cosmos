import { afterEach, describe, expect, it } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { SafetyResourceList } from "./SafetyResourceList";

afterEach(cleanup);

describe("SafetyResourceList", () => {
  it("renders each resource line verbatim with a tel: link for any dialable number found in it", () => {
    render(<SafetyResourceList resources={[
      { id: "cn-police", label: "如果你正处于紧急危险中，请立即拨打 110（报警），或联系身边可信赖的人。",
        phone: "110", authorityUrl: "https://example.gov/110", verifiedAt: "2026-07-27", region: "CN",
        audience: "ALL", hours: "24/7", channel: "PHONE", category: "EMERGENCY" },
      { id: "cn-12356", label: "全国统一心理援助热线：12356。",
        phone: "12356", authorityUrl: "https://example.gov/12356", verifiedAt: "2026-07-27", region: "CN",
        audience: "ALL", hours: "LOCAL_SERVICE", channel: "PHONE", category: "CRISIS_SUPPORT" },
      { id: "cn-boundary", label: "Inner Cosmos 不提供心理诊断，也不替代医生、咨询师或热线。",
        phone: null, authorityUrl: "https://example.gov/boundary", verifiedAt: "2026-07-27", region: "CN",
        audience: "ALL", hours: "ALWAYS", channel: "NOTICE", category: "PRODUCT_BOUNDARY" }
    ]} dialLabel="拨打 " />);
    expect(screen.getByText(/如果你正处于紧急危险中/)).toBeInTheDocument();
    expect(screen.getByText(/Inner Cosmos 不提供心理诊断/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "拨打 110" })).toHaveAttribute("href", "tel:110");
    expect(screen.getByRole("link", { name: "拨打 12356" })).toHaveAttribute("href", "tel:12356");
    // The disclaimer line has no phone number, so it must not render a dangling tel: link.
    expect(screen.queryByRole("link", { name: /Inner Cosmos/ })).not.toBeInTheDocument();
  });

  it("renders nothing when there are no resources", () => {
    const { container } = render(<SafetyResourceList resources={[]} dialLabel="拨打 " />);
    expect(container).toBeEmptyDOMElement();
  });
});
