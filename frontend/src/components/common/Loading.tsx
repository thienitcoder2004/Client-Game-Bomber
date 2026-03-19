export default function Loading({ text = "Đang tải..." }: { text?: string }) {
  return (
    <div
      style={{
        textAlign: "center",
        padding: 32,
        color: "#cbd5e1",
      }}
    >
      {text}
    </div>
  );
}
