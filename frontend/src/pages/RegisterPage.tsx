import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import Input from "../components/common/Input";
import { authApi } from "../services/authApi";
import { setStoredAuthUser, setStoredToken } from "../utils/storage";

export default function RegisterPage() {
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errorText, setErrorText] = useState("");
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorText("");

    if (password !== confirmPassword) {
      setErrorText("Mật khẩu nhập lại không khớp");
      return;
    }

    setLoading(true);

    try {
      const res = await authApi.register({
        email,
        username,
        password,
      });

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

      navigate("/character");
    } catch (error) {
      setErrorText(error instanceof Error ? error.message : "Đăng ký thất bại");
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
      <Card style={{ width: 520, maxWidth: "100%" }}>
        <h2 style={{ marginTop: 0 }}>Đăng ký tài khoản</h2>

        <form onSubmit={onSubmit}>
          <Input label="Email" value={email} onChange={setEmail} />
          <Input label="Username" value={username} onChange={setUsername} />
          <Input
            label="Mật khẩu"
            type="password"
            value={password}
            onChange={setPassword}
          />
          <Input
            label="Nhập lại mật khẩu"
            type="password"
            value={confirmPassword}
            onChange={setConfirmPassword}
          />

          {errorText && (
            <div style={{ color: "#fca5a5", marginBottom: 14 }}>
              {errorText}
            </div>
          )}

          <Button type="submit" disabled={loading} style={{ width: "100%" }}>
            {loading ? "Đang xử lý..." : "Tạo tài khoản"}
          </Button>
        </form>

        <div style={{ marginTop: 16, color: "#cbd5e1" }}>
          Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
        </div>
      </Card>
    </div>
  );
}
