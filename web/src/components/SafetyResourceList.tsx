// Shared by SafetyResourceCard (the persistent in-conversation crisis alert) and SafetyHarborPage
// (the freely reachable support space) so the phone-number extraction logic lives in exactly one
// place. Renders real backend content (GET /api/safety/resources) verbatim -- never invents a number.
const PHONE_PATTERN = /\d{3,4}[-\s]?\d{7,8}|\d{3}[-\s]?\d{3}[-\s]?\d{4}|\b(?:110|120|119|12320|12355|988)\b/;

export function SafetyResourceList({ resources, dialLabel }: { resources: string[]; dialLabel: string }) {
  if (resources.length === 0) return null;
  return <div className="safety-resource-list">
    {resources.map(line => {
      const match = line.match(PHONE_PATTERN);
      return <p key={line}>
        <span>{line}</span>
        {match && <a className="phone-link" href={`tel:${match[0].replace(/[\s-]/g, "")}`}>{dialLabel}{match[0]}</a>}
      </p>;
    })}
  </div>;
}
