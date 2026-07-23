import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { PeopleDiscovery } from "./PeopleDiscovery";
import type { DiscoverablePerson } from "../api";

afterEach(cleanup);

const person = (over: Partial<DiscoverablePerson> = {}): DiscoverablePerson => ({
  id: 2, username: "yu", nickname: "小雨", relationStatus: "NONE", ...over
});

describe("PeopleDiscovery", () => {
  it("lets the user send a connection request to a not-yet-connected person", () => {
    const onRequest = vi.fn();
    render(<PeopleDiscovery people={[person({ id: 5, nickname: "小满" })]} isBusy={() => false} onRequest={onRequest} />);
    expect(screen.getByText("小满")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "想认识 ta" }));
    expect(onRequest).toHaveBeenCalledExactlyOnceWith(5);
  });

  it("does not offer a request button once a relation already exists", () => {
    render(<PeopleDiscovery isBusy={() => false} onRequest={() => undefined}
      people={[person({ id: 1, nickname: "已连接的人", relationStatus: "ACCEPTED" }),
        person({ id: 2, nickname: "已邀请的人", relationStatus: "PENDING_OUT" }),
        person({ id: 3, nickname: "邀请我的人", relationStatus: "PENDING_IN" })]} />);
    expect(screen.queryByRole("button", { name: "想认识 ta" })).not.toBeInTheDocument();
    expect(screen.getByText("已连接")).toBeVisible();
    expect(screen.getByText("已发出邀请")).toBeVisible();
    expect(screen.getByText(/想认识你/)).toBeVisible();
  });

  it("shows an empty state when nobody is discoverable", () => {
    render(<PeopleDiscovery people={[]} isBusy={() => false} onRequest={() => undefined} />);
    expect(screen.getByText(/还没有/)).toBeVisible();
  });

  it("disables request buttons while a request is in flight", () => {
    render(<PeopleDiscovery people={[person()]} isBusy={() => true} onRequest={() => undefined} />);
    expect(screen.getByRole("button", { name: "想认识 ta" })).toBeDisabled();
  });

  // Gemini audit 4.8 (CONFIRMED/P1): isBusy must be consulted PER PERSON -- inviting person A must
  // never disable person B's invite button. This proves the component calls isBusy with each
  // person's own id (not a single shared flag), by making it busy for only one of two ids.
  it("only disables the specific person's button that isBusy reports as busy -- an unrelated person's button stays enabled", () => {
    render(<PeopleDiscovery
      people={[person({ id: 5, nickname: "小满" }), person({ id: 9, nickname: "小九" })]}
      isBusy={(userId) => userId === 5}
      onRequest={() => undefined} />);
    const buttons = screen.getAllByRole("button", { name: "想认识 ta" });
    expect(buttons).toHaveLength(2);
    expect(buttons[0]).toBeDisabled(); // person 5 -- busy
    expect(buttons[1]).not.toBeDisabled(); // person 9 -- unrelated, must stay enabled
  });

  it("renders in English and maps relation statuses when locale is en-SG", () => {
    render(<PeopleDiscovery locale="en-SG" people={[person({ relationStatus: "PENDING_OUT" })]} isBusy={() => false} onRequest={() => undefined} />);
    expect(screen.getByRole("heading", { name: "Reach out to people, without rushing any relationship" })).toBeVisible();
    expect(screen.getByText("1 person to get to know")).toBeVisible();
    expect(screen.getByText("Invite sent")).toBeVisible();
  });
});
