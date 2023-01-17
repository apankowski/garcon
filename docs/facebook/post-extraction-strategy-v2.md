# Post extraction strategy v2

This document covers some research around post extraction strategy for pages served by Facebook since mid 2022.

Downsides of this strategy:

1. it's a bit complicated,
2. it allows extracting a single, most recent post.

## DOM loading

For some reason regular `curl` doesn't work, i.e. the following returns a login page:

```
curl -X GET -v -L 'https://www.facebook.com/people/P%C3%B3%C5%82-%C5%BBartem-P%C3%B3%C5%82-Serio/100028295814975/'
```

The extended one, as sent by Firefox, works:

```
curl 'https://www.facebook.com/people/P%C3%B3%C5%82-%C5%BBartem-P%C3%B3%C5%82-Serio/100028295814975/' -H 'User-Agent: Mozilla/5.0 (X11; Linux x86_64; rv:108.0) Gecko/20100101 Firefox/108.0' -H 'Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8' -H 'Accept-Language: en-US,en;q=0.5' --compressed -H 'DNT: 1' -H 'Connection: keep-alive' -H 'Upgrade-Insecure-Requests: 1' -H 'Sec-Fetch-Dest: document' -H 'Sec-Fetch-Mode: navigate' -H 'Sec-Fetch-Site: none' -H 'Sec-Fetch-User: ?1' -H 'Pragma: no-cache' -H 'Cache-Control: no-cache' -H 'TE: trailers'
```

A shorter, but still functional version, aligned with existing code fetching the DOM:

```
curl 'https://www.facebook.com/people/P%C3%B3%C5%82-%C5%BBartem-P%C3%B3%C5%82-Serio/100028295814975/' -H 'User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:80.0) Gecko/20100101 Firefox/80.0' -H 'Accept: text/html,application/xhtml+xml' -H 'Accept-Language: pl,en;q=0.5' -H 'Pragma: no-cache' -H 'Cache-Control: no-cache' -H 'DNT: 1' -H 'Sec-Fetch-Dest: document' -H 'Sec-Fetch-Mode: navigate' -H 'Sec-Fetch-Site: none' -H 'Sec-Fetch-User: ?1'
```

Compared to previously working request, the difference is in the addition of:

1. _do not track_ header
2. [fetch metadata request headers](https://developer.mozilla.org/en-US/docs/Glossary/Fetch_metadata_request_header)

## Payload extraction

Downloaded page DOM contains information needed to render the top-most post (called the "payload" here). The page is not server-side rendered. Rather, information needed to render the post on the client side is embedded in the DOM, in one of the inline scripts.

There is a lot of scripts, both inline and loaded. Identifying the one containing the payload is challenging. Moreover, the payload is buried deep in calls setting up invocation of post rendering logic:

```javascript
requireLazy(["Bootloader"],function(b){
    b.loadResources(["xKlBoGV"],{
        onAll:function(){
            requireLazy(["JSScheduler","ServerJS","ScheduledApplyEach"],function(JSScheduler,ServerJS,ScheduledApplyEach){
                JSScheduler.runWithPriority(3,function(){
                    (new ServerJS()).handleWithCustomApplyEach(ScheduledApplyEach,{"define":[/*...*/]});
                });
            });
        }
    })
});
```

Above, the payload is the `{"define":[/*...*/]}` JSON object (cut short for brevity), the snippet is formatted for readability, and it's just one line of a larger inline script.

Instead of trying to identify the script of interest, all plain JSON object literals from all the scripts in the DOM can be extracted and a query extracting post data applied to each one of them.

## Payload analysis

We'd like to extract the following pieces of information from the payload:

* page
  * ID
  * title
* posts
  * ID
  * published at
  * URL
  * content

These were found in the payload at:

* page
  * ID:
    * ~~`require[69][3][1].__bbox.result.data.user.id`~~
    * `post.comet_sections.content.story.comet_sections.context_layout.story.comet_sections.actor_photo.story.actors[0].id`
    * `post.comet_sections.content.story.comet_sections.context_layout.story.comet_sections.title.story.actors[0].id`
    * `post.comet_sections.content.story.actors[0].id`
    * ~~`post.comet_sections.context_layout.story.comet_sections.actor_photo.story.actors[0].id`~~
    * ~~`post.comet_sections.context_layout.story.comet_sections.title.story.actors[0].id`~~
    * ~~`post.comet_sections.feedback.story.feedback_context.feedback_target_with_context.ufi_renderer.feedback.top_level_comment_list_renderer.feedback.owning_profile.id`~~
    * ~~`require[69][3][1].__bbox.variables.userID`~~
  * name:
    * `post.comet_sections.content.story.comet_sections.context_layout.story.comet_sections.actor_photo.story.actors[0].name`
    * `post.comet_sections.content.story.comet_sections.context_layout.story.comet_sections.title.story.actors[0].name`
    * `post.comet_sections.content.story.actors[0].name`
    * ~~`post.comet_sections.context_layout.story.comet_sections.actor_photo.story.actors[0].name`~~
    * ~~`post.comet_sections.context_layout.story.comet_sections.title.story.actors[0].name`~~
* `post` is each object having `"__typename": "Story"` property (e.g. `require[69][3][1].__bbox.result.data.user.timeline_list_feed_units.edges[0].node`)
  * ID:
    * `post.post_id`
  * published at:
    * `post.comet_sections.content.story.comet_sections.context_layout.story.comet_sections.metadata[0].story.creation_time`
    * ~~`post.comet_sections.context_layout.story.comet_sections.metadata[0].story.creation_time`~~
  * URL:
    * `post.comet_sections.content.story.comet_sections.context_layout.story.comet_sections.metadata[0].story.url`
    * `post.comet_sections.content.story.wwwURL`
    * ~~`post.comet_sections.context_layout.story.comet_sections.metadata[0].story.url`~~
    * ~~`post.comet_sections.feedback.story.feedback_context.feedback_target_with_context.ufi_renderer.feedback.url`~~
    * ~~`post.comet_sections.feedback.story.feedback_context.feedback_target_with_context.ufi_renderer.feedback.top_level_comment_list_renderer.feedback.composer_renderer.feedback.url`~~
    * ~~`post.comet_sections.feedback.story.url`~~
  * content:
    * `post.comet_sections.content.story.comet_sections.message.story.message.text`
    * `post.comet_sections.content.story.comet_sections.message_container.story.message.text`
    * `post.comet_sections.content.story.message.text`

We prefer to go through `content` object, therefore similar paths going initially through `context_layout` are crossed out.

## Post extraction strategy

Comprehensive `jq` query returning all information for a captured payload:

```jq
{
    "page": [.. | objects | select(.__typename == "User" and .__isEntity == "User")] | {
        "id": map(.id) | unique | first,
        "title": map(.name) | unique | first
    },
    "posts": [.. | objects | select(.__typename == "Story")] |
        (.. | objects | select(has("content")) | .content) as $content |
            ($content | .. | objects | select(has("creation_time"))) as $metadata |
                map({
                    "id": .post_id,
                    "published_at": $metadata.creation_time,
                    "url": $metadata.url,
                    "content": $content | .. | objects | select(.__typename == "TextWithEntities") | .text,
                })
}
```

Testing `jq` queries is very convenient with [jq play](https://jqplay.org/).
