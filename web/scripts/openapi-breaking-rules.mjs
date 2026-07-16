const HTTP_METHODS = ["get", "put", "post", "delete", "options", "head", "patch", "trace"];

function localRef(spec, schema) {
  if (!schema?.$ref?.startsWith("#/")) return schema;
  return schema.$ref.slice(2).split("/").reduce((value, key) => value?.[key], spec);
}

function schemaTypes(schema) {
  if (!schema?.type) return new Set();
  return new Set(Array.isArray(schema.type) ? schema.type : [schema.type]);
}

function compareSchema(oldSpec, newSpec, oldInput, newInput, location, changes, seen = new Set()) {
  const oldSchema = localRef(oldSpec, oldInput);
  const newSchema = localRef(newSpec, newInput);
  if (!oldSchema || !newSchema) return;
  const marker = `${location}:${oldInput?.$ref ?? "inline"}:${newInput?.$ref ?? "inline"}`;
  if (seen.has(marker)) return;
  seen.add(marker);

  const oldTypes = schemaTypes(oldSchema);
  const newTypes = schemaTypes(newSchema);
  const acceptsOldType = (type) => newTypes.has(type) || (type === "integer" && newTypes.has("number"));
  if (oldTypes.size > 0 && newTypes.size > 0 && [...oldTypes].some(type => !acceptsOldType(type))) {
    changes.push({ code: "schema.type.narrow", location,
      detail: `${[...oldTypes].sort().join("|")} -> ${[...newTypes].sort().join("|")}` });
  }

  const oldEnum = new Set(oldSchema.enum ?? []);
  const newEnum = new Set(newSchema.enum ?? []);
  if (oldEnum.size === 0 && newEnum.size > 0) {
    changes.push({ code: "schema.enum.add", location, detail: "previously unrestricted values are now constrained" });
  }
  for (const value of oldEnum) {
    if (!newEnum.has(value)) changes.push({ code: "schema.enum.remove", location, detail: String(value) });
  }

  const oldRequired = new Set(oldSchema.required ?? []);
  const newRequired = new Set(newSchema.required ?? []);
  for (const name of newRequired) {
    if (!oldRequired.has(name)) changes.push({ code: "schema.required.add", location, detail: name });
  }
  for (const name of oldRequired) {
    if (!newRequired.has(name)) changes.push({ code: "schema.required.remove", location, detail: name });
  }

  const oldProperties = oldSchema.properties ?? {};
  const newProperties = newSchema.properties ?? {};
  for (const [name, oldProperty] of Object.entries(oldProperties)) {
    if (!(name in newProperties)) {
      changes.push({ code: "schema.property.remove", location, detail: name });
      continue;
    }
    compareSchema(oldSpec, newSpec, oldProperty, newProperties[name], `${location}.${name}`, changes, seen);
  }
  if (oldSchema.items && newSchema.items) {
    compareSchema(oldSpec, newSpec, oldSchema.items, newSchema.items, `${location}[]`, changes, seen);
  }
}

function parameters(pathItem, operation) {
  return [...(pathItem?.parameters ?? []), ...(operation?.parameters ?? [])];
}

export function findBreakingChanges(oldSpec, newSpec) {
  const changes = [];
  for (const [path, oldPathItem] of Object.entries(oldSpec.paths ?? {})) {
    const newPathItem = newSpec.paths?.[path];
    if (!newPathItem) {
      changes.push({ code: "path.remove", location: path, detail: "path removed" });
      continue;
    }
    for (const method of HTTP_METHODS) {
      const oldOperation = oldPathItem?.[method];
      if (!oldOperation) continue;
      const newOperation = newPathItem?.[method];
      const operationLocation = `${method.toUpperCase()} ${path}`;
      if (!newOperation) {
        changes.push({ code: "operation.remove", location: operationLocation, detail: "operation removed" });
        continue;
      }

      const oldParams = new Map(parameters(oldPathItem, oldOperation).map(item => [`${item.in}:${item.name}`, item]));
      for (const item of parameters(newPathItem, newOperation)) {
        const key = `${item.in}:${item.name}`;
        const oldItem = oldParams.get(key);
        if (item.required && !oldItem?.required) {
          changes.push({ code: oldItem ? "parameter.required" : "parameter.required.add", location: operationLocation, detail: key });
        }
        if (oldItem) compareSchema(oldSpec, newSpec, oldItem.schema, item.schema, `${operationLocation} parameter ${key}`, changes);
      }

      if (newOperation.requestBody?.required && !oldOperation.requestBody?.required) {
        changes.push({ code: "requestBody.required", location: operationLocation, detail: "request body became required" });
      }
      const oldContent = oldOperation.requestBody?.content ?? {};
      const newContent = newOperation.requestBody?.content ?? {};
      for (const [mediaType, oldMedia] of Object.entries(oldContent)) {
        if (!newContent[mediaType]) {
          changes.push({ code: "request.mediaType.remove", location: operationLocation, detail: mediaType });
          continue;
        }
        compareSchema(oldSpec, newSpec, oldMedia.schema, newContent[mediaType].schema,
          `${operationLocation} request ${mediaType}`, changes);
      }

      for (const [status, oldResponse] of Object.entries(oldOperation.responses ?? {})) {
        if (!/^2\d\d$/.test(status) || newOperation.responses?.[status]) continue;
        changes.push({ code: "response.success.remove", location: operationLocation, detail: status });
      }
    }
  }

  for (const [name, oldSchema] of Object.entries(oldSpec.components?.schemas ?? {})) {
    const newSchema = newSpec.components?.schemas?.[name];
    if (!newSchema) {
      changes.push({ code: "component.schema.remove", location: `#/components/schemas/${name}`, detail: "schema removed" });
      continue;
    }
    compareSchema(oldSpec, newSpec, oldSchema, newSchema, `#/components/schemas/${name}`, changes);
  }
  return changes;
}
