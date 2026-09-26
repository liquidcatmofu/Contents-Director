# Localization

Contents Director uses message bundles for user-facing UI text.

## Bundled languages

| Locale | File | Status |
| --- | --- | --- |
| English / canonical fallback | `messages.xml` | Complete |
| Japanese (`ja`) | `messages_ja.xml` | Complete |
| Simplified Chinese (`zh_CN`) | `messages_zh_CN.xml` | Complete |
| Portuguese (`pt`) | `messages_pt.xml` | Partial; missing keys fall back to English |

The active locale defaults to the JVM locale returned by `Locale.getDefault()`. Contents Director does not currently expose a separate modpack setting for selecting a UI language.

Once a locale is selected, missing keys fall back to the canonical English bundle rather than switching to another system-locale bundle.

## Bundle location and format

Built-in bundles are stored under:

```text
src/main/resources/com/juanmuscaria/modpackdirector/i18n/
```

They use Java XML properties format:

```xml
<entry key="modpack_director.selection_page.next_button_label">Next</entry>
```

The English `messages.xml` file is the canonical key set. Translations intended to be complete should contain every canonical key.

## Placeholders

Parameterized messages use indexed placeholders such as `{0}`:

```xml
<entry key="modpack_director.progress.install">Installing {0}</entry>
```

Translations may move a placeholder to a grammatically appropriate position but must preserve the same placeholder identifiers. Tests verify placeholder compatibility for the complete Japanese and Simplified Chinese bundles.

## Modpack-provided overrides

Contents Director loads an optional external message bundle from the platform configuration directory with basename `messages`.

For a normal Minecraft layout:

```text
config/mod-director/messages.xml
config/mod-director/messages_ja.xml
config/mod-director/messages_zh_CN.xml
```

External messages take precedence over built-in messages. The bundled classpath messages remain the parent source, so a modpack can override only the keys it needs and allow all other keys to fall back to the built-in bundle.

External filesystem bundles are resolved from an absolute configuration-directory path on Windows, Linux, and macOS.

Use the same Java XML properties format and keep placeholders compatible with the canonical entry.

## macOS external UI helper

On macOS, interactive Swing UI may run in a separate helper JVM. Localized strings are resolved by the main Contents Director process first and passed to the helper request. This preserves JVM-locale selection and modpack-provided message overrides without initializing Swing in the Minecraft JVM.

## Adding or updating a translation

1. Use `messages.xml` as the canonical source.
2. Add or update the locale-specific file using Java resource-bundle naming conventions.
3. Preserve all canonical keys for translations intended to be complete.
4. Preserve placeholder identifiers such as `{0}`.
5. Run:

```bash
./gradlew clean build --no-daemon
```

The localization tests verify the Japanese and Simplified Chinese key sets/placeholders, normal locale resolution, partial Portuguese fallback, external locale overrides, and strings passed to the external UI helper.

## Missing-key behavior

If a locale-specific bundle does not define a key, resolution falls back to the canonical English bundle.

If a key cannot be resolved at all, Contents Director returns the key itself (and includes parameters when present) rather than failing installation solely because a translation is missing.
