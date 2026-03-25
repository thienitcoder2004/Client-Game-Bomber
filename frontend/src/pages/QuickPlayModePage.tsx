import { useNavigate } from "react-router-dom";
import Button from "../components/common/Button";
import Card from "../components/common/Card";

const badgeStyle: React.CSSProperties = {
  display: "inline-flex",
  alignItems: "center",
  gap: 8,
  padding: "8px 12px",
  borderRadius: 999,
  background: "rgba(255,255,255,0.06)",
  border: "1px solid rgba(255,255,255,0.08)",
  color: "#e2e8f0",
  fontSize: 13,
  fontWeight: 700,
};

/**
 * Trang chọn kiểu chơi nhanh.
 *
 * - SOLO: 4 người, ai sống cuối cùng thắng.
 * - DUO: 4 người, chia 2 đội 2v2.
 */
export default function QuickPlayModePage() {
  const navigate = useNavigate();

  /**
   * Vào quick play chơi đơn.
   * mode=quick dùng để biết đây là đi từ quick play.
   * queueMode=SOLO dùng để backend ghép vào queue solo.
   */
  const goToSolo = () => {
    navigate("/game?mode=quick&queueMode=SOLO&requiredPlayers=4");
  };

  /**
   * Vào quick play chơi đôi.
   * queueMode=DUO để backend biết phải chia team 2v2.
   */
  const goToDuo = () => {
    navigate("/game?mode=quick&queueMode=DUO&requiredPlayers=4");
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        padding: 24,
        background:
          "radial-gradient(circle at top left, rgba(59,130,246,0.20), transparent 24%), radial-gradient(circle at top right, rgba(34,197,94,0.12), transparent 18%), radial-gradient(circle at bottom right, rgba(168,85,247,0.12), transparent 20%), linear-gradient(180deg, #030712 0%, #08111f 45%, #0f172a 100%)",
      }}
    >
      <div style={{ maxWidth: 1180, margin: "0 auto" }}>
        <Card style={{ padding: 28, marginBottom: 18 }}>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "flex-start",
              gap: 18,
              flexWrap: "wrap",
            }}
          >
            <div style={{ maxWidth: 760 }}>
              <div style={{ ...badgeStyle, marginBottom: 14 }}>
                ⚡ QUICK PLAY
              </div>

              <h1
                style={{
                  margin: 0,
                  color: "#fff",
                  fontSize: 42,
                  lineHeight: 1.1,
                }}
              >
                Chọn kiểu chơi nhanh
              </h1>

              <p
                style={{
                  color: "#cbd5e1",
                  lineHeight: 1.8,
                  marginTop: 14,
                  marginBottom: 0,
                  fontSize: 16,
                }}
              >
                Bạn có thể vào trận theo kiểu đấu tự do hoặc đấu cặp. Mỗi trận
                đều đủ 4 người, nhưng luật thắng thua sẽ khác nhau.
              </p>
            </div>

            <Button
              onClick={() => navigate("/lobby")}
              style={{
                background: "linear-gradient(180deg, #475569, #334155)",
                boxShadow: "none",
              }}
            >
              ← Về sảnh
            </Button>
          </div>
        </Card>

        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
            gap: 18,
          }}
        >
          <BigModeCard
            title="CHƠI ĐƠN"
            titleColor="#86efac"
            description="4 người, mỗi người là 1 phe riêng. Ai sống cuối cùng sẽ thắng. Phù hợp khi muốn đấu tự do."
            subInfo="Luật: 1 người thắng cuối cùng"
            background="linear-gradient(135deg, rgba(34,197,94,0.16), rgba(255,255,255,0.03))"
            border="1px solid rgba(34,197,94,0.20)"
            buttonText="Vào chơi đơn"
            onClick={goToSolo}
          />

          <BigModeCard
            title="CHƠI ĐÔI"
            titleColor="#93c5fd"
            description="4 người chia thành 2 đội. Đội bên trái gồm Player 1 + Player 3, đội bên phải gồm Player 2 + Player 4. Chỉ còn 1 đội sống là thắng."
            subInfo="Luật: 2v2 theo đội"
            background="linear-gradient(135deg, rgba(59,130,246,0.16), rgba(255,255,255,0.03))"
            border="1px solid rgba(59,130,246,0.20)"
            buttonText="Vào chơi đôi"
            onClick={goToDuo}
          />
        </div>
      </div>
    </div>
  );
}

type BigModeCardProps = {
  title: string;
  titleColor: string;
  description: string;
  subInfo: string;
  background: string;
  border: string;
  buttonText: string;
  onClick: () => void;
};

function BigModeCard({
  title,
  titleColor,
  description,
  subInfo,
  background,
  border,
  buttonText,
  onClick,
}: BigModeCardProps) {
  return (
    <Card
      style={{
        padding: 28,
        minHeight: 340,
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        background,
        border,
      }}
    >
      <div>
        <div
          style={{
            display: "inline-flex",
            padding: "8px 12px",
            borderRadius: 999,
            fontWeight: 800,
            letterSpacing: 0.4,
            color: titleColor,
            background: "rgba(255,255,255,0.05)",
            border: "1px solid rgba(255,255,255,0.08)",
            marginBottom: 18,
          }}
        >
          {title}
        </div>

        <div
          style={{
            fontSize: 18,
            lineHeight: 1.8,
            color: "#e2e8f0",
            marginBottom: 16,
          }}
        >
          {description}
        </div>

        <div
          style={{
            display: "inline-flex",
            padding: "8px 12px",
            borderRadius: 999,
            fontSize: 13,
            fontWeight: 800,
            color: "#fff",
            background: "rgba(15,23,42,0.46)",
            border: "1px solid rgba(255,255,255,0.08)",
          }}
        >
          {subInfo}
        </div>
      </div>

      <div style={{ marginTop: 24 }}>
        <Button
          onClick={onClick}
          style={{
            width: "100%",
            padding: "18px 20px",
            fontSize: 18,
          }}
        >
          {buttonText}
        </Button>
      </div>
    </Card>
  );
}
