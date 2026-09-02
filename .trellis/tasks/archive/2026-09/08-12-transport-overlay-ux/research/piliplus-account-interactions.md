# PiliPlus Account Interaction Follow-up

## Reference

Reference worktree: `temp/PiliPlus-latest`, commit `e5dfc6394`.

## Comments And Replies

Relevant files:

- `lib/pages/common/reply_controller.dart`
- `lib/pages/video/reply/controller.dart`
- `lib/pages/video/reply/view.dart`
- `lib/pages/video/reply_reply/`
- `lib/http/reply.dart`

Observed reusable behavior:

- `ReplyController.onReply()` evaluates server-provided input-disable state and
  hint text before opening a reply composer.
- It keeps reply drafts keyed by target, supports top-level and nested replies,
  and locally inserts a successfully published reply into the appropriate list.
- It performs optional comment anti-fraud follow-up after publication.
- `ReplyHttp` includes account-bound actions such as comment like/dislike,
  report, and top; requests use `Accounts.main.csrf` where required.

## Direct Messages

Relevant files:

- `lib/pages/whisper/`
- `lib/pages/whisper_detail/controller.dart`
- `lib/pages/whisper_detail/view.dart`
- `lib/pages/whisper_block/`, `whisper_settings/`, `whisper_link_setting/`
- `lib/grpc/im.dart`

Observed reusable behavior:

- PiliPlus has a session list, session detail, send flow, block list, IM
  settings, and link settings.
- `WhisperDetailController.sendMsg()` prevents concurrent sends, requires login,
  sends through `ImGrpc.sendMsg`, clears draft/refreshes only after success, and
  surfaces errors without claiming success.

## ViriViri TODO

Comments, replies, and direct messages are deferred account capabilities. Later
work should introduce controlled product modules, not theme-owned network code:

```text
CommentThread / CommentComposer / ReplyComposer
DirectMessageSessionList / DirectMessageThread / DirectMessageComposer
```

Requirements before enabling write actions:

1. Login, cookie/session, CSRF and service contract support are implemented and
   verified for the target endpoint/protocol.
2. Each composer exposes disabled, login-required, draft, sending, success, and
   failure states.
3. Sending is idempotency/concurrency guarded; success updates the local model,
   failure preserves user text where appropriate.
4. Theme layers only render controlled state/actions. They do not issue Bilibili
   writes or fabricate completion.
5. The application-owned IME may be reused as a composer input source, but
   composer sessions must not share `SearchSession` or search history.

Until those prerequisites exist, comments may be read-only and all write/DM
entry points must explicitly show unavailable or login-required state.
