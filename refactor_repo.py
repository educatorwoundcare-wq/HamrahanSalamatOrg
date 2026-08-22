import re

with open('app/src/main/java/com/example/data/HamrahanRepository.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
i = 0
while i < len(lines):
    line = lines[i]
    
    # Check for simple suspend fun insertXXX(entity: XXX): Long {
    match_insert = re.match(r'^(\s*)suspend fun insert(\w+)\((\w+):\s*([\w<>]+)\):\s*Long\s*\{\s*$', line)
    match_update = re.match(r'^(\s*)suspend fun update(\w+)\((\w+):\s*([\w<>]+)\)\s*\{\s*$', line)
    match_delete = re.match(r'^(\s*)suspend fun delete(\w+)\((\w+):\s*([\w<>]+)\)\s*\{\s*$', line)

    if match_insert and 'registerLocalChange' in lines[i+2]:
        indent = match_insert.group(1)
        entity_name = match_insert.group(2)
        var_name = match_insert.group(3)
        type_name = match_insert.group(4)
        
        # Original body is typically 3 lines: 
        # val id = dao.insertXXX(xxx)
        # registerLocalChange("XXX", xxx.uuid)
        # return id
        # }
        id_line = lines[i+1].strip()
        reg_line = lines[i+2].strip()
        ret_line = lines[i+3].strip()
        close_brace = lines[i+4].strip()
        
        if id_line.startswith('val id =') and reg_line.startswith('registerLocalChange') and ret_line.startswith('return id'):
            new_lines.append(line)
            new_lines.append(indent + "    var id = 0L\n")
            new_lines.append(indent + "    dao.runInTransaction {\n")
            new_lines.append(indent + "        id = " + id_line.replace('val id = ', '') + "\n")
            # modify registerLocalChange call to use "INSERT"
            reg_call = reg_line.replace(')', ', "INSERT")')
            new_lines.append(indent + "        " + reg_call + "\n")
            new_lines.append(indent + "    }\n")
            new_lines.append(indent + "    return id\n")
            new_lines.append(indent + "}\n")
            i += 5
            continue
            
    elif match_update and 'registerLocalChange' in lines[i+2]:
        indent = match_update.group(1)
        entity_name = match_update.group(2)
        var_name = match_update.group(3)
        type_name = match_update.group(4)
        
        # Original body is typically 2 lines:
        # dao.updateXXX(xxx)
        # registerLocalChange("XXX", xxx.uuid)
        # }
        up_line = lines[i+1].strip()
        reg_line = lines[i+2].strip()
        close_brace = lines[i+3].strip()
        
        if up_line.startswith('dao.update') and reg_line.startswith('registerLocalChange'):
            new_lines.append(line)
            new_lines.append(indent + "    dao.runInTransaction {\n")
            new_lines.append(indent + "        " + up_line + "\n")
            reg_call = reg_line.replace(')', ', "UPDATE")')
            new_lines.append(indent + "        " + reg_call + "\n")
            new_lines.append(indent + "    }\n")
            new_lines.append(indent + "}\n")
            i += 4
            continue

    elif match_delete and 'registerLocalChange' in lines[i+2]:
        indent = match_delete.group(1)
        entity_name = match_delete.group(2)
        var_name = match_delete.group(3)
        type_name = match_delete.group(4)
        
        del_line = lines[i+1].strip()
        reg_line = lines[i+2].strip()
        close_brace = lines[i+3].strip()
        
        if del_line.startswith('dao.delete') and reg_line.startswith('registerLocalChange'):
            new_lines.append(line)
            new_lines.append(indent + "    dao.runInTransaction {\n")
            new_lines.append(indent + "        " + del_line + "\n")
            reg_call = reg_line.replace('isDeleted = true', '"DELETE"')
            new_lines.append(indent + "        " + reg_call + "\n")
            new_lines.append(indent + "    }\n")
            new_lines.append(indent + "}\n")
            i += 4
            continue
            
    new_lines.append(line)
    i += 1

# replace the registerLocalChange definition
out_text = "".join(new_lines)
# We will do the rest manually to ensure correctness
with open('app/src/main/java/com/example/data/HamrahanRepository.kt', 'w') as f:
    f.write(out_text)

