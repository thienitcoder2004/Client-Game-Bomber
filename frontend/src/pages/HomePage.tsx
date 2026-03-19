import { Link } from "react-router-dom";
import Button from "../components/common/Button";
import Card from "../components/common/Card";

export default function HomePage() {
  return (
    <div
      style={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        padding: 24,
      }}
    >
      <Card style={{ width: 720, maxWidth: "100%" }}>
        <h1 style={{ marginTop: 0, fontSize: 42, marginBottom: 10 }}>
          BOMBER GAME
        </h1>
        <p style={{ color: "#cbd5e1", fontSize: 17, lineHeight: 1.6 }}>
          Game đặt bom 4 người với tài khoản, hồ sơ nhân vật, lịch sử đấu và
          realtime WebSocket.
        </p>

        <div
          style={{
            display: "flex",
            gap: 14,
            marginTop: 24,
            flexWrap: "wrap",
          }}
        >
          <Link to="/login">
            <Button>Đăng nhập</Button>
          </Link>

          <Link to="/register">
            <Button
              style={{
                background: "linear-gradient(180deg, #22c55e, #16a34a)",
              }}
            >
              Đăng ký
            </Button>
          </Link>
        </div>
      </Card>
    </div>
  );
}
