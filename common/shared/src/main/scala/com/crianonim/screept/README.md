# Screept Language Reference

Screept is a small imperative scripting language with expressions, variables, functions, procedures, conditionals, and random number generation.

## Program Structure

A program is one or more statements separated by semicolons or newlines:

```
statement1; statement2; statement3
```

```
statement1
statement2
statement3
```

Both can be mixed freely. Trailing semicolons are allowed. Blank lines between statements are fine.

## Types

There are three value types:

| Type   | Examples            | Truthy when        |
|--------|---------------------|--------------------|
| number | `42`, `3.14`, `-1`  | not zero           |
| text   | `"hello"`, `""`     | non-empty string   |
| func   | `FUNC _0 + 1`      | always             |

Numbers are floating-point. Integers display without a decimal point (e.g. `7` not `7.0`).

## Identifiers

Identifiers start with a lowercase letter or `_`, followed by any alphanumeric characters or `_`:

```
x
my_var
_0
item3
```

### Computed Identifiers

Use `$[expr]` to compute an identifier name at runtime from an expression:

```
i = 0
$["item_" + i] = 100
PRINT $["item_0"]
```

Output: `100`

## Expressions

### Literals

```
42          number
3.14        number with decimal
-5          negative number
"hello"     text string
```

### Variables

```
x           lookup variable x
$[expr]     lookup variable with computed name
```

### Arithmetic

```
a + b       addition (numbers), or string concatenation (if either is text)
a - b       subtraction (numbers only)
a * b       multiplication (numbers only)
a / b       division (numbers only)
a // b      integer division - floor(a / b) (numbers only)
```

Operator precedence (highest to lowest):

1. Unary: `+x`, `-x`, `!x`
2. Multiplicative: `*`, `/`, `//`
3. Additive: `+`, `-`
4. Comparison: `==`, `<`, `>`
5. Conditional: `? :`

Use parentheses to override precedence: `(a + b) * c`

Expressions can span multiple lines when a line ends with an operator:

```
result = 1 +
  2 * 3
```

Parentheses, brackets (`$[...]`), and argument lists also allow newlines freely.

### Comparison

Comparisons return `1` (true) or `0` (false):

```
a == b      equality (same type and string representation)
a < b       less than (numbers only)
a > b       greater than (numbers only)
```

### Unary Operators

```
+x          identity (numbers only)
-x          negation (numbers only)
!x          logical not (returns 1 if falsy, 0 if truthy)
```

### Conditional Expression (Ternary)

```
condition ? value_if_true : value_if_false
```

Example:

```
x = 10
result = x > 5 ? "big" : "small"
PRINT result
```

Output: `big`

### Functions (FUNC)

Define anonymous functions with `FUNC`. Arguments are accessed as `_0`, `_1`, `_2`, etc.:

```
add = FUNC _0 + _1
PRINT add(3, 4)
```

Output: `7`

```
greet = FUNC "Hello " + _0
PRINT greet("World")
```

Output: `Hello World`

### Function Calls

Call a function stored in a variable:

```
identifier(arg1, arg2, ...)
```

Arguments are bound to `_0`, `_1`, etc. in the function body.

## Statements

### Variable Binding

```
identifier = expression
```

Examples:

```
x = 10
name = "Alice"
doubled = FUNC _0 * 2
```

### PRINT

Evaluates an expression and appends its string representation to the output:

```
PRINT expression
```

Examples:

```
PRINT 42
PRINT "Hello " + "World"
PRINT x + y
```

### EMIT

Sends a value to an external handler (if one is provided). Does not add to the output list:

```
EMIT expression
```

### IF / THEN / ELSE

```
IF condition THEN statement
IF condition THEN statement ELSE statement
```

THEN and ELSE can appear on the same line or the next line:

```
IF x > 5 THEN PRINT "big" ELSE PRINT "small"
```

```
IF x > 5
THEN PRINT "big"
ELSE PRINT "small"
```

Use blocks `{ ... }` for multiple statements in branches:

```
x = 10
IF x > 5 THEN {
  PRINT "x is big"
  PRINT x
} ELSE {
  PRINT "x is small"
}
```

Output:
```
x is big
10
```

### Blocks

Group multiple statements with curly braces. Statements inside are separated by semicolons or newlines:

```
{ statement1; statement2; statement3 }
```

```
{
  statement1
  statement2
  statement3
}
```

Trailing semicolons are allowed inside blocks.

### Procedures (PROC / RUN)

Define a named procedure (a reusable block of statements):

```
PROC name statement
```

Run a procedure with arguments:

```
RUN name(arg1, arg2, ...)
```

Arguments are bound to `_0`, `_1`, etc. inside the procedure body:

```
PROC greet {
  PRINT "Hello "
  PRINT _0
}
RUN greet("World")
```

The procedure body can also start on the next line:

```
PROC greet
  PRINT "hello"
```

Output:
```
Hello
World
```

### Random Numbers (RND)

Generate a random integer in an inclusive range and bind it to a variable:

```
RND identifier from to
```

Example:

```
RND x 1 6
PRINT "You rolled: " + x
```

Output: `You rolled: 3` (random 1-6)

## Complete Example

```
name = "Adventurer"
hp = 20

PROC attack {
  RND damage 1 6
  PRINT name + " attacks for " + damage + " damage!"
}

RUN attack()
IF hp > 10
THEN PRINT name + " is healthy"
ELSE PRINT name + " is wounded"
```

Possible output:
```
Adventurer attacks for 4 damage!
Adventurer is healthy
```

## Environment

After execution, the environment contains:

- **vars** - all bound variables (name -> value)
- **procedures** - all defined procedures (name -> statement body)
- **output** - ordered list of all `PRINT` outputs (each with a timestamp)
