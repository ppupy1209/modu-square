import type { Metadata } from "next";
import { CommunityArticlesPage } from "./CommunityArticlesPage";

export const metadata: Metadata = {
  title: "커뮤니티 — Modu Square",
  description: "일상, 취향, 질문, 동네, 여행, 정보로 나누어 Modu Square의 이야기를 둘러보세요.",
};

export default function CommunityPage() {
  return <CommunityArticlesPage />;
}
