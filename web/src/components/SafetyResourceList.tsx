import type { SafetyResource } from "../api";

export function SafetyResourceList({ resources, dialLabel }: { resources: SafetyResource[]; dialLabel: string }) {
  if (resources.length === 0) return null;
  return <div className="safety-resource-list">
    {resources.map(resource => {
      return <p key={resource.id} data-resource-region={resource.region} data-resource-category={resource.category}>
        <span>{resource.label}</span>
        {resource.phone && <a className="phone-link" href={`tel:${resource.phone}`}>{dialLabel}{resource.phone}</a>}
      </p>;
    })}
  </div>;
}
