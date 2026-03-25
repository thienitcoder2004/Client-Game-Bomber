import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import BomberGame from "../components/BomberGame";
import Button from "../components/common/Button";

export default function GamePage() {
  const navigate = useNavigate();

  /**
   * Xác định nút "Về sảnh" sẽ quay về đâu.
   *
   * - room  -> /rooms
   * - quick -> /quick-play
   * - còn lại -> /lobby
   */
  const backPath = useMemo(() => {
    const params = new URLSearchParams(window.location.search);
    const sourceMode = params.get("mode");

    if (sourceMode === "room") return "/rooms";
    if (sourceMode === "quick") return "/quick-play";
    return "/lobby";
  }, []);

  return (
    <div
      style={{
        minHeight: "100vh",
        padding: "8px 6px 10px",
      }}
    >
      <div
        style={{
          width: "fit-content",
          margin: "0 auto 8px",
          position: "relative",
          zIndex: 50,
        }}
      >
        <Button
          onClick={() => navigate(backPath)}
          style={{
            background: "linear-gradient(180deg, #475569, #334155)",
            padding: "10px 14px",
          }}
        >
          ← Về sảnh
        </Button>
      </div>

      <BomberGame backPath={backPath} />
    </div>
  );
}
