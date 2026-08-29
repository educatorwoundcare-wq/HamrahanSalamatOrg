import sys
with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    text = f.read()

depth = 0
for i, char in enumerate(text):
    if char == '{':
        depth += 1
    elif char == '}':
        depth -= 1
        if depth == 0:
            print(f"Depth reached 0 at char {i}, around context: {text[i-50:i+50]}")
print(f"Final depth: {depth}")
