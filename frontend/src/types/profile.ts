export type ProfileResponse = {
  userId: string;
  email: string;
  username: string;
  characterName: string;
  gender: string;
  avatarCode: string;
  profileCompleted: boolean;
  role: string;
};

export type UpdateProfileRequest = {
  characterName: string;
  gender: "MALE" | "FEMALE";
  avatarCode: string;
};