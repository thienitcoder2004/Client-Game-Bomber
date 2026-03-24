export type AuthResponse = {
  token: string;
  userId: string;
  email: string;
  username: string;
  characterName: string;
  gender: string;
  avatarCode: string;
  profileCompleted: boolean;
  role: string;
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

export type ForgotPasswordRequest = {
  email: string;
};

export type ResetPasswordRequest = {
  email: string;
  otpCode: string;
  newPassword: string;
};

export type MessageResponse = {
  message: string;
};