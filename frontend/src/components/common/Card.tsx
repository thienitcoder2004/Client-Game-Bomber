import type { CSSProperties, ReactNode } from "react";

export default function Card({
  children,
  style,
}: {
  children: ReactNode;
  style?: CSSProperties;
}) {
  return (
    <div
      style={{
        background:
          "linear-gradient(180deg, rgba(15,23,42,0.96), rgba(15,23,42,0.88))",
        border: "1px solid rgba(255,255,255,0.08)",
        borderRadius: 24,
        padding: 22,
        boxShadow:
          "0 18px 42px rgba(0,0,0,0.34), inset 0 1px 0 rgba(255,255,255,0.04)",
        backdropFilter: "blur(10px)",
        ...style,
      }}
    >
      {children}
    </div>
  );
}
