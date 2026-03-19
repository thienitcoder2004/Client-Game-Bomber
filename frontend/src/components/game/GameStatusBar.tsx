export default function GameStatusBar({
  statusText,
  playerLabel,
  compact = false,
}: {
  statusText: string;
  playerLabel: string;
  compact?: boolean;
}) {
  return (
    <div
      style={{
        padding: compact ? "8px 10px" : "10px 14px",
        background:
          "linear-gradient(90deg, rgba(2,6,23,0.98), rgba(15,23,42,0.98))",
        color: "#fff",
        fontWeight: 700,
        fontSize: compact ? 12 : 14,
        display: "flex",
        justifyContent: "space-between",
        gap: 10,
        flexWrap: "wrap",
        lineHeight: 1.4,
        borderBottom: "1px solid rgba(255,255,255,0.08)",
      }}
    >
      <span>{statusText}</span>
      <span>{playerLabel}</span>
    </div>
  );
}
