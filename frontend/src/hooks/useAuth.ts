import { useEffect, useState } from "react";
import { profileApi } from "../services/profileApi";
import type { ProfileResponse } from "../types/profile";
import {
  clearAuthStorage,
  getStoredAuthUser,
  getStoredToken,
  setStoredAuthUser,
} from "../utils/storage";

function getInitialProfile(): ProfileResponse | null {
  const storedUser = getStoredAuthUser();

  if (!storedUser) return null;

  return {
    userId: storedUser.userId,
    email: storedUser.email,
    username: storedUser.username,
    characterName: storedUser.characterName,
    gender: storedUser.gender,
    avatarCode: storedUser.avatarCode,
    profileCompleted: storedUser.profileCompleted,
    role: storedUser.role,
  };
}

export function useAuth() {
  const [token, setToken] = useState<string | null>(() => getStoredToken());
  const [profile, setProfile] = useState<ProfileResponse | null>(() =>
    getInitialProfile(),
  );
  const [loading, setLoading] = useState<boolean>(() => !!getStoredToken());

  useEffect(() => {
    if (!token) {
      return;
    }

    let isMounted = true;

    profileApi
      .getMe()
      .then((data) => {
        if (!isMounted) return;

        setProfile(data);
        setStoredAuthUser({
          userId: data.userId,
          email: data.email,
          username: data.username,
          characterName: data.characterName,
          gender: data.gender,
          avatarCode: data.avatarCode,
          profileCompleted: data.profileCompleted,
          role: data.role,
        });
      })
      .catch(() => {
        if (!isMounted) return;

        clearAuthStorage();
        setToken(null);
        setProfile(null);
      })
      .finally(() => {
        if (!isMounted) return;
        setLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [token]);

  const logout = () => {
    clearAuthStorage();
    setToken(null);
    setProfile(null);
    setLoading(false);
  };

  return {
    token,
    profile,
    loading,
    isAuthenticated: !!token,
    isAdmin: profile?.role === "ADMIN",
    setToken,
    setProfile,
    logout,
  };
}