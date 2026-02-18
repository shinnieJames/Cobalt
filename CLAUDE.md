# Javadoc Documentation Style Guide

This guide codifies the javadoc conventions used by the JDK (OpenJDK) standard library.
Follow these rules when writing javadoc for classes, interfaces, enums, records, annotations,
constructors, methods, and fields.

---

## 1. General Structure of a Javadoc Comment

```java
/**
 * First sentence is the summary — concise, complete, and stands alone.
 *
 * <p>Additional paragraphs start with {@code <p>} on a blank-asterisk line.
 * The main description precedes all block tags and cannot continue after them.
 *
 * @apiNote  Usage guidance for callers
 * @implSpec Contract that subclasses/implementations must satisfy
 * @implNote Implementation detail (not part of the API contract)
 * @param <T> description of type parameter
 * @param name description of parameter
 * @return description of return value
 * @throws ExceptionType description of when it is thrown
 * @since 21
 * @see OtherClass#method(Type)
 */
```

### Rules

- The comment must appear **immediately before** the declaration it documents.
- Only **one** doc comment per declaration.
- The **first sentence** (up to the first period followed by whitespace, or an explicit
  `{@summary}` tag) is used as the summary in index pages — make it count.
- Separate paragraphs with `<p>` at the start of a new line (after a blank `*` line).
- Block tags must appear **after** the main description in a fixed order (see Section 8).
- Always document both public and non public members.
- Always use multiline javadocs, not singleline.

---

## 2. Class / Interface / Enum / Record / Annotation Documentation

### 2.1 Opening Summary

Write a single-sentence summary that states **what the type is or represents**.

| Type Kind | JDK Pattern | Example |
|-----------|------------|---------|
| Class | "A/An [noun] that [verb]..." | *"A container object which may or may not contain a non-{@code null} value."* |
| Interface | "A/An [noun] that [verb]..." | *"An ordered collection, where the user has precise control over where in the list each element is inserted."* |
| Enum | "A/An [noun], such as '[example]'." | *"A day-of-week, such as 'Tuesday'."* |
| Annotation | "A program element annotated {@code @Name} is one that..." | *"A program element annotated {@code @Deprecated} is one that programmers are discouraged from using."* |
| Utility class | "[Noun] of static utility methods for..." | *"Static utility methods for operating on objects, or checking certain conditions before operation."* |

### 2.2 Body

After the summary sentence, elaborate on:

1. **Purpose and semantics** — What does this type do? What contract does it enforce?
2. **Behavioral properties** — Is it immutable? Thread-safe? Value-based? Serializable?
3. **Usage patterns and constraints** — When to use it, when not to, null handling policy.
4. **Relationship to other types** — Mention superclasses, companion types, or alternatives.
5. **Code examples** — For complex types, show typical usage in `<pre>{@code ...}</pre>` blocks.

### 2.3 Standard Block Tags for Types

```java
/**
 * ...main description...
 *
 * @apiNote  Advice for API consumers (e.g. intended use cases, anti-patterns)
 * @implSpec Behavioral contract for implementors/subclasses
 * @implNote Non-binding implementation detail
 * @param <T> description of each type parameter
 * @author Name (only if project convention requires it)
 * @since 1.8
 * @see RelatedType
 * @jls 15.18.1 String Concatenation Operator +
 */
```

### 2.4 Enum Constants

Each constant gets its own doc comment. Use the pattern:

```java
/**
 * The singleton instance for [description].
 * This has the numeric value of {@code N}.
 */
CONSTANT_NAME,
```

JDK examples:
- `DayOfWeek.MONDAY`: *"The singleton instance for the day-of-week of Monday. This has the numeric value of {@code 1}."*
- `TimeUnit.NANOSECONDS`: *"Time unit representing one thousandth of a microsecond."*
- `Month.JANUARY`: *"The singleton instance for the month of January with 31 days. This has the numeric value of {@code 1}."*

---

## 3. Method Documentation

