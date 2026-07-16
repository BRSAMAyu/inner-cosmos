import test from "node:test";
import assert from "node:assert/strict";
import { findBreakingChanges } from "./openapi-breaking-rules.mjs";

const baseline = {
  paths: {
    "/items": {
      post: {
        parameters: [{ in: "header", name: "Idempotency-Key", required: true, schema: { type: "string" } }],
        requestBody: { required: true, content: { "application/json": { schema: { $ref: "#/components/schemas/Input" } } } },
        responses: { "200": { description: "ok" } },
      },
    },
  },
  components: { schemas: { Input: { type: "object", required: ["name"], properties: {
    name: { type: "string" }, mode: { type: "string", enum: ["A", "B"] },
  } } } },
};

const copy = () => structuredClone(baseline);

test("unchanged and additive optional fields remain compatible", () => {
  const candidate = copy();
  candidate.components.schemas.Input.properties.note = { type: "string" };
  assert.deepEqual(findBreakingChanges(baseline, candidate), []);
});

test("removed operations are breaking", () => {
  const candidate = copy();
  delete candidate.paths["/items"].post;
  assert.ok(findBreakingChanges(baseline, candidate).some(change => change.code === "operation.remove"));
});

test("new required request fields are breaking", () => {
  const candidate = copy();
  candidate.components.schemas.Input.required.push("note");
  candidate.components.schemas.Input.properties.note = { type: "string" };
  assert.ok(findBreakingChanges(baseline, candidate).some(change => change.code === "schema.required.add"));
});

test("enum narrowing and type changes are breaking", () => {
  const candidate = copy();
  candidate.components.schemas.Input.properties.mode.enum = ["A"];
  candidate.components.schemas.Input.properties.name.type = "number";
  const codes = findBreakingChanges(baseline, candidate).map(change => change.code);
  assert.ok(codes.includes("schema.enum.remove"));
  assert.ok(codes.includes("schema.type.narrow"));
});

test("nullable and numeric type widening remain compatible", () => {
  const candidate = copy();
  candidate.components.schemas.Input.properties.name.type = ["string", "null"];
  assert.deepEqual(findBreakingChanges(baseline, candidate), []);
});

test("adding an enum to an unrestricted field is breaking", () => {
  const candidate = copy();
  candidate.components.schemas.Input.properties.name.enum = ["known"];
  assert.ok(findBreakingChanges(baseline, candidate).some(change => change.code === "schema.enum.add"));
});
