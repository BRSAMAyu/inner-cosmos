const TARGET_RATE = 16_000;
const MAX_DURATION_MS = 60_000;

export type AudioLevelListener = (level: number, speaking: boolean) => void;

export function encodePcmWav(samples: Float32Array, sampleRate = TARGET_RATE): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  const write = (offset: number, value: string) => [...value].forEach((char, index) => view.setUint8(offset + index, char.charCodeAt(0)));
  write(0, "RIFF"); view.setUint32(4, 36 + samples.length * 2, true); write(8, "WAVE"); write(12, "fmt ");
  view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true); view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true); view.setUint16(34, 16, true); write(36, "data");
  view.setUint32(40, samples.length * 2, true);
  samples.forEach((sample, index) => view.setInt16(44 + index * 2,
    Math.max(-1, Math.min(1, sample)) * (sample < 0 ? 0x8000 : 0x7fff), true));
  return new Blob([buffer], { type: "audio/wav" });
}

export function resampleMono(input: Float32Array, sourceRate: number, targetRate = TARGET_RATE): Float32Array {
  if (sourceRate === targetRate) return input.slice();
  const ratio = sourceRate / targetRate;
  const output = new Float32Array(Math.max(1, Math.floor(input.length / ratio)));
  for (let i = 0; i < output.length; i++) {
    const start = Math.floor(i * ratio);
    const end = Math.min(input.length, Math.max(start + 1, Math.floor((i + 1) * ratio)));
    let sum = 0;
    for (let j = start; j < end; j++) sum += input[j];
    output[i] = sum / (end - start);
  }
  return output;
}

export class PcmWavRecorder {
  private context: AudioContext | null = null;
  private stream: MediaStream | null = null;
  private source: MediaStreamAudioSourceNode | null = null;
  private node: AudioWorkletNode | ScriptProcessorNode | null = null;
  private chunks: Float32Array[] = [];
  private timeout: ReturnType<typeof setTimeout> | null = null;
  private stopped = false;
  private visibilityHandler: (() => void) | null = null;

  async start(onLevel?: AudioLevelListener, onAutoStop?: () => void): Promise<void> {
    if (this.stream) throw new Error("Recorder is already active");
    this.stopped = false;
    this.chunks = [];
    this.stream = await navigator.mediaDevices.getUserMedia({ audio: {
      channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true
    } });
    this.context = new AudioContext({ latencyHint: "interactive" });
    this.source = this.context.createMediaStreamSource(this.stream);
    const receive = (samples: Float32Array) => {
      if (this.stopped) return;
      const copy = samples.slice();
      this.chunks.push(copy);
      const rms = Math.sqrt(copy.reduce((sum, value) => sum + value * value, 0) / Math.max(1, copy.length));
      onLevel?.(Math.min(1, rms * 8), rms > 0.018);
    };
    try {
      const source = `class InnerCosmosPcm extends AudioWorkletProcessor { process(inputs) { const c=inputs[0]&&inputs[0][0]; if(c) this.port.postMessage(c.slice()); return true; } } registerProcessor('inner-cosmos-pcm', InnerCosmosPcm);`;
      const moduleUrl = URL.createObjectURL(new Blob([source], { type: "text/javascript" }));
      try { await this.context.audioWorklet.addModule(moduleUrl); } finally { URL.revokeObjectURL(moduleUrl); }
      const worklet = new AudioWorkletNode(this.context, "inner-cosmos-pcm", { numberOfInputs: 1, numberOfOutputs: 0 });
      worklet.port.onmessage = event => receive(new Float32Array(event.data));
      this.node = worklet;
    } catch {
      const fallback = this.context.createScriptProcessor(4096, 1, 1);
      fallback.onaudioprocess = event => receive(event.inputBuffer.getChannelData(0));
      fallback.connect(this.context.destination);
      this.node = fallback;
    }
    this.source.connect(this.node);
    this.timeout = setTimeout(() => { onAutoStop?.(); }, MAX_DURATION_MS);
    this.visibilityHandler = () => { if (document.hidden) onAutoStop?.(); };
    document.addEventListener("visibilitychange", this.visibilityHandler);
  }

  async stop(cancel = false): Promise<Blob | null> {
    if (this.stopped) return null;
    this.stopped = true;
    if (this.timeout) clearTimeout(this.timeout);
    if (this.visibilityHandler) document.removeEventListener("visibilitychange", this.visibilityHandler);
    this.source?.disconnect(); this.node?.disconnect();
    this.stream?.getTracks().forEach(track => track.stop());
    const rate = this.context?.sampleRate ?? TARGET_RATE;
    await this.context?.close().catch(() => undefined);
    this.stream = null; this.context = null; this.source = null; this.node = null;
    const chunks = this.chunks; this.chunks = [];
    if (cancel || chunks.length === 0) return null;
    const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    const merged = new Float32Array(total);
    let offset = 0;
    chunks.forEach(chunk => { merged.set(chunk, offset); offset += chunk.length; });
    return encodePcmWav(resampleMono(merged, rate));
  }
}
