import Link from "next/link";
import { ARTICLE_TAGS } from "../lib/article-feed";
import { Icon, topicIcon } from "./Icon";

export function TopicNavigation() {
  return (
    <section className="topic-navigation" aria-labelledby="topics-title">
      <h2 id="topics-title">주제 둘러보기</h2>
      <nav className="topic-grid" aria-label="주제 둘러보기">
        {ARTICLE_TAGS.map((topic) => (
          <Link
            key={topic}
            href={`/community?topic=${encodeURIComponent(topic)}`}
          >
            <Icon name={topicIcon(topic)} />
            {topic}
          </Link>
        ))}
      </nav>
    </section>
  );
}
