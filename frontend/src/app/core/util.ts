export function digitsOnly(value: string): string {
  return value.replace(/\D/g, '');
}

/** "9876543210" → "98765 43210" (how Indians usually write mobile numbers). */
export function formatIndianMobile(value: string): string {
  const d = digitsOnly(value).slice(0, 10);
  return d.length > 5 ? `${d.slice(0, 5)} ${d.slice(5)}` : d;
}

export function isValidIndianMobile(value: string): boolean {
  return /^[6-9]\d{9}$/.test(digitsOnly(value));
}

/** Only allow in-app paths as redirect targets, never another site. */
export function safeReturnUrl(url: unknown, fallback = '/'): string {
  return typeof url === 'string' && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\')
    ? url
    : fallback;
}

/** Today's date in the browser's time zone, as YYYY-MM-DD. */
export function todayIso(now = new Date()): string {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

/** Centre-crops a photo to a square JPEG. Browsers apply the camera's rotation (EXIF) for us here. */
export async function squareJpeg(file: Blob, size: number): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  try {
    const side = Math.min(bitmap.width, bitmap.height);
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Canvas not supported');
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, size, size);
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, 0, 0, size, size);
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('Could not process photo'))), 'image/jpeg', 0.9),
    );
  } finally {
    bitmap.close();
  }
}

export function storageGet(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function storageSet(key: string, value: string | null): void {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    // Storage can be unavailable (private mode, blocked cookies). Nothing important depends on it.
  }
}
