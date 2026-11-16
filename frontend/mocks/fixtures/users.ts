import type { UserRow } from "./types.ts";

export const ME_ID = "u-seoyeon";

export const users: UserRow[] = [
  {
    id: "u-seoyeon",
    username: "seoyeon",
    nickname: "서연",
    profileImageUrl: "/images/profiles/seoyeon.jpg",
    bio: "좋아하는 장소를 천천히 모아요.\n서울 · 카페와 산책을 좋아해요.",
    region: "서울",
    postCount: 24,
    followerCount: 328,
    followingCount: 162,
  },
  {
    id: "u-jimin",
    username: "jimin",
    nickname: "지민",
    profileImageUrl: "/images/photos/shop-people.jpg",
    bio: "좋아하는 장소를 천천히 모아요.\n서울 · 카페와 산책을 좋아해요.",
    region: "성수동",
    postCount: 41,
    followerCount: 1204,
    followingCount: 210,
  },
  {
    id: "u-minsu",
    username: "minsu",
    nickname: "민수",
    profileImageUrl: "/images/photos/park.jpg",
    bio: "주말마다 걷기 좋은 길을 찾아요.",
    region: "서울숲",
    postCount: 12,
    followerCount: 88,
    followingCount: 95,
  },
  {
    id: "u-haeun",
    username: "haeun",
    nickname: "하은",
    profileImageUrl: "/images/photos/shop.jpg",
    bio: "작은 책방과 소품샵 기록.",
    region: "망원동",
    postCount: 30,
    followerCount: 512,
    followingCount: 301,
  },
  {
    id: "u-yujin",
    username: "yujin",
    nickname: "유진",
    profileImageUrl: "/images/photos/cafe.jpg",
    bio: "사진 찍기 좋은 골목을 모아요.",
    region: "종로",
    postCount: 57,
    followerCount: 2310,
    followingCount: 120,
  },
  {
    id: "u-walker",
    username: "seongsu_walker",
    nickname: "성수 산책자",
    profileImageUrl: null,
    bio: "성수동 구석구석.",
    region: "성수동",
    postCount: 8,
    followerCount: 64,
    followingCount: 70,
  },
];

/** [follower, following] */
export const follows: [string, string][] = [
  ["u-seoyeon", "u-jimin"],
  ["u-seoyeon", "u-minsu"],
  ["u-jimin", "u-seoyeon"],
  ["u-haeun", "u-seoyeon"],
  ["u-yujin", "u-seoyeon"],
  ["u-minsu", "u-seoyeon"],
];
