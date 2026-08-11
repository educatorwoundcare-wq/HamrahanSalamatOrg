with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    new_lines.append(line)
    # Line 33
    if i + 1 == 33:
        new_lines.append("    }\n")
    # Line 192 (Wait, 191 is `} else {`, 192 is `"\n✅ هیچ عدم انطباق...`)
    # The `if` expression needs to close?
    if i + 1 == 192:
        new_lines.append("                }\n")
    # Line 203 is `_integrityReport.value = ...` inside catch
    if i + 1 == 203:
        new_lines.append("            }\n")
        new_lines.append("        }\n")
        new_lines.append("    }\n")
    # Line 210 is `repository.insertSystemSetting...`
    if i + 1 == 210:
        new_lines.append("        }\n")
        new_lines.append("    }\n")
    # Line 226 is `updateSystemSetting...`
    if i + 1 == 226:
        new_lines.append("    }\n")
    # Line 230 is `updateSystemSetting...`
    if i + 1 == 230:
        new_lines.append("    }\n")
    # Line 238 is `companyJoinSuccess...`
    if i + 1 == 238:
        new_lines.append("    }\n")

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.writelines(new_lines)
