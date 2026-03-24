import type { Assets } from "./types";

/**
 * Load 1 ảnh.
 * Nếu lỗi thì trả về null để game vẫn chạy bằng fallback canvas.
 */
function loadImage(src: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => resolve(null);
    img.src = src;
  });
}

/**
 * Load toàn bộ asset game.
 * Bao gồm:
 * - sprite nam / nữ
 * - tường
 * - bom
 * - item thường
 * - item mới: dịch chuyển, bom random, bom đóng băng
 */
export async function loadAssets(): Promise<Assets> {
  const [
    maleDownStand,
    maleDownWalk,
    maleUpStand,
    maleUpWalk,
    maleLeftStand,
    maleLeftWalk,
    maleRightStand,
    maleRightWalk,

    femaleDownStand,
    femaleDownWalk,
    femaleUpStand,
    femaleUpWalk,
    femaleLeftStand,
    femaleLeftWalk,
    femaleRightStand,
    femaleRightWalk,

    hardWall,
    softWall,

    bombCap1,
    bombCap2,
    bombCap3,

    bombUp,
    flameUp,
    speedUp,
    shield,
    heart,

    teleport,
    randomBomb,
    freezeBomb,
  ] = await Promise.all([
    // ===== Nhân vật nam =====
    loadImage("/images/hinh1.png"),
    loadImage("/images/hinh1buoc.png"),
    loadImage("/images/quaylung.png"),
    loadImage("/images/quaylungbuoc.png"),
    loadImage("/images/xoaytrai.png"),
    loadImage("/images/xoaytraibuoc.png"),
    loadImage("/images/xoayphai.png"),
    loadImage("/images/xoayphaibuoc.png"),

    // ===== Nhân vật nữ =====
    loadImage("/images/nuhinh1.png"),
    loadImage("/images/nuhinh1buoc.png"),
    loadImage("/images/nuquaylung.png"),
    loadImage("/images/nuquaylungbuoc.png"),
    loadImage("/images/nuxoaytrai.png"),
    loadImage("/images/nuxoaytraibuoc.png"),
    loadImage("/images/nuxoayphai.png"),
    loadImage("/images/nuxoayphaibuoc.png"),

    // ===== Map =====
    loadImage("/images/tuonggachphakoduoc.png"),
    loadImage("/images/tuonggophaduoc.png"),

    // ===== Bom =====
    loadImage("/images/bomcap1.png"),
    loadImage("/images/bomcap2.png"),
    loadImage("/images/bomcap3.png"),

    // ===== Item cũ =====
    loadImage("/images/bomthem.png"),
    loadImage("/images/flame_up.png"),
    loadImage("/images/dichuyen.png"),
    loadImage("/images/flame_plus.png"),
    loadImage("/images/heart_plus.png"),

    // ===== Item mới =====
    loadImage("/images/dichchuyen.png"),
    loadImage("/images/bomnolungtung.png"),
    loadImage("/images/dongbang.png"),
  ]);

  return {
    male: {
      downStand: maleDownStand,
      downWalk: maleDownWalk,
      upStand: maleUpStand,
      upWalk: maleUpWalk,
      leftStand: maleLeftStand,
      leftWalk: maleLeftWalk,
      rightStand: maleRightStand,
      rightWalk: maleRightWalk,
    },

    female: {
      downStand: femaleDownStand,
      downWalk: femaleDownWalk,
      upStand: femaleUpStand,
      upWalk: femaleUpWalk,
      leftStand: femaleLeftStand,
      leftWalk: femaleLeftWalk,
      rightStand: femaleRightStand,
      rightWalk: femaleRightWalk,
    },

    map: {
      hardWall,
      softWall,
    },

    bomb: {
      cap1: bombCap1,
      cap2: bombCap2,
      cap3: bombCap3,
    },

    items: {
      bombUp,
      flameUp,
      speedUp,
      shield,
      heart,

      // item mới
      teleport,
      randomBomb,
      freezeBomb,
    },
  };
}