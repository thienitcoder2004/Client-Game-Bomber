const TOKEN_KEY = "bomber_token";
const AUTH_KEY = "bomber_auth_user";

export type StoredAuthUser = {
  userId: string;
  email: string;
  username: string;
  characterName: string;
  gender: string;
  avatarCode: string;
  profileCompleted: boolean;
  role: string;
};

export function getStoredToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setStoredToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearStoredToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export function getStoredAuthUser(): StoredAuthUser | null {
  const raw = localStorage.getItem(AUTH_KEY);
  if (!raw) return null;

  try {
    return JSON.parse(raw) as StoredAuthUser;
  } catch {
    return null;
  }
}

export function setStoredAuthUser(user: StoredAuthUser) {
  localStorage.setItem(AUTH_KEY, JSON.stringify(user));
}

export function clearStoredAuthUser() {
  localStorage.removeItem(AUTH_KEY);
}

export function clearAuthStorage() {
  clearStoredToken();
  clearStoredAuthUser();
}