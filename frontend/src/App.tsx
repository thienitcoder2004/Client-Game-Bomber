import type { JSX } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { getStoredAuthUser, getStoredToken } from "./utils/storage";
import HomePage from "./pages/HomePage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import ForgotPasswordPage from "./pages/ForgotPasswordPage";
import CharacterSelectPage from "./pages/CharacterSelectPage";
import LobbyPage from "./pages/LobbyPage";
import RoomLobbyPage from "./pages/RoomLobbyPage";
import HistoryPage from "./pages/HistoryPage";
import GamePage from "./pages/GamePage";
import AdminPage from "./pages/AdminPage";
import FriendsPage from "./pages/FriendsPage";

function ProtectedRoute({ children }: { children: JSX.Element }) {
  const token = getStoredToken();
  const location = useLocation();

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return children;
}

function AdminRoute({ children }: { children: JSX.Element }) {
  const token = getStoredToken();
  const user = getStoredAuthUser();
  const location = useLocation();

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (user?.role !== "ADMIN") {
    return <Navigate to="/lobby" replace />;
  }

  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />

      <Route
        path="/character"
        element={
          <ProtectedRoute>
            <CharacterSelectPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/lobby"
        element={
          <ProtectedRoute>
            <LobbyPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/rooms"
        element={
          <ProtectedRoute>
            <RoomLobbyPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/history"
        element={
          <ProtectedRoute>
            <HistoryPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/friends"
        element={
          <ProtectedRoute>
            <FriendsPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/game"
        element={
          <ProtectedRoute>
            <GamePage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/admin"
        element={
          <AdminRoute>
            <AdminPage />
          </AdminRoute>
        }
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
