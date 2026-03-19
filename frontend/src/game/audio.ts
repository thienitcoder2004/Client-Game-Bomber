export type GameAudio = {
  step: HTMLAudioElement | null;
  bombPlace: HTMLAudioElement | null;
  bombExplode: HTMLAudioElement | null;
  lastStepAt: number;
  enabled: boolean;
  primed: boolean;
};

function makeAudio(src: string, volume: number) {
  const audio = new Audio(src);
  audio.preload = "auto";
  audio.volume = volume;
  return audio;
}

export function createGameAudio(): GameAudio {
  return {
    step: makeAudio("/sounds/step.wav", 0.22),
    bombPlace: makeAudio("/sounds/bomb_place.wav", 0.35),
    bombExplode: makeAudio("/sounds/bomb_explode.wav", 0.5),
    lastStepAt: 0,
    enabled: true,
    primed: false,
  };
}

function playOneShot(base: HTMLAudioElement | null) {
  if (!base) return;

  const sound = base.cloneNode(true) as HTMLAudioElement;
  sound.volume = base.volume;
  sound.play().catch(() => {});
}

export function primeAudio(audio: GameAudio) {
  if (audio.primed) return;
  audio.primed = true;

  const list = [audio.step, audio.bombPlace, audio.bombExplode];

  for (const a of list) {
    if (!a) continue;
    const prevMuted = a.muted;
    a.muted = true;
    a.play()
      .then(() => {
        a.pause();
        a.currentTime = 0;
        a.muted = prevMuted;
      })
      .catch(() => {
        a.muted = prevMuted;
      });
  }
}

export function playStep(audio: GameAudio, now: number) {
  if (!audio.enabled) return;
  if (now - audio.lastStepAt < 120) return;

  audio.lastStepAt = now;
  playOneShot(audio.step);
}

export function playBombPlace(audio: GameAudio) {
  if (!audio.enabled) return;
  playOneShot(audio.bombPlace);
}

export function playBombExplode(audio: GameAudio) {
  if (!audio.enabled) return;
  playOneShot(audio.bombExplode);
}