### 3.1 Summary Sentence

Start with a **third-person verb phrase** (no subject). Describe what the method does,
not how it does it.

| Pattern | Example |
|---------|---------|
| "Returns..." | *"Returns the {@code char} value at the specified index."* |
| "Compares..." | *"Compares two strings lexicographically."* |
| "If a value is present, ..." | *"If a value is present, returns the value, otherwise throws {@code NoSuchElementException}."* |
| "Performs..." | *"Performs the given action for each element of the {@code Iterable}."* |

### 3.2 Method Body

- Describe preconditions, postconditions, and side effects.
- State what happens with edge cases (null input, empty collections, overflow).
- For overloaded methods, describe the relationship to other overloads.
- For default/optional operations, state that `UnsupportedOperationException` may be thrown.

### 3.3 Code Examples in Methods

Use `<pre>{@code ...}</pre>` for multi-line examples:

```java
/**
 * @apiNote
 * This method supports post-processing on {@code Optional} values, without
 * the need to explicitly check for a return status. For example:
 *
 * <pre>{@code
 *     Optional<Path> p =
 *         uris.stream().filter(uri -> !isProcessedYet(uri))
 *                       .findFirst()
 *                       .map(Paths::get);
 * }</pre>
 */
```

### 3.4 Block Tag Order for Methods

```java
/**
 * ...main description...
 *
 * @apiNote  ...
 * @implSpec ...
 * @implNote ...
 * @param <T> type parameter description
 * @param name parameter description
 * @return description
 * @throws ExceptionType when/why it is thrown
 * @since 21
 * @see #otherMethod(Type)
 */
```

---

## 4. Constructor Documentation

- Starts with a verb: *"Constructs..."*, *"Creates..."*, *"Initializes..."*, *"Allocates..."*.
- Document every parameter with `@param`.
- Document every checked exception with `@throws`.
- State if the constructor makes a defensive copy of mutable arguments.

```java
/**
 * Constructs a new {@code String} by decoding the specified subarray of
 * bytes using the specified charset.
 *
 * <p>The contents of the string are unspecified if the byte array
 * is modified during string construction.
 *
 * @param bytes       the bytes to be decoded into characters
 * @param offset      the index of the first byte to decode
 * @param length      the number of bytes to decode
 * @param charsetName the name of a supported {@linkplain java.nio.charset.Charset charset}
 * @throws UnsupportedEncodingException if the named charset is not supported
 * @throws IndexOutOfBoundsException    if {@code offset} is negative,
 *         {@code length} is negative, or {@code offset} is greater than
 *         {@code bytes.length - length}
 * @since 1.1
 */
```

---

## 5. Field Documentation

- For constants, state the value using `{@value}` where appropriate.
- For fields relevant to serialization, use `@serial`.

```java
/**
 * A Comparator that orders {@code String} objects as by
 * {@link #compareToIgnoreCase(String)}.
 *
 * @since 1.2
 */
public static final Comparator<String> CASE_INSENSITIVE_ORDER = ...;
```

---

## 6. HTML Formatting Conventions

### 6.1 Paragraphs

Separate paragraphs with `<p>` at the start of a continuation line.
Do **not** use `</p>` closing tags (JDK convention).

```java
/**
 * First paragraph describing the class.
 *
 * <p>Second paragraph with additional details.
 *
 * <p>Third paragraph about edge cases.
 */
```

### 6.2 Lists

Use `<ul>` / `<li>` for unordered lists. Do **not** close `<li>` tags (JDK convention).

```java
/**
 * The other object is considered equal if:
 * <ul>
 * <li>it is also an {@code Optional} and;
 * <li>both instances have no value present or;
 * <li>the present values are "equal to" each other via {@code equals()}.
 * </ul>
 */
```

### 6.3 Code Blocks

For inline code, use `{@code expression}`.
For multi-line code blocks, use `<pre>{@code ... }</pre>`:

