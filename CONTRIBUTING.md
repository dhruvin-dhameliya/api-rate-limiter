# Contributing

Thanks for taking the time to contribute. `api-rate-limiter` is a small
Spring Boot library and every improvement is welcome, whether it's a bug
report, a doc typo, a test, or a new feature.

## Ways to help

- **Bug reports & feature requests.** Open an issue using the templates
  in `.github/ISSUE_TEMPLATE/`.
- **Docs.** The `README.md` is the single source of truth. If something
  is unclear or wrong, a PR that fixes it is worth more than an issue.
- **Code.** The "Known limits" section of the README lists good first
  PRs. For anything else, open an issue to discuss first.

## Development setup

```bash
git clone https://github.com/dhruvin-dhameliya/api-rate-limiter.git
cd api-rate-limiter
./mvnw verify
```

Requirements:

- JDK 17+
- Maven 3.9+ (the wrapper `./mvnw` is included)
- (Optional) Docker, for the Redis integration tests: `docker run -p 6379:6379 redis:7`

## Branch & commit conventions

- Branch names: `type/short-description`, e.g. `feat/token-bucket`,
  `fix/wait-time-window`, `docs/config-yaml`.
- Commit messages: follow [Conventional Commits](https://www.conventionalcommits.org/)
  where you can (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`,
  `chore:`). It keeps the history readable.

## Pull-request checklist

- [ ] The change is scoped. One logical thing per PR.
- [ ] Tests cover the new behaviour, and existing tests still pass
      (`./mvnw verify`).
- [ ] `README.md` reflects the change. It's the only user-facing doc.

## Coding guidelines

- Java 17 language level, no preview features.
- Lombok is used for boilerplate (`@Data`, `@RequiredArgsConstructor`,
  `@Slf4j`). Follow the existing style.
- Prefer immutable configuration and dependency injection over static state.
- Log user-visible events at `INFO`, internal decisions at `DEBUG`, and
  only use `WARN` / `ERROR` for actionable conditions.
- Never log raw API keys, passwords, or session tokens.

## Reporting a security issue

Please **do not** open a public issue for security vulnerabilities. See
[`SECURITY.md`](./SECURITY.md) for the private disclosure process.

## Code of Conduct

By participating you agree to abide by the
[Code of Conduct](./CODE_OF_CONDUCT.md).
