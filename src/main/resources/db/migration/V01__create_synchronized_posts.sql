CREATE TABLE synchronized_posts (
  id UUID PRIMARY KEY,
  version INTEGER NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  page_id TEXT NOT NULL,
  page_name TEXT,
  post_external_id TEXT NOT NULL,
  post_link TEXT NOT NULL,
  post_published_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  post_content TEXT NOT NULL,
  classification_status TEXT NOT NULL,
  repost_status TEXT NOT NULL,
  repost_error_count INTEGER,
  repost_last_attempt_at TIMESTAMP WITHOUT TIME ZONE,
  repost_reposted_at TIMESTAMP WITHOUT TIME ZONE
);