```java
/**
 * For example:
 * <blockquote><pre>
 *     String str = "abc";
 * </pre></blockquote>
 */
```

Or more commonly:

```java
/**
 * <pre>{@code
 *     List<String> list = people.stream()
 *         .map(Person::getName)
 *         .collect(Collectors.toList());
 * }</pre>
 */
```

### 6.4 Emphasis

- `{@code text}` — code font, no HTML interpretation (preferred over `<code>`)
- `<em>text</em>` or `<i>text</i>` — italic emphasis
- `<b>text</b>` or `<strong>text</strong>` — bold emphasis

---

## 7. Complete Javadoc Tag Reference

### 7.1 Block Tags

Block tags must start at the beginning of a line (after the leading `*` and whitespace).

#### `@param name description`
Documents a method/constructor parameter or type parameter.
- For type parameters, use angle brackets: `@param <T> the element type`
- List all parameters in declaration order.
- Description starts lowercase; does not end with a period if it's a phrase (JDK style varies).

```java
/**
 * @param <T> the type of value
 * @param value the value to describe, which must be non-{@code null}
 */
```

#### `@return description`
Documents the return value of a method.
- Starts with a lowercase article or noun phrase.
- Can span multiple lines.

```java
/**
 * @return the non-{@code null} value described by this {@code Optional}
 */
```

#### `@throws exception-class description`
Documents an exception that may be thrown.
- One tag per exception type; document each separately.
- Use "if" to describe the condition: *"if the index is out of range"*.
- Document both checked exceptions (from `throws` clause) and unchecked exceptions
  that callers may reasonably want to catch.
- Synonym: `@exception` (deprecated form, avoid using).

```java
/**
 * @throws NullPointerException if value is {@code null}
 * @throws IndexOutOfBoundsException if {@code offset} is negative,
 *         {@code length} is negative, or {@code offset} is greater than
 *         {@code bytes.length - length}
 */
```

#### `@since version`
Identifies the version/release when the API was introduced.
- Required on every public and protected class, method, constructor, and field.
- Format matches the project's versioning scheme (e.g., `1.8`, `9`, `21`).

```java
/** @since 1.8 */
```

#### `@see reference`
Adds a "See Also" cross-reference. Three forms:

```java
/**
 * @see String                                    // class reference
 * @see java.lang.Object#equals(java.lang.Object) // method reference
 * @see #compareTo(String)                         // same-class method reference
 * @see "The Java Language Specification"           // text entry (in quotes)
 * @see <a href="https://example.com">Label</a>    // URL link
 */
```

#### `@author name`
Identifies the author. Multiple `@author` tags are allowed.
Only included in output with the `-author` flag. Some projects omit this in favor of VCS history.

```java
/**
 * @author Lee Boynton
 * @author Arthur van Hoff
 */
```

#### `@version version-text`
Records the current version of the software (not the version it was introduced — use `@since` for that).
Only included in output with the `-version` flag.

#### `@deprecated explanation`
Marks the API element as deprecated. Always pair with the `@Deprecated` annotation.
Explain why it is deprecated and what to use instead.

```java
/**
 * @deprecated This method does not properly convert bytes to characters.
 *     Use {@link String#String(byte[], String)} instead.
 */
@Deprecated
public String(byte[] ascii, int hibyte) { ... }
```

#### `@serial field-description | include | exclude`
Documents serializable fields. Use `@serial include` or `@serial exclude`
to control whether a class appears in the serialized-form page.

#### `@serialField name type description`
Documents a field in `serialPersistentFields`.

#### `@serialData description`
Documents data written by `writeObject` or `writeExternal`.

#### `@hidden`
Hides the element from generated documentation. Useful for internal API
that must be public for technical reasons.

```java
/** @hidden */
public void internalMethod() { ... }
```

#### `@provides service-type description`
Documents a service provider in a `module-info.java`.

#### `@uses service-type description`
Documents a service dependency in a `module-info.java`.

