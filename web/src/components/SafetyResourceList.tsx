import type { SafetyResource } from "../api";

function contactHref(resource: SafetyResource): string | null {
  if (!resource.phone) return null;
  const digits = resource.phone.replace(/\D/g, "");
  if (resource.channel === "WHATSAPP") {
    const international = resource.region === "SG" && digits.length === 8 ? `65${digits}` : digits;
    return `https://wa.me/${international}`;
  }
  return `tel:${resource.phone}`;
}

export function SafetyResourceList({ resources, dialLabel, messageLabel }: {
  resources: SafetyResource[];
  dialLabel: string;
  messageLabel: string;
}) {
  if (resources.length === 0) return null;
  return <div className="safety-resource-list">
    {resources.map(resource => {
      const href = contactHref(resource);
      const actionLabel = resource.channel === "WHATSAPP" ? messageLabel : dialLabel;
      return <p key={resource.id} data-resource-region={resource.region} data-resource-category={resource.category}>
        <span>{resource.label}</span>
        {href && <a className="phone-link" href={href}>{actionLabel}{resource.phone}</a>}
      </p>;
    })}
  </div>;
}
