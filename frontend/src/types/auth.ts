export type AuthResponse = {
  token: string;
  userId: string;
  email: string;
  username: string;
  characterName: string;
  gender: string;
  avatarCode: string;
  profileCompleted: boolean;
};

export type LoginRequest = {
  login: string;
  password: string;
};

export type RegisterRequest = {
  email: string;
  username: string;
  password: string;
};