#### `@spec URL title` (since JDK 20)
Links to an external specification.

```java
/**
 * @spec https://www.unicode.org/versions/latest/core-spec/chapter-5/#G21790
 *       Unicode Caseless Matching
 */
```

#### `@jls section-number title`
References a section of the Java Language Specification.

```java
/**
 * @jls 15.18.1 String Concatenation Operator +
 */
```

---

### 7.2 Inline Tags

Inline tags can appear anywhere in the description or in block tag text.

#### `{@code text}`
Renders `text` in code font without interpreting HTML or nested tags.
Use for type names, method names, keywords, literals, and short expressions.

```java
/** Returns {@code true} if the value is present. */

/** If a value is present, returns the value, otherwise returns {@code other}. */
```

**Prefer `{@code ...}` over `<code>...</code>`** — it also escapes HTML entities
so `{@code A<B>C}` renders correctly as `A<B>C`.

#### `{@literal text}`
Same as `{@code}` but without code font. Use when you need to include
literal angle brackets or at-signs in normal prose.

#### `{@link package.Class#member label}`
Inserts a hyperlink in code font to the referenced program element.

```java
/** Use the {@link #getComponentAt(int, int) getComponentAt} method. */

/** Returns a sequential {@link Stream} containing only that value. */

/** @see {@link java.util.Optional} for an alternative */
```

#### `{@linkplain package.Class#member label}`
Same as `{@link}` but renders the label in plain text (not code font).
Use when the label is a natural-language phrase.

```java
/** programmers should treat instances that are
 * {@linkplain #equals(Object) equal} as interchangeable */
```

#### `{@value field-reference}`
Displays the compile-time value of a constant field.

```java
/** The value of this constant is {@value}. */
public static final int MAX = 100;

/** The maximum size is {@value #MAX}. */
```

Since JDK 20, an optional format string is supported: `{@value "%05d" #MAX}`.

#### `{@inheritDoc}`
Copies the corresponding documentation from the overridden or implemented method.
Valid in: main description, `@return`, `@param`, `@throws` text.

```java
/**
 * {@inheritDoc}
 *
 * <p>This implementation additionally verifies the input.
 */
@Override
public void process(Data data) { ... }
```

#### `{@docRoot}`
Resolves to the relative path to the documentation root. Use for links to shared resources.

```java
/** See the <a href="{@docRoot}/copyright.html">Copyright</a>. */
```

#### `{@index term description}` (since JDK 9)
Adds a term to the documentation search index.

```java
/** This method uses the {@index SHA-256 hash algorithm}. */
```

#### `{@summary text}` (since JDK 10)
Explicitly marks the summary/first sentence. Overrides the default first-sentence detection.

```java
/** {@summary Returns the day-of-week for this date.} Full description follows... */
```

#### `{@return description}` (since JDK 16)
Inline form of `@return` that also serves as the first sentence of the method description.

```java
/**
 * {@return {@code true} if the arguments are equal to each other
 * and {@code false} otherwise}
 */
public static boolean equals(Object a, Object b) { ... }
```

#### `{@systemProperty property-name}` (since JDK 12)
References a system property, adding it to the system properties page.

```java
/** Uses the {@systemProperty java.home} system property. */
```

#### `{@snippet attributes : body}` (since JDK 18)
Embeds a code snippet with optional highlighting, replacement, and linking.

**Inline snippet:**
```java
/**
 * Example usage:
 * {@snippet lang="java" :
 *     var list = List.of("a", "b", "c");  // @highlight substring="List.of"
 * }
 */
```

**External snippet (from a file):**
```java
/**
 * {@snippet file="ExampleTest.java" region="setup"}
 */
```

**Markup tags** (inside comments within snippet body):
- `@highlight substring="text" type=bold|italic|highlighted`
- `@replace substring="text" replacement="new"`
- `@link substring="text" target="Class#method"`
- `@start region=name` / `@end region=name`

---

### 7.3 The Advisory Tags: `@apiNote`, `@implSpec`, `@implNote`

