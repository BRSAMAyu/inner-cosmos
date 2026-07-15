import { describe, expect, it } from "vitest";
import { sequenceFromEventId, SseDecoder, toTypedEvent } from "./protocol";

describe("typed SSE protocol", () => {
  it("decodes frames split across network chunks", () => {
    const decoder = new SseDecoder();
    expect(decoder.push("id: 42:7\r\nevent: token\r\ndata: {\"con")).toEqual([]);
    const frames = decoder.push("tent\":\"我在\"}\r\n\r\n");
    expect(frames).toHaveLength(1);
    expect(toTypedEvent(frames[0])).toEqual({
      id: "42:7", type: "token", payload: { content: "我在" }
    });
  });

  it("supports multiline data and replay sequence cursors", () => {
    const decoder = new SseDecoder();
    const [frame] = decoder.push("id: 9:12\nevent: timeline.event\ndata: {\"turnId\":9,\ndata: \"sequence\":12}\n\n");
    expect(frame.data).toBe("{\"turnId\":9,\n\"sequence\":12}");
    expect(sequenceFromEventId(frame.id)).toBe(12);
  });

  it("ignores malformed JSON instead of corrupting chat state", () => {
    expect(toTypedEvent({ id: "1:1", event: "token", data: "not-json" })).toBeNull();
  });
});
