import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Button from "../components/common/Button";
import Card from "../components/common/Card";
import Input from "../components/common/Input";
import Loading from "../components/common/Loading";
import { useAuth } from "../hooks/useAuth";
import { profileApi } from "../services/profileApi";
import { setStoredAuthUser } from "../utils/storage";

export default function CharacterSelectPage() {
  const navigate = useNavigate();
  const { profile, loading } = useAuth();

  const [characterName, setCharacterName] = useState("");
  const [gender, setGender] = useState<"MALE" | "FEMALE">("MALE");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (!profile) return;

    setCharacterName(profile.characterName || "");
    setGender((profile.gender as "MALE" | "FEMALE") || "MALE");
  }, [profile]);

  // Mã avatar lưu xuống backend
  const avatarCode = gender === "MALE" ? "male-default" : "female-default";

  // Ảnh preview ngoài màn chọn nhân vật
  // Bạn có thể đổi sang ảnh khác nếu muốn
  const previewImage = useMemo(() => {
    return gender === "MALE" ? "/images/hinh1.png" : "/images/nuhinh1.png";
  }, [gender]);

  const onSave = async () => {
    setSaving(true);
    setMessage("");

    try {
      const res = await profileApi.updateMe({
        characterName,
        gender,
        avatarCode,
      });

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

      setMessage("Lưu nhân vật thành công");
      setTimeout(() => navigate("/lobby"), 700);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Cập nhật thất bại");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <Loading text="Đang tải hồ sơ..." />;
  }

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        padding: 24,
      }}
    >
      <Card style={{ width: 760, maxWidth: "100%" }}>
        <h2 style={{ marginTop: 0 }}>Chọn nhân vật</h2>

        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1.15fr 0.95fr",
            gap: 20,
            alignItems: "stretch",
          }}
        >
          <div>
            <Input
              label="Tên nhân vật"
              value={characterName}
              onChange={setCharacterName}
            />

            <div style={{ marginBottom: 12, fontWeight: 700 }}>Giới tính</div>

            <div style={{ display: "flex", gap: 12, marginBottom: 18 }}>
              <button
                type="button"
                onClick={() => setGender("MALE")}
                style={{
                  flex: 1,
                  padding: "14px 16px",
                  borderRadius: 14,
                  border:
                    gender === "MALE"
                      ? "2px solid #3b82f6"
                      : "1px solid rgba(255,255,255,0.12)",
                  background:
                    gender === "MALE"
                      ? "rgba(59,130,246,0.18)"
                      : "rgba(255,255,255,0.05)",
                  color: "#fff",
                  fontWeight: 700,
                  cursor: "pointer",
                }}
              >
                Nam
              </button>

              <button
                type="button"
                onClick={() => setGender("FEMALE")}
                style={{
                  flex: 1,
                  padding: "14px 16px",
                  borderRadius: 14,
                  border:
                    gender === "FEMALE"
                      ? "2px solid #ec4899"
                      : "1px solid rgba(255,255,255,0.12)",
                  background:
                    gender === "FEMALE"
                      ? "rgba(236,72,153,0.18)"
                      : "rgba(255,255,255,0.05)",
                  color: "#fff",
                  fontWeight: 700,
                  cursor: "pointer",
                }}
              >
                Nữ
              </button>
            </div>

            {message && (
              <div
                style={{
                  marginBottom: 14,
                  color: message.includes("thành công") ? "#86efac" : "#fca5a5",
                }}
              >
                {message}
              </div>
            )}

            <div style={{ display: "flex", gap: 12 }}>
              <Button onClick={onSave} disabled={saving}>
                {saving ? "Đang lưu..." : "Xác nhận nhân vật"}
              </Button>
            </div>
          </div>

          <div
            style={{
              borderRadius: 18,
              border: "1px solid rgba(255,255,255,0.08)",
              background: "rgba(255,255,255,0.04)",
              padding: 16,
              display: "flex",
              flexDirection: "column",
            }}
          >
            <div style={{ fontWeight: 700, marginBottom: 12 }}>Xem trước</div>

            <div
              style={{
                height: 240,
                borderRadius: 16,
                background:
                  "radial-gradient(circle at top, rgba(59,130,246,0.16), rgba(15,23,42,0.4))",
                border: "1px solid rgba(255,255,255,0.06)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                marginBottom: 14,
                overflow: "hidden",
              }}
            >
              <img
                src={previewImage}
                alt={gender === "MALE" ? "Nhân vật nam" : "Nhân vật nữ"}
                style={{
                  width: 150,
                  height: 150,
                  objectFit: "contain",
                  imageRendering: "pixelated",
                  filter: "drop-shadow(0 12px 20px rgba(0,0,0,0.35))",
                }}
              />
            </div>

            <div style={{ color: "#cbd5e1", marginBottom: 8 }}>
              Tên: {characterName || "-"}
            </div>
            <div style={{ color: "#cbd5e1", marginBottom: 8 }}>
              Giới tính: {gender === "MALE" ? "Nam" : "Nữ"}
            </div>
            <div style={{ color: "#cbd5e1" }}>Mẫu nhân vật: {avatarCode}</div>
          </div>
        </div>
      </Card>
    </div>
  );
}
