type Props = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: string;
  placeholder?: string;
};

export default function Input({
  label,
  value,
  onChange,
  type = "text",
  placeholder,
}: Props) {
  return (
    <label style={{ display: "block", marginBottom: 14 }}>
      <div
        style={{
          marginBottom: 8,
          fontWeight: 800,
          color: "#e2e8f0",
          fontSize: 14,
        }}
      >
        {label}
      </div>

      <input
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        style={{
          width: "100%",
          padding: "13px 14px",
          borderRadius: 16,
          border: "1px solid rgba(255,255,255,0.12)",
          background: "rgba(255,255,255,0.05)",
          color: "#fff",
          outline: "none",
          boxSizing: "border-box",
          fontSize: 14,
        }}
      />
    </label>
  );
}
