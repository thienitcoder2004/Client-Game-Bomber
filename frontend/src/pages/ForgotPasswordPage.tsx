import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import Input from "../components/common/Input";
import { authApi } from "../services/authApi";

export default function ForgotPasswordPage() {
  const navigate = useNavigate();

  const [step, setStep] = useState<1 | 2>(1);
  const [email, setEmail] = useState("");
  const [otpCode, setOtpCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [errorText, setErrorText] = useState("");
  const [successText, setSuccessText] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSendOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorText("");
    setSuccessText("");
    setLoading(true);

    try {
      const res = await authApi.requestForgotPassword({ email });
      setSuccessText(
        res.message || "Nếu email tồn tại, mã xác thực đã được gửi.",
      );
      setStep(2);
    } catch (error) {
      setErrorText(
        error instanceof Error ? error.message : "Không thể gửi mã xác thực",
      );
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorText("");
    setSuccessText("");

    if (newPassword !== confirmPassword) {
      setErrorText("Mật khẩu nhập lại không khớp");
      return;
    }

    setLoading(true);

    try {
      const res = await authApi.resetPassword({
        email,
        otpCode,
        newPassword,
      });

      setSuccessText(res.message || "Đặt lại mật khẩu thành công");

      setTimeout(() => {
        navigate("/login");
      }, 1200);
    } catch (error) {
      setErrorText(
        error instanceof Error ? error.message : "Đặt lại mật khẩu thất bại",
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
      <Card style={{ width: 520, maxWidth: "100%" }}>
        <h2 style={{ marginTop: 0 }}>Quên mật khẩu</h2>

        <div
          style={{
            color: "#cbd5e1",
            marginBottom: 16,
            lineHeight: 1.6,
            fontSize: 14,
          }}
        >
          {step === 1
            ? "Nhập email đã đăng ký để nhận mã OTP xác thực."
            : "Nhập mã OTP vừa nhận trong email và mật khẩu mới của bạn."}
        </div>

        {step === 1 ? (
          <form onSubmit={handleSendOtp}>
            <Input
              label="Email"
              value={email}
              onChange={setEmail}
              placeholder="Nhập email đã đăng ký"
            />

            {errorText && (
              <div style={{ color: "#fca5a5", marginBottom: 14 }}>
                {errorText}
              </div>
            )}

            {successText && (
              <div style={{ color: "#86efac", marginBottom: 14 }}>
                {successText}
              </div>
            )}

            <Button type="submit" disabled={loading} style={{ width: "100%" }}>
              {loading ? "Đang gửi..." : "Gửi mã xác thực"}
            </Button>
          </form>
        ) : (
          <form onSubmit={handleResetPassword}>
            <Input
              label="Email"
              value={email}
              onChange={setEmail}
              placeholder="Nhập lại email"
            />
            <Input
              label="Mã OTP"
              value={otpCode}
              onChange={setOtpCode}
              placeholder="Nhập 6 số trong email"
            />
            <Input
              label="Mật khẩu mới"
              type="password"
              value={newPassword}
              onChange={setNewPassword}
              placeholder="Nhập mật khẩu mới"
            />
            <Input
              label="Nhập lại mật khẩu mới"
              type="password"
              value={confirmPassword}
              onChange={setConfirmPassword}
              placeholder="Nhập lại mật khẩu mới"
            />

            {errorText && (
              <div style={{ color: "#fca5a5", marginBottom: 14 }}>
                {errorText}
              </div>
            )}

            {successText && (
              <div style={{ color: "#86efac", marginBottom: 14 }}>
                {successText}
              </div>
            )}

            <div
              style={{
                display: "grid",
                gap: 10,
              }}
            >
              <Button
                type="submit"
                disabled={loading}
                style={{ width: "100%" }}
              >
                {loading ? "Đang xử lý..." : "Đặt lại mật khẩu"}
              </Button>

              <Button
                type="button"
                disabled={loading}
                style={{ width: "100%" }}
                onClick={() => {
                  setStep(1);
                  setErrorText("");
                  setSuccessText("");
                  setOtpCode("");
                }}
              >
                Gửi lại mã khác
              </Button>
            </div>
          </form>
        )}

        <div style={{ marginTop: 16, color: "#cbd5e1" }}>
          <Link to="/login">Quay lại đăng nhập</Link>
        </div>
      </Card>
    </div>
  );
}
