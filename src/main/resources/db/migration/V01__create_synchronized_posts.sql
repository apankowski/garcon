CREATE TABLE synchronized_posts (
  id UUID PRIMARY KEY,
  version INTEGER NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  page_key TEXT NOT NULL,
  page_name TEXT NOT NULL,
  post_external_id TEXT NOT NULL,
  post_url TEXT NOT NULL,
  post_published_at TIMESTAMP WITH TIME ZONE NOT NULL,
  post_content TEXT NOT NULL,
  classification TEXT NOT NULL,
  repost_status TEXT NOT NULL,
  repost_attempts INTEGER,
  repost_last_attempt_at TIMESTAMP WITH TIME ZONE,
  repost_next_attempt_at TIMESTAMP WITH TIME ZONE,
  repost_reposted_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX synchronized_posts_post_external_id ON synchronized_posts(post_external_id);