These three tags partition documentation by audience and contractual weight.
They are the backbone of JDK interface/default-method documentation.

#### `@apiNote`
**Audience:** API consumers (callers).
**Purpose:** Non-normative guidance — tips, best practices, common pitfalls, intended use cases.
**Not part of the API contract.**

```java
/**
 * @apiNote
 * {@code Optional} is primarily intended for use as a method return type where
 * there is a clear need to represent "no result," and where using {@code null}
 * is likely to cause errors.
 */
```

```java
/**
 * @apiNote
 * This method exists to be used as a {@code Predicate},
 * {@code filter(Objects::isNull)}
 */
```

#### `@implSpec`
**Audience:** Implementors / subclass authors.
**Purpose:** Normative specification of the default behavior. Defines the contract
that implementations must satisfy (or that the default implementation provides).

```java
/**
 * @implSpec
 * The default implementation creates a sequential {@code Stream} from the
 * collection's {@code Spliterator}.
 */
```

```java
/**
 * @implSpec
 * If a value is present the result must include its string representation
 * in the result. Empty and present {@code Optional}s must be unambiguously
 * differentiable.
 */
```

#### `@implNote`
**Audience:** Implementors and maintainers.
**Purpose:** Non-normative implementation detail — informational, not contractual.
Performance hints, internal design rationale, garbage collection behavior.

```java
/**
 * @implNote
 * The returned {@code Collector} is not concurrent. For parallel stream
 * pipelines, the {@code combiner} function operates by merging the keys
 * from one map into another, which can be an expensive operation.
 */
```

```java
/**
 * @implNote
 * If the given Map is an unmodifiable Map, calling copyOf will generally
 * not create a copy.
 */
```

---

## 8. Block Tag Ordering Convention

The JDK follows this canonical order:

```
@apiNote
@implSpec
@implNote
@param      (in declaration order; type params first)
@return
@throws     (in order of likelihood or alphabetically)
@since
@serial / @serialField / @serialData
@deprecated
@author
@version
@see
@jls
@spec
```

---

## 9. Style Rules Observed in the JDK

### 9.1 Language and Tone

- Use **third person declarative** ("Returns the value") not imperative ("Return the value")
  and not first person ("I return the value").
- Write in **present tense**.
- Use **American English** spelling.
- Be **precise**: say `{@code null}` not "null", say `{@code true}` not "true".

### 9.2 Summary Sentence

- Must be a **complete, standalone sentence** — it appears in isolation in summary tables.
- For methods: start with a verb phrase in third person (*"Returns..."*, *"Compares..."*,
  *"Sets..."*, *"Throws..."*).
- For classes: state what the type **is** (*"A container object which..."*).
- For enums: identify what it represents (*"A day-of-week, such as 'Tuesday'."*).

### 9.3 `{@code}` Usage

Always wrap these in `{@code ...}`:
- Java keywords: `{@code null}`, `{@code true}`, `{@code false}`
- Type names when used as values: `{@code String}`, `{@code Optional}`
- Method names in prose: `{@code isPresent()}`
- Literal values: `{@code 0}`, `{@code -1}`
- Short inline code expressions: `{@code e1.compareTo(e2) == 0}`

### 9.4 Cross-References

- Use `{@link}` for the **first** reference to another API element in a doc comment.
- Use `{@code}` for subsequent references to the same element within the same comment.
- Use `{@linkplain}` when the label is a natural-language phrase rather than a code identifier.
- Use `@see` for "See Also" references at the end that don't fit inline in the prose.
- Use `{@link #method(Type) displayName}` to provide a shorter display label.

```java
/** Returns a sequential {@link Stream} containing only that value,
 *  otherwise returns an empty {@code Stream}. */
```

### 9.5 `@throws` Documentation

- Document **every** checked exception from the `throws` clause.
- Document significant unchecked exceptions (especially `NullPointerException`,
  `IllegalArgumentException`, `IndexOutOfBoundsException`).
