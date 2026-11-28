export type UploadedAdminPlaceImage = { key: string; imageUrl: string };

export function adminPlaceFileKey(file: { name: string; size: number; lastModified: number }) {
  return `${file.name}:${file.size}:${file.lastModified}`;
}

/** 같은 파일 서명이면 이미 받은 주소를 다시 쓰고, 파일이 다르면 null입니다. */
export function reusedAdminPlaceImage(cache: UploadedAdminPlaceImage | null, file: { name: string; size: number; lastModified: number }) {
  const key = adminPlaceFileKey(file);
  return cache?.key === key ? cache.imageUrl : null;
}
