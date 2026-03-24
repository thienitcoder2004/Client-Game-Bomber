import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import Input from "../components/common/Input";
import { authApi } from "../services/authApi";
import { setStoredAuthUser, setStoredToken } from "../utils/storage";

export default function LoginPage() {
  const navigate = useNavigate();

  const [login, setLogin] = useState("");
  const [password, setPassword] = useState("");
  const [errorText, setErrorText] = useState("");
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorText("");
    setLoading(true);

    try {
      const res = await authApi.login({ login, password });

      setStoredToken(res.token);
      setStoredAuthUser({
        userId: res.userId,
        email: res.email,
        username: res.username,
        characterName: res.characterName,
        gender: res.gender,
        avatarCode: res.avatarCode,
        profileCompleted: res.profileCompleted,
        role: res.role,
      });

      if (res.profileCompleted) {
        navigate("/lobby");
      } else {
        navigate("/character");
      }
    } catch (error) {
      setErrorText(
        error instanceof Error ? error.message : "Đăng nhập thất bại",
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        padding: 24,
      }}
    >
      <Card style={{ width: 460, maxWidth: "100%" }}>
        <h2 style={{ marginTop: 0 }}>Đăng nhập</h2>

        <form onSubmit={onSubmit}>
          <Input
            label="Email hoặc Username"
            value={login}
            onChange={setLogin}
            placeholder="Nhập email hoặc username"
          />

          <Input
            label="Mật khẩu"
            type="password"
            value={password}
            onChange={setPassword}
            placeholder="Nhập mật khẩu"
          />

          {errorText && (
            <div style={{ color: "#fca5a5", marginBottom: 14 }}>
              {errorText}
            </div>
          )}

          <Button type="submit" disabled={loading} style={{ width: "100%" }}>
            {loading ? "Đang xử lý..." : "Đăng nhập"}
          </Button>
        </form>

        <div
          style={{
            marginTop: 16,
            color: "#cbd5e1",
            display: "flex",
            justifyContent: "space-between",
            gap: 12,
            flexWrap: "wrap",
          }}
        >
          <span>
            Chưa có tài khoản? <Link to="/register">Đăng ký</Link>
          </span>
          <Link to="/forgot-password">Quên mật khẩu?</Link>
        </div>
      </Card>
    </div>
  );
}
