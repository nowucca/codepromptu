print("Enter your multiline string (press Enter twice to finish):")
lines = []
while True:
    line = input()
    if line:
        lines.append(line)
    else:
        break
multiline_string = '\n'.join(lines)

# Replace newline characters with \n
one_line_string = multiline_string.replace('\n', '\\n')

print(f"""{one_line_string}""")


