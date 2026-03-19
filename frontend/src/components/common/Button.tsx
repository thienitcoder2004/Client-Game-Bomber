import type { CSSProperties, ReactNode } from "react";

type Props = {
  children: ReactNode;
  onClick?: () => void;
  type?: "button" | "submit";
  disabled?: boolean;
  style?: CSSProperties;
};

export default function Button({
  children,
  onClick,
  type = "button",
  disabled,
  style,
}: Props) {
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      style={{
        border: "1px solid rgba(255,255,255,0.14)",
        background: disabled
          ? "linear-gradient(180deg, #475569, #334155)"
          : "linear-gradient(180deg, #3b82f6, #1d4ed8)",
        color: "#fff",
        borderRadius: 16,
        padding: "13px 18px",
        fontWeight: 800,
        fontSize: 14,
        letterSpacing: 0.2,
        cursor: disabled ? "not-allowed" : "pointer",
        opacity: disabled ? 0.7 : 1,
        boxShadow: disabled
          ? "none"
          : "0 12px 28px rgba(29,78,216,0.28), inset 0 1px 0 rgba(255,255,255,0.15)",
        transition: "all 0.2s ease",
        ...style,
      }}
    >
      {children}
    </button>
  );
}
