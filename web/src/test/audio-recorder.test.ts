import { describe, expect, it } from "vitest";
import { encodePcmWav, resampleMono } from "../audio-recorder";

describe("PCM WAV contract", () => {
  it("writes a mono 16-bit WAV header and exact payload length", async () => {
    const blob = encodePcmWav(new Float32Array([0, 1, -1, .5]));
    const bytes = new Uint8Array(await blob.arrayBuffer());
    expect(new TextDecoder().decode(bytes.slice(0, 4))).toBe("RIFF");
    expect(new TextDecoder().decode(bytes.slice(8, 12))).toBe("WAVE");
    expect(new DataView(bytes.buffer).getUint16(22, true)).toBe(1);
    expect(new DataView(bytes.buffer).getUint32(24, true)).toBe(16_000);
    expect(new DataView(bytes.buffer).getUint16(34, true)).toBe(16);
    expect(blob.type).toBe("audio/wav");
    expect(bytes.byteLength).toBe(52);
  });

  it("downsamples by averaging source windows", () => {
    const output = resampleMono(new Float32Array([1, 1, -1, -1]), 32_000, 16_000);
    expect([...output]).toEqual([1, -1]);
  });
});