- Start with "if": *"if the index is out of range"*.
- Mark optional exceptions: *"(optional)"* for interface methods where implementations
  may or may not enforce the check.
- Align multi-line `@throws` descriptions with consistent indentation.

```java
/**
 * @throws ClassCastException if the key is of an inappropriate type
 *         for this map (optional)
 * @throws NullPointerException if the specified key is null and this map
 *         does not permit null keys (optional)
 */
```

### 9.6 `@param` Documentation

- List in **declaration order**, type parameters first.
- Start with a lowercase article or noun phrase.
- Describe constraints: *"which must be non-{@code null}"*, *"May be {@code null}."*
- For multi-line descriptions, indent continuation lines to align with the first
  word of the description.

```java
/**
 * @param <T> the type of the value
 * @param value the possibly-{@code null} value to describe
 * @param emptyAction the empty-based action to be performed, if no value is
 *        present
 */
```

### 9.7 `@return` Documentation

- Start with a lowercase article or "the".
- State both the positive and negative outcomes when applicable.

```java
/**
 * @return {@code true} if a value is present, otherwise {@code false}
 */
```

Or use the inline form (JDK 16+):

```java
/**
 * {@return {@code true} if the arguments are equal to each other
 * and {@code false} otherwise}
 */
```

### 9.8 Thread Safety and Immutability

State thread-safety and immutability in the class-level javadoc:

```java
/**
 * ...
 * <p>This class is immutable and thread-safe.
 *
 * ...
 */
```

### 9.9 Null Handling Policy

State the null policy at the class level when it applies uniformly:

```java
/**
 * <p>Unless otherwise noted, passing a {@code null} argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be
 * thrown.
 */
```

### 9.10 Value-Based Classes

```java
/**
 * <p>This is a <a href="{@docRoot}/.../ValueBased.html">value-based</a>
 * class; programmers should treat instances that are
 * {@linkplain #equals(Object) equal} as interchangeable and should not
 * use instances for synchronization, or unpredictable behavior may
 * occur.
 */
```

---

## 10. Complete Examples

### 10.1 Enum

```java
/**
 * A day-of-week, such as 'Tuesday'.
 *
 * <p>{@code DayOfWeek} is an enum representing the 7 days of the week -
 * Monday, Tuesday, Wednesday, Thursday, Friday, Saturday and Sunday.
 *
 * <p>In addition to the textual enum name, each day-of-week has an
 * {@code int} value. The {@code int} value follows the ISO-8601 standard,
 * from 1 (Monday) to 7 (Sunday). It is recommended that applications use
 * the enum rather than the {@code int} value to ensure code clarity.
 *
 * <p>This enum provides access to the localized textual form of the
 * day-of-week. Some locales also assign different numeric values to the
 * days, declaring Sunday to have the value 1 — use
 * {@link java.time.temporal.WeekFields} for localized week-numbering.
 *
 * <p>This is a value-based class; programmers should treat instances that
 * are {@linkplain #equals(Object) equal} as interchangeable.
 *
 * <p>This class is immutable and thread-safe.
 *
 * @since 1.8
 */
public enum DayOfWeek implements TemporalAccessor, TemporalAdjuster {

    /**
     * The singleton instance for the day-of-week of Monday.
     * This has the numeric value of {@code 1}.
     */
    MONDAY,

    /**
     * The singleton instance for the day-of-week of Tuesday.
     * This has the numeric value of {@code 2}.
     */
    TUESDAY,

    // ... remaining constants

    ;

    /**
     * Obtains an instance of {@code DayOfWeek} from an {@code int} value.
     *
     * <p>{@code DayOfWeek} is an enum representing the 7 days of the week.
     * This factory allows the enum to be obtained from the {@code int} value.
     * The {@code int} value follows the ISO-8601 standard, from 1 (Monday)
     * to 7 (Sunday).
     *
     * @param dayOfWeek the day-of-week to represent, from 1 (Monday) to 7 (Sunday)
     * @return the day-of-week singleton, not null
     * @throws DateTimeException if the day-of-week is invalid
     */
    public static DayOfWeek of(int dayOfWeek) { ... }
}
```

