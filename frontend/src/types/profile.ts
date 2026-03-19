export type ProfileResponse = {
  userId: string;
  email: string;
  username: string;
  characterName: string;
  gender: string;
  avatarCode: string;
  profileCompleted: boolean;
};

export type UpdateProfileRequest = {
  characterName: string;
  gender: "MALE" | "FEMALE";
  avatarCode: string;
};