### 10.2 Utility Class Method

```java
/**
 * Returns the hash code of a non-{@code null} argument and 0 for a
 * {@code null} argument.
 *
 * @param o an object
 * @return the hash code of a non-{@code null} argument and 0 for a
 *         {@code null} argument
 * @see Object#hashCode
 */
public static int hashCode(Object o) { ... }
```

### 10.3 Interface Default Method with `@implSpec`

```java
/**
 * Removes all of the elements of this collection that satisfy the given
 * predicate. Errors or runtime exceptions thrown during iteration or by
 * the predicate are relayed to the caller.
 *
 * @implSpec
 * The default implementation traverses all elements of the collection using
 * its {@link #iterator()}. Each matching element is removed using
 * {@link Iterator#remove()}. If the collection's iterator does not support
 * removal then an {@code UnsupportedOperationException} will be thrown on
 * the first matching element.
 *
 * @param filter a predicate which returns {@code true} for elements to be
 *        removed
 * @return {@code true} if any elements were removed
 * @throws NullPointerException if the specified filter is {@code null}
 * @throws UnsupportedOperationException if elements cannot be removed from
 *         this collection
 * @since 1.8
 */
default boolean removeIf(Predicate<? super E> filter) { ... }
```

### 10.4 Method with `@apiNote` and Code Example

```java
/**
 * If a value is present, returns an {@code Optional} describing (as if by
 * {@link #ofNullable}) the result of applying the given mapping function to
 * the value, otherwise returns an empty {@code Optional}.
 *
 * <p>If the mapping function returns a {@code null} result then this method
 * returns an empty {@code Optional}.
 *
 * @apiNote
 * This method supports post-processing on {@code Optional} values, without
 * the need to explicitly check for a return status. For example, the
 * following code traverses a stream of URIs, selects one that has not
 * yet been processed, and creates a path from that URI, returning
 * an {@code Optional<Path>}:
 *
 * <pre>{@code
 *     Optional<Path> p =
 *         uris.stream().filter(uri -> !isProcessedYet(uri))
 *                       .findFirst()
 *                       .map(Paths::get);
 * }</pre>
 *
 * Here, {@code findFirst} returns an {@code Optional<URI>}, and then
 * {@code map} returns an {@code Optional<Path>} for the desired
 * URI if one exists.
 *
 * @param mapper the mapping function to apply to a value, if present
 * @param <U> the type of the value returned from the mapping function
 * @return an {@code Optional} describing the result of applying a mapping
 *         function to the value of this {@code Optional}, if a value is
 *         present, otherwise an empty {@code Optional}
 * @throws NullPointerException if the mapping function is {@code null}
 */
public <U> Optional<U> map(Function<? super T, ? extends U> mapper) { ... }
```

---

## 11. Checklist

Before considering javadoc complete, verify:

- [ ] Every type, method, constructor, and field has a doc comment
- [ ] The first sentence is a complete, standalone summary
- [ ] All `@param` tags present and in declaration order (type params first)
- [ ] `@return` tag present for every non-void method
- [ ] `@throws` tag for every checked exception and significant unchecked exceptions
- [ ] `@since` tag present (you can check the version from pom.xml/build.gradle)
- [ ] Java keywords and type names wrapped in `{@code ...}`
- [ ] Cross-references use `{@link}` (first mention) or `{@code}` (subsequent)
- [ ] Multi-line code examples use `<pre>{@code ...}</pre>`
- [ ] Paragraphs separated with `<p>` on blank lines
- [ ] `@apiNote` / `@implSpec` / `@implNote` used where appropriate
- [ ] Null handling documented (per-method or class-level policy)
- [ ] Thread safety stated at class level where relevant
